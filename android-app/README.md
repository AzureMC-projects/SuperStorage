# SuperStorage for Android

A separate Android-native app built with Java and Android's Storage Access Framework.

## Build locally
Open `android-app/` in Android Studio with JDK 17 and Android SDK 35, or run:

```sh
gradle testDebugUnitTest
gradle assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Features
- Home overview with real device storage totals from `StatFs`.
- Folder-based file scan using Android's system folder picker. The user grants access to a chosen folder; the app does not request broad storage permissions.
- Recursive file listing (bounded to 250 files and 12 directory levels), sorted by size.
- Selective deletion with a review dialog before deletion; files are never deleted automatically.
- Persistent selected-folder permission and a forget-folder control.
- Android Security Settings and Google Play Protect entry points.
- Settings, app-permission shortcut, and project/about page.
- GitHub Actions builds the debug APK, runs unit tests, and uploads the APK as a workflow artifact.

## Security and platform limits
This is a storage helper, not a full antivirus product. It does not claim to scan for malware or inspect other apps' private data. For app threat protection it opens Google Play Protect. Android restricts access to app-private storage, and the user-selected folder limits the cleanup scope.

The folder scan caps results at 250 files and recursion at 12 levels to keep the UI responsive. Large or deeply nested folders may therefore be partially listed. Deletion depends on the document provider's support and Android may restrict protected locations. Always review selected names before confirming.

## CI artifact
Push to `android-native-redesign` or run **Actions → Android build → Run workflow**. Download `SuperStorage-debug-apk` from the successful run's artifacts. This is a debug APK, not a Play Store-signed release.
