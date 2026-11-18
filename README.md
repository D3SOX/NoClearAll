# NoClearAll

NoClearAll is a deliberately narrow [modern libxposed](https://github.com/libxposed/api) module for
Android 16 / LineageOS 23.2. It removes the **Clear all** action from the notification footer in
`com.android.systemui` without changing notification data or any individual-notification behavior.

> **Tested environment:** Pixel 8 Pro, Android 16, LineageOS 23.2, and Vector 2.2 (libxposed API
> 102). Other Android versions, ROMs, SystemUI builds, Xposed frameworks, and OEM notification
> shades are untested and may behave differently.

There is no activity, settings screen, telemetry, networking, analytics, or requested permission.

## Compatibility

- Android 16 (API 36)
- LineageOS 23.2 SystemUI implementation using
  `com.android.systemui.statusbar.notification.footer.ui.view.FooterView`
- A modern LSPosed-compatible framework implementing libxposed API 101 or newer

The project uses the modern module resources under `META-INF/xposed/` rather than the legacy
`assets/xposed_init` format. Its declared scope is static and contains only `com.android.systemui`.

## Build

Requirements:

- Android SDK Platform 36
- JDK 21 or newer supported by the bundled Gradle/Android Gradle Plugin versions
- Internet access on the first build so Gradle can resolve the Android plugin and the compile-only
  libxposed API

Open this directory in Android Studio and build the project, or run:

```bash
./gradlew :app:assembleRelease
```

The installable APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

Without signing environment variables, local release builds use the Android debug key so they remain
directly installable. Tagged GitHub releases use the repository's dedicated signing key supplied by
encrypted Actions secrets. See [`signing/README.md`](signing/README.md) for the expected secret names
and backup information.

## Install and enable

1. Install `app/build/outputs/apk/release/app-release.apk` on the phone.

   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

2. Open LSPosed and enable **NoClearAll**.
3. Confirm that its scope contains only **System UI / `com.android.systemui`**. The module declares a
   static one-package scope; do not add Android Framework, system server, a launcher, or other apps.
4. Reboot the phone, or restart SystemUI from an ADB shell:

   ```bash
   adb shell su -c 'killall com.android.systemui'
   ```

   Android should start SystemUI again automatically. A full reboot is the safer option if the ROM
   does not restart it promptly.
5. Open the notification shade and verify that **Clear all** is absent. Confirm that swiping one
   notification away still works and that settings, history, silent controls, expansion, and grouping
   are unchanged.

To inspect module diagnostics:

```bash
adb logcat | grep NoClearAll
```

## Hooks

The module installs hooks only after the `com.android.systemui` package class loader is ready:

1. Every compatible `FooterView.setClearAllButtonVisible(boolean, boolean, ...)` overload is allowed
   to run, but with its first argument replaced by `false`. This includes the current two-argument and
   three-argument (`Consumer<Boolean>`) LineageOS 23.2 methods. SystemUI's binder therefore cannot
   re-enable the secondary footer view.
2. `FooterView.setClearAllButtonClickListener(View.OnClickListener)` is short-circuited, so neither
   FooterView nor its button can retain the supplied Clear all listener.
3. After `FooterView.onFinishInflate()` returns, the module resolves
   `com.android.systemui:id/dismiss_text` at runtime and applies `View.GONE`, clears its click listener,
   and disables both clickability and enabled state. The same safeguard runs after intercepted setter
   calls.

Release SystemUI builds may have the public-ish setters removed by R8 after their bodies are inlined
into `FooterViewBinder`. For that case, the module also hooks the inherited
`StackScrollerDecorView.setSecondaryVisible(boolean, boolean, ...)` method and changes calls only when
the receiver is an actual `FooterView`. This remains narrowly scoped and does not hook
`View.setVisibility()`.

`GONE` removes the view from layout and hit-testing. Clearing the listener and disabling the view
prevent an invisible action area even if unrelated future code manipulates the view directly.

The module does **not** hook `View.setVisibility()` or any broad framework method. It does not cancel,
clear, mutate, or listen to notification data, so individual dismissal and every other notification
feature remain under SystemUI's normal control.

## Failure behavior and future ROM updates

Class lookup, method enumeration, each hook installation, resource lookup, and direct view updates are
guarded independently. Missing or changed pieces are logged through the Xposed logging API and the
remaining safeguards continue. The module never intentionally throws into SystemUI.

After a LineageOS update, check logs if Clear all returns. Adjustment may be needed if LineageOS:

- renames or moves `FooterView`;
- renames `setClearAllButtonVisible`, `setClearAllButtonClickListener`, or `onFinishInflate`;
- changes the visibility setter so its first two parameters are no longer `(boolean, boolean)`; or
- replaces the `dismiss_text` resource ID or stops using a child view for the action.

The hook intentionally avoids private field names. The public-ish footer methods are the primary
control point; runtime lookup of `dismiss_text` is only a secondary safeguard.

## Source basis

The implementation was checked against the current LineageOS 23.2
[`FooterView.java`](https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.2/packages/SystemUI/src/com/android/systemui/statusbar/notification/footer/ui/view/FooterView.java),
which contains both visibility overloads, the click-listener setter, `onFinishInflate()`, and the
`dismiss_text` secondary-view lookup. Module structure and API usage follow the current
[`libxposed/api`](https://github.com/libxposed/api) and
[`libxposed/example`](https://github.com/libxposed/example) projects.

## Automated releases

Pushing a tag matching `v*` runs `.github/workflows/release.yml`. The workflow installs Android SDK
36, restores the dedicated key from GitHub Actions secrets, builds and lints the release APK, verifies
its signature and static SystemUI scope, generates a SHA-256 checksum, and publishes both files in a
GitHub release.

## License

[GPL-3.0-or-later](LICENSE)
