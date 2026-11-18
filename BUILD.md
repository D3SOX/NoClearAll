# Building NoClearAll

You need JDK 21 or newer and the Android SDK. Gradle downloads the SDK platform and build tools it
needs on the first build.

```bash
./gradlew :app:assembleRelease
```

The APK ends up in `app/build/outputs/apk/release/app-release.apk`. Install it with:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Local release builds use the Android debug key, so you can install them without extra setup. Official
releases use a dedicated key. See [signing/README.md](signing/README.md) for how it's stored.
