package dev.aliarslan.musvedde.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.aliarslan.musvedde.domain.ErrorEntry
import dev.aliarslan.musvedde.domain.ErrorLevel

@Entity(tableName = "error_log", indices = [Index("timestamp")])
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val source: String,
    val message: String,
    val stackTrace: String?,
)

fun ErrorLogEntity.toDomain() = ErrorEntry(
    id = id,
    timestamp = timestamp,
    level = ErrorLevel.entries.firstOrNull { it.name == level } ?: ErrorLevel.ERROR,
    source = source,
    message = message,
    stackTrace = stackTrace,
)
