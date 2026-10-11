package dev.aliarslan.musvedde.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.aliarslan.musvedde.data.backup.BackupManager
import dev.aliarslan.musvedde.data.backup.BackupTarget
import dev.aliarslan.musvedde.data.backup.FolderBackupTarget
import java.io.File
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Named(BackupManager.CACHE_DIR)
    fun backupCacheDir(@ApplicationContext context: Context): File = context.cacheDir
}

@Module
@InstallIn(SingletonComponent::class)
interface BackupTargetsModule {
    @Binds
    @IntoSet
    fun folder(target: FolderBackupTarget): BackupTarget
}
