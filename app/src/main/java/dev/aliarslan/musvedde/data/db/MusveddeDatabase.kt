package dev.aliarslan.musvedde.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        NoteEntity::class,
        NoteFtsEntity::class,
        LabelEntity::class,
        NoteLabelCrossRef::class,
        ErrorLogEntity::class,
        NoteVersionEntity::class,
        PendingVersionEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class MusveddeDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun labelDao(): LabelDao
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun noteVersionDao(): NoteVersionDao

    companion object {
        const val NAME = "musvedde.db"
    }
}
