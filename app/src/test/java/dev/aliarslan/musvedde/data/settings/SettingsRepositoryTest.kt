package dev.aliarslan.musvedde.data.settings

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.domain.AnimationSpeed
import dev.aliarslan.musvedde.domain.BackupFrequency
import dev.aliarslan.musvedde.domain.EditorFont
import dev.aliarslan.musvedde.domain.Settings
import dev.aliarslan.musvedde.domain.ThemeMode
import dev.aliarslan.musvedde.domain.ThemePalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SettingsRepositoryTest {
    private fun repository() = SettingsRepository(InMemoryPreferencesDataStore())

    @Test
    fun defaultsMatchSpec() = runTest {
        val settings = repository().settings.first()

        assertThat(settings).isEqualTo(Settings())
        assertThat(settings.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(settings.themePalette).isEqualTo(ThemePalette.WALLPAPER)
        assertThat(settings.editorFontSizeSp).isEqualTo(18f)
        assertThat(settings.editorLineHeight).isEqualTo(1.5f)
        assertThat(settings.editorFont).isEqualTo(EditorFont.SERIF)
        assertThat(settings.pinchZoom).isTrue()
        assertThat(settings.restoreDismissedPins).isTrue()
        assertThat(settings.backupFrequency).isEqualTo(BackupFrequency.OFF)
        assertThat(settings.backupRetention).isEqualTo(10)
        assertThat(settings.animationSpeed).isEqualTo(AnimationSpeed.NORMAL)
    }

    @Test
    fun updateRoundTrips() = runTest {
        val repository = repository()

        repository.update {
            it.copy(
                themeMode = ThemeMode.AMOLED,
                backupFolderUri = "content://tree/x",
                backupFrequency = BackupFrequency.WEEKLY,
                lastFolderBackupAt = 123L,
                lastDriveBackupError = "boom",
                animationSpeed = AnimationSpeed.SLOW,
                editorFont = EditorFont.GOOGLE_SANS,
            )
        }
        repository.update { it.copy(lastDriveBackupError = null) }

        val settings = repository.settings.first()
        assertThat(settings.themeMode).isEqualTo(ThemeMode.AMOLED)
        assertThat(settings.backupFolderUri).isEqualTo("content://tree/x")
        assertThat(settings.backupFrequency).isEqualTo(BackupFrequency.WEEKLY)
        assertThat(settings.lastFolderBackupAt).isEqualTo(123L)
        assertThat(settings.lastDriveBackupError).isNull()
        assertThat(settings.animationSpeed).isEqualTo(AnimationSpeed.SLOW)
        assertThat(settings.editorFont).isEqualTo(EditorFont.GOOGLE_SANS)
    }

    @Test
    fun fontSizeIsClamped() = runTest {
        val repository = repository()

        repository.update { it.copy(editorFontSizeSp = 40f, editorLineHeight = 0.5f, uiTextScale = 3f, backupRetention = 7) }

        val settings = repository.settings.first()
        assertThat(settings.editorFontSizeSp).isEqualTo(32f)
        assertThat(settings.editorLineHeight).isEqualTo(1.2f)
        assertThat(settings.uiTextScale).isEqualTo(1.5f)
        assertThat(settings.backupRetention).isEqualTo(10)
    }
}
