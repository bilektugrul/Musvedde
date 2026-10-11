package dev.aliarslan.musvedde.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.aliarslan.musvedde.domain.Label

@Entity(tableName = "labels", indices = [Index(value = ["name"], unique = true)])
data class LabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

fun LabelEntity.toDomain() = Label(id = id, name = name)
