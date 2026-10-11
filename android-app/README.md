# SuperStorage for Android

This is a separate native Android app module. It is intentionally not a port of the desktop UI.

## Open and build

Open the `android-app` directory in Android Studio with a compatible JDK and Android SDK installed. Let Gradle sync, then run the `app` configuration on an emulator or device.

Command-line build from this directory:

```sh
gradle assembleDebug
```

The APK, if the build succeeds, is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Current state

- Native Java Android activity and mobile bottom navigation.
- Home, Storage, Security, and Settings screens.
- Storage and security screens link to Android system settings.
- No broad storage permission is requested.
- No real file cleanup, duplicate finder, malware signature engine, or background scanning is implemented yet. The UI explicitly says so rather than reporting fake scan results.

## Planned implementation

1. Storage overview using Android-supported storage APIs and scoped access.
2. Reviewable large-file and duplicate-media results, with user confirmation before deletion.
3. Security scan engine with a clearly documented detection source, local-first processing, and truthful scan states. Android app sandboxing prevents a normal app from freely scanning every other app's private files.
4. Persisted preferences for theme, scan options, and notifications.
5. Device testing, accessibility checks, and build verification before release.

This starter has not been device-tested or verified by a CI build yet.
