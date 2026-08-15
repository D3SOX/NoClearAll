package dev.noclearall;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;

/**
 * Removes only the notification footer's Clear all action from SystemUI.
 *
 * <p>The preferred hooks force every supported setClearAllButtonVisible overload to receive
 * {@code visible = false}. Optimized builds that remove those methods are handled through a
 * class-filtered secondary-visibility hook. Hiding the concrete dismiss_text view and clearing its
 * listener provide the final safeguard.</p>
 */
public final class NoClearAllModule extends XposedModule {
    private static final String TAG = "NoClearAll";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String FOOTER_VIEW_CLASS =
            "com.android.systemui.statusbar.notification.footer.ui.view.FooterView";
    private static final String STACK_SCROLLER_DECOR_VIEW_CLASS =
            "com.android.systemui.statusbar.notification.row.StackScrollerDecorView";
    private static final String CLEAR_ALL_ID_NAME = "dismiss_text";
    private static final int RESOURCE_ID_UNRESOLVED = Integer.MIN_VALUE;

    private final AtomicBoolean hooksInstalled = new AtomicBoolean();
    private final AtomicBoolean blockedVisibilityLogged = new AtomicBoolean();
    private final AtomicBoolean blockedListenerLogged = new AtomicBoolean();
    private final AtomicBoolean hiddenViewLogged = new AtomicBoolean();
    private final AtomicBoolean missingViewLogged = new AtomicBoolean();
    private final AtomicBoolean safeguardFailureLogged = new AtomicBoolean();

    private volatile int clearAllResourceId = RESOURCE_ID_UNRESOLVED;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        logInfo("Loaded in process " + param.getProcessName()
                + " with libxposed API " + getApiVersion());

