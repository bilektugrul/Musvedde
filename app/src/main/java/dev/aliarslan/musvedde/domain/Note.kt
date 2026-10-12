package dev.aliarslan.musvedde.domain

data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val color: NoteColor,
    val pinnedToTop: Boolean,
    val pinnedToNotification: Boolean,
    val state: NoteState,
    val position: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long?,
    val labelIds: List<Long>,
    val folderId: Long? = null,
    val hidden: Boolean = false,
    val locked: Boolean = false,
)
