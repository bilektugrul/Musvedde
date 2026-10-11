package dev.aliarslan.musvedde

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.errorlog.CrashRecorder
import dev.aliarslan.musvedde.data.errorlog.ErrorLogRepository
import dev.aliarslan.musvedde.di.ApplicationScope
import dev.aliarslan.musvedde.di.CoreModule
import kotlinx.coroutines.CoroutineScope
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

    override fun onCreate() {
        CrashRecorder(CoreModule.pendingCrashFile(this), Clock.System).install()
        super.onCreate()
        appScope.launch { errorLog.importPendingCrash() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