        // Vector 2.2 can load a module after its LoadedApk class-loader callbacks have already run.
        // Install immediately from the process context class loader, then retry once on the main
        // queue in case SystemUI's class loader is still being attached at this exact moment.
        if (!param.isSystemServer() && SYSTEM_UI_PACKAGE.equals(param.getProcessName())) {
            if (!tryInstallHooks(
                    Thread.currentThread().getContextClassLoader(),
                    "onModuleLoaded")) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!tryInstallHooks(
                            Thread.currentThread().getContextClassLoader(),
                            "deferred onModuleLoaded")) {
                        tryInstallHooks(ClassLoader.getSystemClassLoader(), "system class loader");
                    }
                });
            }
        }
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (SYSTEM_UI_PACKAGE.equals(param.getPackageName())) {
            tryInstallHooks(param.getDefaultClassLoader(), "onPackageLoaded");
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!SYSTEM_UI_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        tryInstallHooks(param.getClassLoader(), "onPackageReady");
    }

    private boolean tryInstallHooks(ClassLoader classLoader, String source) {
        if (hooksInstalled.get()) {
            return true;
        }
        if (classLoader == null) {
            logWarning("No class loader was available from " + source);
            return false;
        }

        final Class<?> footerClass;
        try {
            footerClass = Class.forName(FOOTER_VIEW_CLASS, false, classLoader);
        } catch (Throwable throwable) {
            String message = "FooterView is not available from " + source + ": "
                    + throwable.getClass().getSimpleName();
            if ("onModuleLoaded".equals(source)) {
                logInfo(message + "; waiting for a package class-loader callback");
            } else {
                logWarning(message);
            }
            return false;
        }

        if (!hooksInstalled.compareAndSet(false, true)) {
            return true;
        }

        installHooks(footerClass, source);
        return true;
    }

    private void installHooks(Class<?> footerClass, String source) {

        int visibilityHookCount = 0;
        int secondaryVisibilityHookCount = 0;
        int listenerHookCount = 0;
        int inflateHookCount = 0;

        final Method[] methods;
        try {
            methods = footerClass.getDeclaredMethods();
        } catch (Throwable throwable) {
            logError("Could not inspect FooterView; SystemUI was left unchanged", throwable);
            return;
        }

        for (Method method : methods) {
            try {
                if (isClearAllVisibilitySetter(method)) {
                    hookVisibilitySetter(method);
                    visibilityHookCount++;
                    logInfo("Hooked " + readableSignature(method));
                } else if (isClearAllClickListenerSetter(method)) {
                    hookClickListenerSetter(method);
                    listenerHookCount++;
                    logInfo("Hooked " + readableSignature(method));
                } else if (isOnFinishInflate(method)) {
                    hookOnFinishInflate(method);
                    inflateHookCount++;
                    logInfo("Hooked FooterView.onFinishInflate()");
                }
            } catch (Throwable throwable) {
                // Treat each method independently so one changed overload cannot disable the rest.
                logError("Failed to hook " + readableSignature(method)
                        + "; continuing with remaining safeguards", throwable);
            }
        }

        // Current release builds can inline/remove FooterView's public-ish setters. Their optimized
        // binder then calls this inherited method directly. Hook only that method and only change
        // calls whose receiver is the FooterView class resolved above.
        Class<?> parentClass = footerClass.getSuperclass();
        if (parentClass != null && STACK_SCROLLER_DECOR_VIEW_CLASS.equals(parentClass.getName())) {
            try {
                for (Method method : parentClass.getDeclaredMethods()) {
                    if (!isSecondaryVisibilitySetter(method)) {
                        continue;
                    }
                    try {
                        hookSecondaryVisibilitySetter(method, footerClass);
                        secondaryVisibilityHookCount++;
                        logInfo("Hooked optimized fallback " + readableSignature(method));
                    } catch (Throwable throwable) {
                        logError("Failed to hook optimized fallback " + readableSignature(method),
                                throwable);
                    }
                }
            } catch (Throwable throwable) {
                logError("Could not inspect StackScrollerDecorView fallback methods", throwable);
            }
        }

        if (visibilityHookCount == 0 && secondaryVisibilityHookCount == 0) {
            logError("No compatible Clear all visibility method was found", null);
        } else if (visibilityHookCount == 0) {
            logWarning("FooterView visibility setters were optimized out; using the class-filtered "
                    + "StackScrollerDecorView fallback");
        } else if (visibilityHookCount < 2) {
            logWarning("Only " + visibilityHookCount
                    + " setClearAllButtonVisible overload was found; the ROM API may have changed");
        }
        if (listenerHookCount == 0) {
            logWarning("FooterView click-listener setter was optimized out; the visibility "
                    + "fallback will clear the directly installed listener");
        }
        if (inflateHookCount == 0) {
            logWarning("FooterView.onFinishInflate() was not found; view safeguard unavailable");
        }

        logInfo("Installation complete from " + source + ": visibility=" + visibilityHookCount
                + ", optimizedFallback=" + secondaryVisibilityHookCount
                + ", listener=" + listenerHookCount + ", inflate=" + inflateHookCount);
    }

    /**
     * Match every current or future void overload whose first arguments retain the documented
     * (boolean visible, boolean animate, ...) contract. This covers both known two- and
     * three-argument LineageOS 23.2 signatures without assuming only one overload exists.
     */
    private static boolean isClearAllVisibilitySetter(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        return method.getName().equals("setClearAllButtonVisible")
                && !Modifier.isStatic(method.getModifiers())
                && method.getReturnType() == Void.TYPE
                && parameters.length >= 2
                && parameters[0] == Boolean.TYPE
                && parameters[1] == Boolean.TYPE;
    }

    private static boolean isClearAllClickListenerSetter(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        return method.getName().equals("setClearAllButtonClickListener")
                && !Modifier.isStatic(method.getModifiers())
                && method.getReturnType() == Void.TYPE
                && parameters.length == 1
                && View.OnClickListener.class.isAssignableFrom(parameters[0]);
    }

    private static boolean isSecondaryVisibilitySetter(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        return method.getName().equals("setSecondaryVisible")
                && !Modifier.isStatic(method.getModifiers())
                && method.getReturnType() == Void.TYPE
                && parameters.length >= 2
                && parameters[0] == Boolean.TYPE
                && parameters[1] == Boolean.TYPE;
    }

    private static boolean isOnFinishInflate(Method method) {
        return method.getName().equals("onFinishInflate")
                && !Modifier.isStatic(method.getModifiers())
                && method.getReturnType() == Void.TYPE
                && method.getParameterCount() == 0;
    }

    private void hookVisibilitySetter(Method method) {
        hook(method)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    List<Object> currentArguments = chain.getArgs();
                    Object[] safeArguments = currentArguments.toArray();
                    boolean showWasRequested = Boolean.TRUE.equals(safeArguments[0]);
                    safeArguments[0] = false;

                    if (showWasRequested && blockedVisibilityLogged.compareAndSet(false, true)) {
                        logInfo("Blocked a request to show the Clear all button");
                    }

                    try {
                        return chain.proceed(safeArguments);
                    } finally {
                        hideClearAllViewSafely(chain.getThisObject(), "visibility setter");
                    }
                });
    }

    private void hookSecondaryVisibilitySetter(Method method, Class<?> footerClass) {
        hook(method)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object receiver = chain.getThisObject();
                    if (!footerClass.isInstance(receiver)) {
                        return chain.proceed();
                    }

                    Object[] safeArguments = chain.getArgs().toArray();
                    boolean showWasRequested = Boolean.TRUE.equals(safeArguments[0]);
                    safeArguments[0] = false;

                    if (showWasRequested && blockedVisibilityLogged.compareAndSet(false, true)) {
                        logInfo("Blocked an optimized binder request to show Clear all");
                    }

                    try {
                        return chain.proceed(safeArguments);
                    } finally {
                        // The optimized binder installs its listener immediately before this call.
                        hideClearAllViewSafely(receiver, "optimized visibility setter");
                    }
                });
    }

    private void hookClickListenerSetter(Method method) {
        hook(method)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    // Do not call the original: it stores the listener in FooterView and on the button.
                    hideClearAllViewSafely(chain.getThisObject(), "click-listener setter");
                    if (blockedListenerLogged.compareAndSet(false, true)) {
                        logInfo("Blocked installation of the Clear all click listener");
                    }
                    return null;
                });
    }

    private void hookOnFinishInflate(Method method) {
        hook(method)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    hideClearAllViewSafely(chain.getThisObject(), "onFinishInflate");
                    return result;
                });
    }

    private void hideClearAllViewSafely(Object footerObject, String source) {
        try {
            if (!(footerObject instanceof View footerView)) {
                logMissingViewOnce(source + ": FooterView instance is not an android.view.View");
                return;
            }

            int resourceId = resolveClearAllResourceId(footerView);
            if (resourceId == 0) {
                logMissingViewOnce(source + ": resource id com.android.systemui:id/dismiss_text not found");
                return;
            }

            View clearAllView = footerView.findViewById(resourceId);
            if (clearAllView == null) {
                logMissingViewOnce(source + ": dismiss_text is not a child of FooterView");
                return;
            }

            // GONE removes layout and hit-testing. The remaining calls make the action inert even if
            // future SystemUI code directly changes visibility without using the hooked setter.
            clearAllView.setOnClickListener(null);
            clearAllView.setClickable(false);
            clearAllView.setEnabled(false);
            clearAllView.setVisibility(View.GONE);

            if (hiddenViewLogged.compareAndSet(false, true)) {
                logInfo("Clear all view hidden and disabled after " + source);
            }
        } catch (Throwable throwable) {
            if (safeguardFailureLogged.compareAndSet(false, true)) {
                logError("Clear all view safeguard failed after " + source
                        + "; hook execution will continue safely", throwable);
            }
        }
    }

    private int resolveClearAllResourceId(View footerView) {
        int cached = clearAllResourceId;
        if (cached != RESOURCE_ID_UNRESOLVED) {
            return cached;
        }

        int resolved = footerView.getResources().getIdentifier(
                CLEAR_ALL_ID_NAME,
                "id",
                SYSTEM_UI_PACKAGE);
        clearAllResourceId = resolved;
        return resolved;
    }

    private void logMissingViewOnce(String message) {
        if (missingViewLogged.compareAndSet(false, true)) {
            logWarning(message);
        }
    }

    private void logInfo(String message) {
        try {
            log(Log.INFO, TAG, message);
        } catch (Throwable ignored) {
            // Logging must never affect SystemUI hook execution.
        }
    }

    private void logWarning(String message) {
        try {
            log(Log.WARN, TAG, message);
        } catch (Throwable ignored) {
            // Logging must never affect SystemUI hook execution.
        }
    }

    private void logError(String message, Throwable throwable) {
        try {
            if (throwable == null) {
                log(Log.ERROR, TAG, message);
            } else {
                log(Log.ERROR, TAG, message, throwable);
            }
        } catch (Throwable ignored) {
            // Logging must never affect SystemUI hook execution.
        }
    }

    private static String readableSignature(Method method) {
        List<String> parameterNames = new ArrayList<>();
        for (Class<?> parameterType : method.getParameterTypes()) {
            parameterNames.add(parameterType.getTypeName());
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName()
                + "(" + String.join(", ", parameterNames) + ")";
    }
}
