package dev.aliarslan.musvedde.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.aliarslan.musvedde.domain.Note
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteState

@Entity(
    tableName = "notes",
    indices = [
        Index("state", "pinnedToTop", "position"),
        Index("updatedAt"),
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val color: String,
    val pinnedToTop: Boolean,
    val pinnedToNotification: Boolean,
    val state: String,
    val position: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long?,
)

fun NoteEntity.toDomain(labelIds: List<Long> = emptyList()) = Note(
    id = id,
    title = title,
    body = body,
    color = NoteColor.fromName(color),
    pinnedToTop = pinnedToTop,
    pinnedToNotification = pinnedToNotification,
    state = NoteState.entries.firstOrNull { it.name == state } ?: NoteState.ACTIVE,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    trashedAt = trashedAt,
    labelIds = labelIds,
)
