package dev.aliarslan.musvedde

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.errorlog.CrashRecorder
import dev.aliarslan.musvedde.data.backup.BackupScheduler
import dev.aliarslan.musvedde.data.errorlog.ErrorLogRepository
import dev.aliarslan.musvedde.data.note.OpenNoteTracker
import dev.aliarslan.musvedde.data.note.TrashPurgeWorker
import dev.aliarslan.musvedde.data.note.VersionRepository
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import dev.aliarslan.musvedde.di.ApplicationScope
import dev.aliarslan.musvedde.di.CoreModule
import dev.aliarslan.musvedde.notification.PinnedNoteNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MusveddeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var errorLog: ErrorLogRepository

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var backupScheduler: Lazy<BackupScheduler>

    @Inject
    lateinit var workManager: Lazy<WorkManager>

    @Inject
    lateinit var pinnedNoteNotifier: PinnedNoteNotifier

    @Inject
    lateinit var versions: VersionRepository

    @Inject
    lateinit var openNotes: OpenNoteTracker

    override fun onCreate() {
        CrashRecorder(CoreModule.pendingCrashFile(this), Clock.System).install()
        super.onCreate()
        appScope.launch { errorLog.importPendingCrash() }
        appScope.launch { versions.recoverPending() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = openNotes.setForeground(true)
                override fun onStop(owner: LifecycleOwner) = openNotes.setForeground(false)
            },
        )
        pinnedNoteNotifier.start(appScope)
        TrashPurgeWorker.schedule(workManager.get())
        appScope.launch {
            val current = settings.settings.first()
            backupScheduler.get().apply(current.backupFrequency, current.driveEnabled)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
