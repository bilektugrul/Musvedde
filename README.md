# Müsvedde

A fast, offline-first notes app for Android that stays quick no matter how many notes you keep.

Müsvedde exists because popular note apps get noticeably slower to type in once you have thousands of notes. Here, the typing path never depends on how many notes you have: the editor owns its text, saves are debounced targeted updates, and the list never loads full note bodies.

## Features

- Notes with title and body, 12 colors, pin to top, archive, trash (auto-emptied after 7 days), labels
- Staggered grid or list, drag-and-drop ordering, full-text search
- Instant autosave, undo/redo, focus mode
- Pin a note to the notification shade
- Backups to a folder of your choice (works great with a synced folder) and, optionally, Google Drive; daily, weekly, monthly or on demand
- System, light, dark and AMOLED black themes, Material You colors
- Accessibility settings: UI text size, editor font, size, line height, alignment, pinch-to-zoom, high contrast, reduced motion
- In-app error log
- English and Turkish

## Building

Requirements: JDK 17+, Android SDK (compileSdk 37).

```
./gradlew :app:assembleDebug
```

Run the unit tests with `./gradlew :app:testDebugUnitTest`.

If your user folder path contains spaces and Robolectric fails to load its native runtime, copy the `android-all-instrumented` jar from `~/.m2/repository/org/robolectric/` to a folder without spaces and pass `-ProbolectricDependencyDir=<that folder>`.

## Performance

Performance is the reason this app exists, so it is measured, not assumed:

- The `baselineprofile` module holds a Baseline Profile generator and Macrobenchmarks for cold start, scrolling a 10,000-note list and typing into a note while 10,000 notes exist.
- Debug builds have *Settings → Developer* actions to generate 1,000 or 10,000 sample notes for trying it by hand.

With a device or emulator (API 29+) connected:

```
./gradlew :app:generateReleaseBaselineProfile
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

## Google Drive backup (optional)

Folder backups work out of the box. Drive backups store files in the app's hidden `appDataFolder` and need a Google Cloud OAuth client that matches **your** build's package name and signing key. No keys live in this repository.

1. Create a project at <https://console.cloud.google.com/> and enable the **Google Drive API**.
2. Configure the **OAuth consent screen** (External, testing mode is fine) and add your Google account as a test user. Add the scope `https://www.googleapis.com/auth/drive.appdata`.
3. Create an **OAuth client ID** of type **Android** with package name `dev.aliarslan.musvedde` (or your fork's `applicationId`) and the SHA-1 of the key that signs your build. Get it with `./gradlew signingReport`.
4. Install that build, open *Settings → Backup* and tap *Connect Google Drive*.

If the client is missing or does not match, connecting fails and the reason is written to the in-app error log.

## License

Müsvedde is **source-available** under the [PolyForm Noncommercial License 1.0.0](LICENSE). You are free to read, fork, modify and share it for any noncommercial purpose. Commercial use is not permitted.
