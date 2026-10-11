package dev.aliarslan.musvedde.domain

data class NoteVersion(
    val id: Long,
    val noteId: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val sizeBytes: Long,
)
