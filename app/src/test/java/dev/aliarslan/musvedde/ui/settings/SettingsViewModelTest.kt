package dev.aliarslan.musvedde.ui.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.backup.BackupManager
import dev.aliarslan.musvedde.data.backup.BackupScheduler
import dev.aliarslan.musvedde.data.backup.BackupSerializer
import dev.aliarslan.musvedde.data.backup.KeepImporter
import dev.aliarslan.musvedde.data.backup.RestoreMode
import dev.aliarslan.musvedde.data.db.noteEntity
import dev.aliarslan.musvedde.data.lock.FakeKeyWrapper
import dev.aliarslan.musvedde.data.lock.LockConfig
import dev.aliarslan.musvedde.data.lock.LockManager
import dev.aliarslan.musvedde.data.lock.UnlockResult
import dev.aliarslan.musvedde.data.backup.RecordingLogger
import dev.aliarslan.musvedde.data.backup.RestoreService
import dev.aliarslan.musvedde.data.backup.drive.DriveAuth
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.settings.InMemoryPreferencesDataStore
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import dev.aliarslan.musvedde.domain.BackupFrequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: MusveddeDatabase
    private lateinit var workManager: WorkManager
    private val settings = SettingsRepository(InMemoryPreferencesDataStore())
    private val lockManager = LockManager(InMemoryPreferencesDataStore(), FakeKeyWrapper(), clock = { 1L }, LockConfig(1_000, 1_000))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = inMemoryDatabase(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(): SettingsViewModel {
        val clock = Clock { 1L }
        val serializer = BackupSerializer(db, clock)
        val logger = RecordingLogger()
        return SettingsViewModel(
            context = context,
            settingsRepository = settings,
            scheduler = BackupScheduler(workManager),
            backupManager = BackupManager(
                targets = emptySet(),
                serializer = serializer,
                notes = NoteRepository(db, clock, dispatcher),
                settings = settings,
                logger = logger,
                clock = clock,
                cacheDir = tmp.root,
                safetyDir = tmp.newFolder("safety"),
            ),
            restoreService = RestoreService(db, serializer),
            keepImporter = KeepImporter(clock),
            lockManager = lockManager,
            driveAuth = DriveAuth(context),
            logger = logger,
            workManager = workManager,
            appScope = appScope,
        )
    }

    @Test
    fun frequencyChangeReschedules() = runTest(dispatcher) {
        val vm = viewModel()

        vm.update { it.copy(backupFrequency = BackupFrequency.DAILY) }
        advanceUntilIdle()

        assertThat(settings.settings.first().backupFrequency).isEqualTo(BackupFrequency.DAILY)
        val work = workManager.getWorkInfosForUniqueWork(BackupScheduler.PERIODIC).get()
            .single { it.state != WorkInfo.State.CANCELLED }
        assertThat(work.periodicityInfo!!.repeatIntervalMillis).isEqualTo(TimeUnit.DAYS.toMillis(1))
    }

    @Test
    fun unrelatedChangeDoesNotSchedule() = runTest(dispatcher) {
        val vm = viewModel()

        vm.update { it.copy(highContrast = true) }
        advanceUntilIdle()

        assertThat(workManager.getWorkInfosForUniqueWork(BackupScheduler.PERIODIC).get()).isEmpty()
    }

    @Test
    fun resettingBackupStopsTheSchedule() = runTest(dispatcher) {
        val vm = viewModel()
        vm.update { it.copy(backupFrequency = BackupFrequency.DAILY, backupRetention = 3) }
        advanceUntilIdle()

        vm.resetCategory(SettingsCategory.BACKUP)
        advanceUntilIdle()

        val s = settings.settings.first()
        assertThat(s.backupFrequency).isEqualTo(BackupFrequency.OFF)
        assertThat(s.backupRetention).isEqualTo(10)
        val active = workManager.getWorkInfosForUniqueWork(BackupScheduler.PERIODIC).get()
            .filter { it.state != WorkInfo.State.CANCELLED }
        assertThat(active).isEmpty()
    }

    private fun TestScope.awaitRestore(vm: SettingsViewModel) {
        repeat(500) {
            advanceUntilIdle()
            if (!vm.restoring.value) return
            Thread.sleep(10)
        }
        error("Import did not finish")
    }

    @Test
    fun importingKeepAddsItsNotesOnce() = runTest(dispatcher) {
        val export = tmp.newFile("takeout.zip")
        ZipOutputStream(export.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Takeout/Keep/not.json"))
            zip.write("""{"title":"Keep","textContent":"satır","userEditedTimestampUsec":5000000}""".toByteArray())
            zip.closeEntry()
        }
        val vm = viewModel()

        repeat(2) {
            vm.importKeep(Uri.fromFile(export))
            awaitRestore(vm)
        }

        assertThat(db.noteDao().count()).isEqualTo(1)
        assertThat(vm.restoring.value).isFalse()
    }

    @Test
    fun restoringLockedNotesAsksForTheBackupPinAndAdoptsIt() = runTest(dispatcher) {
        val export = tmp.newFile("backup.zip")
        val source = inMemoryDatabase()
        val sourceLock = LockManager(InMemoryPreferencesDataStore(), FakeKeyWrapper(), clock = { 1L }, LockConfig(1_000, 1_000))
        sourceLock.setPin("2468")
        val id = source.noteDao().insert(noteEntity(title = "sır", body = "kilitli"))
        source.noteDao().setLocked(listOf(id), true)
        export.outputStream().use { BackupSerializer(source, { 1L }, sourceLock).write(it) }
        source.close()
        val vm = viewModel()

        vm.restore(Uri.fromFile(export), RestoreMode.MERGE)
        awaitRestore(vm)
        assertThat(vm.backupPinRequest.value).isFalse()

        vm.restoreWithPin("1111")
        awaitRestore(vm)
        assertThat(vm.backupPinRequest.value).isTrue()
        assertThat(db.noteDao().count()).isEqualTo(0)

        vm.restoreWithPin("2468")
        awaitRestore(vm)
        assertThat(vm.backupPinRequest.value).isNull()
        assertThat(db.noteDao().getById(db.noteDao().pageAll(10, 0).single().id)!!.locked).isTrue()
        assertThat(lockManager.hasPin.first()).isTrue()
        lockManager.lock()
        assertThat(lockManager.unlock("2468")).isEqualTo(UnlockResult.Unlocked)
    }

    @Test
    fun retentionOptionsAre3_5_10_30() {
        assertThat(viewModel().retentionOptions).containsExactly(3, 5, 10, 30).inOrder()
    }
}
