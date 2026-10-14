package dev.aliarslan.musvedde.data.db

data class WebNoteRow(
    val id: Long,
    val title: String,
    val body: String,
    val color: String,
    val pinnedToTop: Boolean,
    val state: String,
    val position: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long?,
    val labelIds: String?,
)
