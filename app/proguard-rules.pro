# The Xposed API is compile-only and supplied by LSPosed.
-dontwarn io.github.libxposed.annotation.**

# The entrypoint name is stored in META-INF/xposed/java_init.list. Keeping this one small class
# unchanged is simpler and safer than relying on resource-content adaptation.
-keep public class dev.noclearall.NoClearAllModule {
    public <init>();
    *;
}
