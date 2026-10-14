package dev.aliarslan.musvedde.web

import dev.aliarslan.musvedde.data.db.WebNoteRow
import kotlinx.serialization.Serializable

@Serializable
data class WebNote(
    val id: Long,
    val title: String,
    val body: String,
    val color: String,
    val pinned: Boolean,
    val state: String,
    val position: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long?,
    val labelIds: List<Long>,
)

fun WebNoteRow.toWeb() = WebNote(
    id = id,
    title = title,
    body = body,
    color = color,
    pinned = pinnedToTop,
    state = state,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    trashedAt = trashedAt,
    labelIds = labelIds.orEmpty().split(',').mapNotNull { it.toLongOrNull() }.sorted(),
)

@Serializable
data class WebLabel(val id: Long, val name: String)

@Serializable
data class WebTheme(val dark: Boolean, val colors: Map<String, String>, val noteColors: Map<String, String>)

@Serializable
data class Bootstrap(
    val notes: List<WebNote>,
    val labels: List<WebLabel>,
    val revision: Long,
    val theme: WebTheme,
    val locale: String,
    val appVersion: String,
)

@Serializable
data class NotesSince(val notes: List<WebNote>, val deletedIds: List<Long>, val revision: Long)

@Serializable
data class PairRequest(val code: String, val name: String)

@Serializable
data class NewNote(val title: String, val body: String, val color: String? = null, val labelIds: List<Long> = emptyList())

@Serializable
data class NoteContent(val title: String, val body: String, val baseUpdatedAt: Long, val force: Boolean = false)

@Serializable
data class BulkRequest(val ids: List<Long>, val action: String, val color: String? = null, val labelId: Long? = null)

@Serializable
data class LabelName(val name: String)

@Serializable
data class RevisionResponse(val revision: Long)

@Serializable
data class ImportResult(val added: Int, val skipped: Int)

@Serializable
data class ErrorResponse(val error: String, val retryAfterSeconds: Long? = null)

@Serializable
data class ConflictResponse(val error: String, val note: WebNote)

@Serializable
data class RevisionEvent(val revision: Long)
