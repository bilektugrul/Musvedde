package dev.aliarslan.musvedde.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.core.ErrorLogger
import dev.aliarslan.musvedde.data.errorlog.ErrorLogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.ZoneId
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    fun clock(): Clock = Clock.System

    @Provides
    fun zone(): ZoneId = ZoneId.systemDefault()

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Named(PENDING_CRASH_FILE)
    fun providePendingCrashFile(@ApplicationContext context: Context): File = pendingCrashFile(context)

    const val PENDING_CRASH_FILE = "pendingCrashFile"

    fun pendingCrashFile(context: Context) = File(context.filesDir, "pending-crash.json")
}

@Module
@InstallIn(SingletonComponent::class)
interface CoreBindings {
    @Binds
    fun errorLogger(repository: ErrorLogRepository): ErrorLogger
}
