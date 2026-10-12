package dev.aliarslan.musvedde.ui.settings

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.domain.AnimationSpeed
import dev.aliarslan.musvedde.domain.BackupFrequency
import dev.aliarslan.musvedde.domain.ContrastLevel
import dev.aliarslan.musvedde.domain.PaletteStyleOption
import dev.aliarslan.musvedde.domain.ThemePalette
import dev.aliarslan.musvedde.domain.EditorFont
import dev.aliarslan.musvedde.domain.ListLayout
import dev.aliarslan.musvedde.domain.MonetTuning
import dev.aliarslan.musvedde.domain.Settings
import dev.aliarslan.musvedde.domain.ThemeMode
import org.junit.Test

class SettingsCategoryTest {
    private val changed = Settings(
        themeMode = ThemeMode.AMOLED,
        themePalette = ThemePalette.OCEAN,
        paletteStyle = PaletteStyleOption.VIBRANT,
        contrast = ContrastLevel.HIGH,
        monet = MonetTuning(luminance = 40, tintedBackground = false),
        listLayout = ListLayout.LIST,
        uiTextScale = 1.3f,
        editorFontSizeSp = 26f,
        editorFont = EditorFont.MONO,
        animationSpeed = AnimationSpeed.FAST,
        highContrast = true,
        alwaysFocusMode = true,
        restoreDismissedPins = false,
        backupFolderUri = "content://tree/x",
        driveEnabled = true,
        backupFrequency = BackupFrequency.WEEKLY,
        backupRetention = 3,
        lastFolderBackupAt = 42,
    )

    @Test
    fun accessibilityResetLeavesOtherCategoriesAlone() {
        val reset = SettingsCategory.ACCESSIBILITY.reset(changed)!!

        assertThat(reset.uiTextScale).isEqualTo(1f)
        assertThat(reset.editorFontSizeSp).isEqualTo(18f)
        assertThat(reset.editorFont).isEqualTo(EditorFont.SERIF)
        assertThat(reset.animationSpeed).isEqualTo(AnimationSpeed.NORMAL)
        assertThat(reset.highContrast).isFalse()
        assertThat(reset.themeMode).isEqualTo(ThemeMode.AMOLED)
        assertThat(reset.alwaysFocusMode).isTrue()
    }

    @Test
    fun appearanceResetRestoresThemeAndLayout() {
        val reset = SettingsCategory.APPEARANCE.reset(changed)!!

        assertThat(reset.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(reset.listLayout).isEqualTo(ListLayout.GRID)
        assertThat(reset.themePalette).isEqualTo(ThemePalette.OCEAN)
        assertThat(reset.editorFontSizeSp).isEqualTo(26f)
    }

    @Test
    fun colorsResetRestoresPaletteAndMonetTuning() {
        val reset = SettingsCategory.COLORS.reset(changed)!!

        assertThat(reset.themePalette).isEqualTo(ThemePalette.WALLPAPER)
        assertThat(reset.paletteStyle).isEqualTo(PaletteStyleOption.TONAL_SPOT)
        assertThat(reset.contrast).isEqualTo(ContrastLevel.STANDARD)
        assertThat(reset.monet).isEqualTo(MonetTuning())
        assertThat(reset.themeMode).isEqualTo(ThemeMode.AMOLED)
    }

    @Test
    fun backupResetKeepsTheFolderDriveAndHistory() {
        val reset = SettingsCategory.BACKUP.reset(changed)!!

        assertThat(reset.backupFrequency).isEqualTo(BackupFrequency.OFF)
        assertThat(reset.backupRetention).isEqualTo(10)
        assertThat(reset.backupFolderUri).isEqualTo("content://tree/x")
        assertThat(reset.driveEnabled).isTrue()
        assertThat(reset.lastFolderBackupAt).isEqualTo(42)
    }

    @Test
    fun editorAndNotificationResets() {
        assertThat(SettingsCategory.EDITOR.reset(changed)!!.alwaysFocusMode).isFalse()
        assertThat(SettingsCategory.NOTIFICATIONS.reset(changed)!!.restoreDismissedPins).isTrue()
    }

    @Test
    fun categoriesWithoutSettingsCannotBeReset() {
        assertThat(SettingsCategory.STORAGE.reset(changed)).isNull()
        assertThat(SettingsCategory.TROUBLESHOOTING.reset(changed)).isNull()
        assertThat(SettingsCategory.ABOUT.reset(changed)).isNull()
    }
}
