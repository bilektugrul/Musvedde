package dev.aliarslan.musvedde.domain

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/** Where the app's colors come from: the wallpaper (Monet) or a fixed seed color. */
enum class ThemePalette(val seed: Long?) {
    WALLPAPER(null),
    PAPER(0xFF6E5530),
    OCEAN(0xFF1E6FB5),
    FOREST(0xFF2E7D4F),
    LAVENDER(0xFF7E57C2),
    ROSE(0xFFC2185B),
    SUNSET(0xFFE65100),
    SLATE(0xFF546E7A),
}

enum class PaletteStyleOption { TONAL_SPOT, VIBRANT, EXPRESSIVE, RAINBOW, FRUIT_SALAD, NEUTRAL, MONOCHROME, FIDELITY, CONTENT }

enum class ContrastLevel(val value: Double) { STANDARD(0.0), MEDIUM(0.5), HIGH(1.0) }

enum class ListLayout { GRID, LIST }

enum class EditorFont { SERIF, SYSTEM, GOOGLE_SANS, MONO }

enum class EditorAlign { START, CENTER }

enum class PreviewSize(val maxLines: Int) { SMALL(6), MEDIUM(10), LARGE(14) }

enum class BackupFrequency { OFF, DAILY, WEEKLY, MONTHLY }

enum class AnimationSpeed(val factor: Float) { SLOW(1.8f), NORMAL(1.35f), FAST(1.0f) }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePalette: ThemePalette = ThemePalette.WALLPAPER,
    val paletteStyle: PaletteStyleOption = PaletteStyleOption.TONAL_SPOT,
    val contrast: ContrastLevel = ContrastLevel.STANDARD,
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
    val autoRestoreChecked: Boolean = false,
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
