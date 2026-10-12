package dev.aliarslan.musvedde.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.aliarslan.musvedde.domain.Folder

@Entity(tableName = "folders", indices = [Index(value = ["name"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val locked: Boolean = false,
)

fun FolderEntity.toDomain() = Folder(id = id, name = name, locked = locked)
