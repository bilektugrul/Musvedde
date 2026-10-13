package dev.aliarslan.musvedde.data.backup

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.settings.InMemoryPreferencesDataStore
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import dev.aliarslan.musvedde.domain.BackupFrequency
import dev.aliarslan.musvedde.domain.EditorFont
import dev.aliarslan.musvedde.domain.MonetTuning
import dev.aliarslan.musvedde.domain.ThemeMode
import dev.aliarslan.musvedde.domain.ThemePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class BackupSettingsTest {
    private val source = inMemoryDatabase()
    private val target = inMemoryDatabase()
    private val sourceSettings = SettingsRepository(InMemoryPreferencesDataStore())
    private val targetSettings = SettingsRepository(InMemoryPreferencesDataStore())

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private suspend fun backup(): ByteArray {
        NoteRepository(source, clock = { 1L }, io = Dispatchers.Unconfined).create("", "not")
        return ByteArrayOutputStream()
            .also { BackupSerializer(source, { 1L }, settingsStore = sourceSettings).write(it) }
            .toByteArray()
    }

    private suspend fun prepareSource() = sourceSettings.update {
        it.copy(
            themeMode = ThemeMode.AMOLED,
            themePalette = ThemePalette.CUSTOM,
            monet = MonetTuning(accentColor = 0xFF123456.toInt(), luminance = 30),
            editorFont = EditorFont.MONO,
            editorFontSizeSp = 24f,
            backupFrequency = BackupFrequency.WEEKLY,
            backupFolderUri = "content://source/folder",
            driveEnabled = true,
            lastDriveBackupAt = 99L,
        )
    }

    @Test
    fun backupsCarryPreferencesButNotThisDevicesBackupSetup() = runTest {
        prepareSource()

        val text = ZipInputStream(ByteArrayInputStream(backup())).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == "settings.json" }
            zip.readBytes().toString(Charsets.UTF_8)
        }

        assertThat(text).contains("AMOLED")
        assertThat(text).doesNotContain("content://source/folder")
        assertThat(text).doesNotContain("lockPin")
    }

    @Test
    fun restoringAppliesPreferencesAndKeepsTheDevicesOwnBackupSetup() = runTest {
        prepareSource()
        targetSettings.update { it.copy(backupFolderUri = "content://target/folder", lastFolderBackupAt = 7L) }
        val content = BackupSerializer(target, { 1L }).read(ByteArrayInputStream(backup()))

        RestoreService(target, BackupSerializer(target, { 1L }), targetSettings).restore(content, RestoreMode.MERGE).getOrThrow()

        val restored = targetSettings.settings.first()
        assertThat(restored.themeMode).isEqualTo(ThemeMode.AMOLED)
        assertThat(restored.themePalette).isEqualTo(ThemePalette.CUSTOM)
        assertThat(restored.monet.accentColor).isEqualTo(0xFF123456.toInt())
        assertThat(restored.monet.luminance).isEqualTo(30)
        assertThat(restored.editorFont).isEqualTo(EditorFont.MONO)
        assertThat(restored.editorFontSizeSp).isEqualTo(24f)
        assertThat(restored.backupFrequency).isEqualTo(BackupFrequency.WEEKLY)
        assertThat(restored.backupFolderUri).isEqualTo("content://target/folder")
        assertThat(restored.lastFolderBackupAt).isEqualTo(7L)
        assertThat(restored.driveEnabled).isFalse()
    }

    @Test
    fun backupsWithoutSettingsLeaveThemAlone() = runTest {
        targetSettings.update { it.copy(themeMode = ThemeMode.DARK) }
        NoteRepository(source, clock = { 1L }, io = Dispatchers.Unconfined).create("", "not")
        val bytes = ByteArrayOutputStream().also { BackupSerializer(source, { 1L }).write(it) }.toByteArray()
        val content = BackupSerializer(target, { 1L }).read(ByteArrayInputStream(bytes))

        assertThat(content.settings).isNull()
        RestoreService(target, BackupSerializer(target, { 1L }), targetSettings).restore(content, RestoreMode.MERGE).getOrThrow()

        assertThat(targetSettings.settings.first().themeMode).isEqualTo(ThemeMode.DARK)
    }
}
