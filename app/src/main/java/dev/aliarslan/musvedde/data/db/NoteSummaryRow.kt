package dev.aliarslan.musvedde.data.db

import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteSummary
import kotlinx.collections.immutable.toImmutableList

data class NoteSummaryRow(
    val id: Long,
    val title: String,
    val preview: String,
    val color: String,
    val pinnedToTop: Boolean,
    val pinnedToNotification: Boolean,
    val position: Long,
    val updatedAt: Long,
    val labelIds: String?,
)

fun NoteSummaryRow.toDomain() = NoteSummary(
    id = id,
    title = title,
    preview = preview,
    color = NoteColor.fromName(color),
    pinnedToTop = pinnedToTop,
    pinnedToNotification = pinnedToNotification,
    position = position,
    updatedAt = updatedAt,
    labelIds = labelIds.orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toImmutableList(),
)
