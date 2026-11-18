# NoClearAll

NoClearAll is an Xposed module that removes the **Clear all** button from the notification shade, so
you can't wipe every notification with one accidental tap. You can still swipe notifications away one
at a time, and nothing else in the shade changes.

## Tested setup

- Pixel 8 Pro, LineageOS 24.0 (Android 17), APatch, ReZygisk 1.0.0, Vector 2.2
- Pixel 8 Pro, LineageOS 23.2 (Android 16), Vector 2.2

The module hooks LineageOS's System UI code. Other ROMs and Android versions are untested.

## Install

You need a rooted phone with an Xposed framework that supports libxposed API 101 or newer, such as
[Vector](https://github.com/JingMatrix/Vector).

1. Download the APK from [Releases](https://github.com/D3SOX/NoClearAll/releases) and install it.
2. Enable NoClearAll in your Xposed manager. Its scope is System UI only.
3. Reboot the phone.

## How it works

The module runs only in System UI. It keeps the Clear all button hidden and removes its click
handler. It doesn't read or change your notifications, and the app has no screen, permissions or
network access.

## If Clear all comes back

A ROM update may have renamed the System UI code that the module hooks. Check the log for messages
from NoClearAll and open an issue with them:

```bash
adb logcat | grep NoClearAll
```

## Building

See [BUILD.md](BUILD.md).

## License

[GPL-3.0-or-later](LICENSE)
