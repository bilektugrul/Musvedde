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

## License

Müsvedde is **source-available** under the [PolyForm Noncommercial License 1.0.0](LICENSE). You are free to read, fork, modify and share it for any noncommercial purpose. Commercial use is not permitted.
