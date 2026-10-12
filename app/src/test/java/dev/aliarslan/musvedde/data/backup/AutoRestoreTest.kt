package dev.aliarslan.musvedde.data.backup

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.lock.FakeKeyWrapper
import dev.aliarslan.musvedde.data.lock.LockConfig
import dev.aliarslan.musvedde.data.lock.LockManager
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.settings.InMemoryPreferencesDataStore
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class AutoRestoreTest {
    private val source = inMemoryDatabase()
    private val db = inMemoryDatabase()
    private val notes = NoteRepository(db, clock = { 5L }, io = Dispatchers.Unconfined)
    private val settings = SettingsRepository(InMemoryPreferencesDataStore())
    private val logger = RecordingLogger()
    private val drive = FakeSilentSource()
    private val lockManager = LockManager(InMemoryPreferencesDataStore(), FakeKeyWrapper(), clock = { 1L }, LockConfig(1_000, 1_000))

    @After
    fun tearDown() {
        source.close()
        db.close()
    }

    private suspend fun backupWith(vararg bodies: String): ByteArray {
        val sourceNotes = NoteRepository(source, clock = { 1L }, io = Dispatchers.Unconfined)
        bodies.forEach { sourceNotes.create("", it) }
        return ByteArrayOutputStream().also { BackupSerializer(source, clock = { 1L }).write(it) }.toByteArray()
    }

    private fun TestScope.autoRestore() = AutoRestore(
        settings = settings,
        db = db,
        source = drive,
        serializer = BackupSerializer(db, clock = { 1L }),
        restoreService = RestoreService(db, BackupSerializer(db, clock = { 1L })),
        logger = logger,
        lockManager = lockManager,
        scope = backgroundScope,
    )

    @Test
    fun restoresTheNewestBackupOnFirstLaunch() = runTest {
        drive.files["musvedde-backup-2026-01-01.zip"] = backupWith("eski")
        drive.files["musvedde-backup-2026-02-01.zip"] = backupWith("yeni", "ikinci")
        val auto = autoRestore()

        auto.runOnFirstLaunch()

        val done = auto.state.value as AutoRestoreState.Done
        assertThat(done.noteCount).isEqualTo(3)
        assertThat(drive.opened).containsExactly("musvedde-backup-2026-02-01.zip")
        assertThat(db.noteDao().count()).isEqualTo(3)
        assertThat(settings.settings.first().driveEnabled).isTrue()
    }

    @Test
    fun finishedBannerHidesAfterThirtySeconds() = runTest {
        drive.files["musvedde-backup-2026-01-01.zip"] = backupWith("not")
        val auto = autoRestore()

        auto.runOnFirstLaunch()
        advanceTimeBy(29_000)
        runCurrent()
        assertThat(auto.state.value).isInstanceOf(AutoRestoreState.Done::class.java)

        advanceTimeBy(1_001)
        runCurrent()
        assertThat(auto.state.value).isNull()
    }

    @Test
    fun dismissHidesTheBanner() = runTest {
        drive.files["musvedde-backup-2026-01-01.zip"] = backupWith("not")
        val auto = autoRestore()
        auto.runOnFirstLaunch()

        auto.dismiss()

        assertThat(auto.state.value).isNull()
    }

    @Test
    fun runsOnlyOnce() = runTest {
        autoRestore().runOnFirstLaunch()
        drive.files["musvedde-backup-2026-01-01.zip"] = backupWith("not")

        autoRestore().runOnFirstLaunch()

        assertThat(drive.listCalls).isEqualTo(1)
        assertThat(db.noteDao().count()).isEqualTo(0)
    }

    @Test
    fun skipsWhenNotesAlreadyExist() = runTest {
        notes.create("", "mevcut")
        drive.files["musvedde-backup-2026-01-01.zip"] = backupWith("not")
        val auto = autoRestore()

        auto.runOnFirstLaunch()

        assertThat(drive.listCalls).isEqualTo(0)
        assertThat(auto.state.value).isNull()
    }

    @Test
    fun staysQuietWhenDriveNeedsTheUser() = runTest {
        drive.authorized = false
        drive.files["musvedde-backup-2026-01-01.zip"] = backupWith("not")
        val auto = autoRestore()

        auto.runOnFirstLaunch()

        assertThat(auto.state.value).isNull()
        assertThat(settings.settings.first().driveEnabled).isFalse()
    }

    @Test
    fun staysQuietWithoutBackups() = runTest {
        val auto = autoRestore()

        auto.runOnFirstLaunch()

        assertThat(auto.state.value).isNull()
        assertThat(settings.settings.first().driveEnabled).isFalse()
    }

    @Test
    fun lockedNotesWaitForThePinAndTheBannerStays() = runTest {
        val sourceLock = LockManager(InMemoryPreferencesDataStore(), FakeKeyWrapper(), clock = { 1L }, LockConfig(1_000, 1_000))
        sourceLock.setPin("2468")
        val sourceNotes = NoteRepository(source, clock = { 1L }, io = Dispatchers.Unconfined)
        sourceNotes.create("", "açık")
        val secret = sourceNotes.create("", "kilitli")
        sourceNotes.setLocked(listOf(secret), true)
        drive.files["musvedde-backup-2026-01-01.zip"] =
            ByteArrayOutputStream().also { BackupSerializer(source, { 1L }, sourceLock).write(it) }.toByteArray()
        val auto = autoRestore()

        auto.runOnFirstLaunch()
        advanceTimeBy(60_000)
        runCurrent()

        val waiting = auto.state.value as AutoRestoreState.Done
        assertThat(waiting.noteCount).isEqualTo(1)
        assertThat(waiting.lockedPending).isEqualTo(1)
        assertThat(auto.unlockPending("1111")).isFalse()
        assertThat((auto.state.value as AutoRestoreState.Done).wrongPin).isTrue()

        assertThat(auto.unlockPending("2468")).isTrue()
        val done = auto.state.value as AutoRestoreState.Done
        assertThat(done.noteCount).isEqualTo(2)
        assertThat(done.lockedPending).isEqualTo(0)
        assertThat(db.noteDao().count()).isEqualTo(2)
        assertThat(lockManager.hasPin.first()).isTrue()
    }

    @Test
    fun reportsDownloadFailures() = runTest {
        drive.files["musvedde-backup-2026-01-01.zip"] = backupWith("not")
        drive.openFailure = IOException("bağlantı koptu")
        val auto = autoRestore()

        auto.runOnFirstLaunch()

        assertThat(auto.state.value).isEqualTo(AutoRestoreState.Failed)
        assertThat(logger.errors.single().first).isEqualTo("Drive")
    }
}

class FakeSilentSource : SilentBackupSource {
    val files = linkedMapOf<String, ByteArray>()
    val opened = mutableListOf<String>()
    var authorized = true
    var listCalls = 0
    var openFailure: Exception? = null

    override suspend fun listSilently(): List<BackupFile>? {
        listCalls++
        if (!authorized) return null
        return files.map { (name, bytes) -> BackupFile(name, bytes.size.toLong(), 0, name) }
    }

    override suspend fun openSilently(file: BackupFile): InputStream {
        openFailure?.let { throw it }
        opened += file.ref
        return ByteArrayInputStream(files.getValue(file.ref))
    }
}
