package dev.aliarslan.musvedde.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        NoteEntity::class,
        NoteFtsEntity::class,
        LabelEntity::class,
        NoteLabelCrossRef::class,
        ErrorLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MusveddeDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun labelDao(): LabelDao
    abstract fun errorLogDao(): ErrorLogDao

    companion object {
        const val NAME = "musvedde.db"
    }
}
