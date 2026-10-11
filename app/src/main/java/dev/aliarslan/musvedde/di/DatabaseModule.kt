package dev.aliarslan.musvedde.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MusveddeDatabase =
        Room.databaseBuilder(context, MusveddeDatabase::class.java, MusveddeDatabase.NAME).build()

    @Provides
    fun noteDao(db: MusveddeDatabase) = db.noteDao()

    @Provides
    fun labelDao(db: MusveddeDatabase) = db.labelDao()

    @Provides
    fun errorLogDao(db: MusveddeDatabase) = db.errorLogDao()
}
