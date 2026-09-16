# Sypers Android APK

This is a small Android wrapper for the Sypers web app. The web pages are
bundled inside the APK, so login, profile, settings, and blocked numbers work
without an internet connection. Android contact access and call screening are
provided by the native wrapper.

Build a debug APK with:

```bash
./gradlew assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This is version 1.3.0 (`versionCode 4`) and can be installed over version 1.0/1.1/1.2
with the same application ID without clearing local app data.
