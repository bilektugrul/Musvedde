package dev.aliarslan.musvedde.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_versions",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("noteId", "createdAt")],
)
data class NoteVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val sizeBytes: Long,
)

@Entity(
    tableName = "pending_versions",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PendingVersionEntity(
    @PrimaryKey val noteId: Long,
    val baselineTitle: String,
    val baselineBody: String,
    val baselineAt: Long,
)

data class VersionTotals(val count: Int, val bytes: Long)

data class NoteStorageRow(
    val id: Long,
    val title: String,
    val preview: String,
    val state: String,
    val noteBytes: Long,
    val versionCount: Int,
    val versionBytes: Long,
)

fun utf8Size(title: String, body: String): Long =
    (title.toByteArray(Charsets.UTF_8).size + body.toByteArray(Charsets.UTF_8).size).toLong()
