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
    /** The note lives in the secret folder. */
    val locked: Boolean = false,
)
