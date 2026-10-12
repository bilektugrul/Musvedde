package dev.aliarslan.musvedde.data.backup

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.core.ErrorLogger
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.settings.InMemoryPreferencesDataStore
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FakeTarget(
    override val id: String,
    var ready: Boolean = true,
    var failure: Exception? = null,
) : BackupTarget {
    val files = linkedMapOf<String, ByteArray>()

    override suspend fun isReady() = ready

    override suspend fun write(name: String, writer: suspend (OutputStream) -> Unit) {
        failure?.let { throw it }
        files[name] = ByteArrayOutputStream().also { writer(it) }.toByteArray()
    }

    override suspend fun list() = files.map { (name, bytes) -> BackupFile(name, bytes.size.toLong(), 0, name) }

    override suspend fun open(file: BackupFile): InputStream = ByteArrayInputStream(files.getValue(file.ref))

    override suspend fun delete(file: BackupFile) {
        files.remove(file.ref)
    }
}

class RecordingLogger : ErrorLogger {
    val errors = mutableListOf<Pair<String, String>>()
    override fun error(source: String, message: String, throwable: Throwable?) {
        errors += source to message
    }
    override fun warning(source: String, message: String, throwable: Throwable?) = Unit
    override fun info(source: String, message: String) = Unit
}

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private var now = 1_000_000L
    private val clock = Clock { now }
    private val notes = NoteRepository(db, clock = clock, io = Dispatchers.Unconfined)
    private val settings = SettingsRepository(InMemoryPreferencesDataStore())
    private val logger = RecordingLogger()
    private val folder = FakeTarget(BackupTarget.FOLDER)
    private val drive = FakeTarget(BackupTarget.DRIVE)

    private fun manager(vararg targets: BackupTarget) = BackupManager(
        targets = targets.toSet(),
        serializer = BackupSerializer(db, clock),
        notes = notes,
        settings = settings,
        logger = logger,
        clock = clock,
        cacheDir = tmp.root,
        safetyDir = safetyDir,
    )

    private val safetyDir by lazy { tmp.newFolder("safety") }

    @After
    fun tearDown() = db.close()

    @Test
    fun missingVaultKeyIsReportedOnEveryTarget() = runTest {
        val id = notes.create("", "kilitli")
        db.noteDao().setLocked(listOf(id), true)
        val manager = BackupManager(
            targets = setOf(folder),
            serializer = BackupSerializer(db, clock, vaultKeys = { null }),
            notes = notes,
            settings = settings,
            logger = logger,
            clock = clock,
            cacheDir = tmp.root,
            safetyDir = safetyDir,
        )

        val result = manager.backupNow(scheduled = false) as BackupRunResult.Done

        assertThat(result.failed.keys).containsExactly(BackupTarget.FOLDER)
        assertThat(folder.files).isEmpty()
        assertThat(settings.settings.first().lastFolderBackupError).contains("PIN")
    }

    @Test
    fun scheduledSkipsWhenUnchanged() = runTest {
        notes.create("", "satır")
        now += 1_000
        manager(folder).backupNow(scheduled = false)
        now += 1_000

        val result = manager(folder).backupNow(scheduled = true)

        assertThat(result).isEqualTo(BackupRunResult.Skipped)
        assertThat(folder.files).hasSize(1)
    }

    @Test
    fun scheduledRunsAfterChange() = runTest {
        val id = notes.create("", "satır")
        now += 1_000
        manager(folder).backupNow(scheduled = false)
        now += 1_000
        notes.updateContent(id, "", "yeni satır")
        now += 1_000

        val result = manager(folder).backupNow(scheduled = true)

        assertThat(result).isInstanceOf(BackupRunResult.Done::class.java)
        assertThat(folder.files).hasSize(2)
    }

    @Test
    fun manualAlwaysRuns() = runTest {
        repeat(2) {
            manager(folder).backupNow(scheduled = false)
            now += 1_000
        }

        assertThat(folder.files).hasSize(2)
        assertThat(settings.settings.first().lastFolderBackupAt).isEqualTo(now - 1_000)
    }

    @Test
    fun backupContainsNotes() = runTest {
        notes.create("Başlık", "satır")

        manager(folder).backupNow(scheduled = false)

        val content = BackupSerializer(db, clock).read(ByteArrayInputStream(folder.files.values.single()))
        assertThat(content.notes.single().body).isEqualTo("satır")
    }

    @Test
    fun retentionKeepsNewestN() = runTest {
        settings.update { it.copy(backupRetention = 3) }
        repeat(5) {
            manager(folder).backupNow(scheduled = false)
            now += 1_000
        }

        assertThat(folder.files.keys).containsExactly(
            BackupSerializer.fileName(now - 3_000),
            BackupSerializer.fileName(now - 2_000),
            BackupSerializer.fileName(now - 1_000),
        )
    }

    @Test
    fun retentionIgnoresForeignFiles() = runTest {
        settings.update { it.copy(backupRetention = 3) }
        folder.files["shopping.txt"] = ByteArray(1)
        repeat(4) {
            manager(folder).backupNow(scheduled = false)
            now += 1_000
        }

        assertThat(folder.files).containsKey("shopping.txt")
        assertThat(folder.files).hasSize(4)
    }

    @Test
    fun failureIsLoggedAndRecorded() = runTest {
        drive.failure = IOException("network down")

        val result = manager(folder, drive).backupNow(scheduled = false) as BackupRunResult.Done

        assertThat(result.succeeded).containsExactly(BackupTarget.FOLDER)
        assertThat(result.failed.keys).containsExactly(BackupTarget.DRIVE)
        val s = settings.settings.first()
        assertThat(s.lastDriveBackupError).contains("network down")
        assertThat(s.lastFolderBackupError).isNull()
        assertThat(logger.errors.map { it.first }).containsExactly("Drive")
    }

    @Test
    fun safetyBackupWorksWithoutTargets() = runTest {
        notes.create("", "kaybolmasın")
        folder.ready = false

        val file = manager(folder).safetyBackup()

        val content = BackupSerializer(db, clock).read(file.inputStream())
        assertThat(content.notes.single().body).isEqualTo("kaybolmasın")
        assertThat(file.parentFile).isEqualTo(safetyDir)
    }

    @Test
    fun safetyBackupsKeepNewestThree() = runTest {
        repeat(5) {
            manager().safetyBackup()
            now += 1_000
        }

        assertThat(safetyDir.listFiles()!!.map { it.name }).containsExactly(
            BackupSerializer.fileName(now - 3_000),
            BackupSerializer.fileName(now - 2_000),
            BackupSerializer.fileName(now - 1_000),
        )
    }

    @Test
    fun noReadyTargets() = runTest {
        folder.ready = false

        assertThat(manager(folder).backupNow(scheduled = false)).isEqualTo(BackupRunResult.NoTargets)
    }
}
