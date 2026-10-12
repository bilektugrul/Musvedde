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
    fun retentionOptionsAre3_5_10_30() {
        assertThat(viewModel().retentionOptions).containsExactly(3, 5, 10, 30).inOrder()
    }
}
