package dev.aliarslan.musvedde.domain

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

enum class ListLayout { GRID, LIST }

enum class EditorFont { SERIF, SYSTEM, GOOGLE_SANS, MONO }

enum class EditorAlign { START, CENTER }

enum class PreviewSize(val maxLines: Int) { SMALL(6), MEDIUM(10), LARGE(14) }

enum class BackupFrequency { OFF, DAILY, WEEKLY, MONTHLY }

enum class AnimationSpeed(val factor: Float) { SLOW(1.8f), NORMAL(1.35f), FAST(1.0f) }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val listLayout: ListLayout = ListLayout.GRID,
    val uiTextScale: Float = 1.0f,
    val editorFontSizeSp: Float = 18f,
    val editorLineHeight: Float = 1.5f,
    val editorFont: EditorFont = EditorFont.SERIF,
    val editorAlign: EditorAlign = EditorAlign.START,
    val pinchZoom: Boolean = true,
    val previewSize: PreviewSize = PreviewSize.MEDIUM,
    val reduceMotion: Boolean = false,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    val highContrast: Boolean = false,
    val alwaysFocusMode: Boolean = false,
    val restoreDismissedPins: Boolean = true,
    val backupFolderUri: String? = null,
    val driveEnabled: Boolean = false,
    val driveAccountEmail: String? = null,
    val backupFrequency: BackupFrequency = BackupFrequency.OFF,
    val backupRetention: Int = 10,
    val lastFolderBackupAt: Long? = null,
    val lastFolderBackupError: String? = null,
    val lastDriveBackupAt: Long? = null,
    val lastDriveBackupError: String? = null,
) {
    fun sanitized() = copy(
        uiTextScale = uiTextScale.coerceIn(UI_TEXT_SCALE_RANGE),
        editorFontSizeSp = editorFontSizeSp.coerceIn(EDITOR_FONT_SIZE_RANGE),
        editorLineHeight = editorLineHeight.coerceIn(EDITOR_LINE_HEIGHT_RANGE),
        backupRetention = backupRetention.takeIf { it in RETENTION_OPTIONS } ?: DEFAULT_RETENTION,
    )

    companion object {
        val UI_TEXT_SCALE_RANGE = 0.85f..1.5f
        val EDITOR_FONT_SIZE_RANGE = 14f..32f
        val EDITOR_LINE_HEIGHT_RANGE = 1.2f..2.0f
        val RETENTION_OPTIONS = listOf(3, 5, 10, 30)
        const val DEFAULT_RETENTION = 10
    }
}
