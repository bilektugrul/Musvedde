package dev.aliarslan.musvedde.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.storage.DatabaseFiles
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MusveddeDatabase =
        Room.databaseBuilder(context, MusveddeDatabase::class.java, MusveddeDatabase.NAME).build()

    @Provides
    fun databaseFiles(@ApplicationContext context: Context): DatabaseFiles = DatabaseFiles {
        val db = context.getDatabasePath(MusveddeDatabase.NAME)
        listOf(db, File(db.path + "-wal"), File(db.path + "-shm"))
    }

    @Provides
    fun noteDao(db: MusveddeDatabase) = db.noteDao()

    @Provides
    fun labelDao(db: MusveddeDatabase) = db.labelDao()

    @Provides
    fun folderDao(db: MusveddeDatabase) = db.folderDao()

    @Provides
    fun errorLogDao(db: MusveddeDatabase) = db.errorLogDao()

    @Provides
    fun noteVersionDao(db: MusveddeDatabase) = db.noteVersionDao()
}
