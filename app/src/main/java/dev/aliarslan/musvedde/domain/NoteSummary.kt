package dev.aliarslan.musvedde.domain

import kotlinx.collections.immutable.ImmutableList

data class NoteSummary(
    val id: Long,
    val title: String,
    val preview: String,
    val color: NoteColor,
    val pinnedToTop: Boolean,
    val pinnedToNotification: Boolean,
    val position: Long,
    val updatedAt: Long,
    val labelIds: ImmutableList<Long>,
    val folderId: Long? = null,
    val hidden: Boolean = false,
    /** True when the note itself or its folder is locked. */
    val locked: Boolean = false,
)
