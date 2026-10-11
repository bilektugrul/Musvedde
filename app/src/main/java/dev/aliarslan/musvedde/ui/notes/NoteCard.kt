package dev.aliarslan.musvedde.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.domain.EditorFont
import dev.aliarslan.musvedde.domain.NoteSummary
import dev.aliarslan.musvedde.domain.PreviewSize
import dev.aliarslan.musvedde.ui.theme.fontFamily
import dev.aliarslan.musvedde.ui.theme.LocalAppearance
import dev.aliarslan.musvedde.ui.theme.noteContainerColor
import kotlinx.collections.immutable.ImmutableMap

@Composable
fun NoteCard(
    note: NoteSummary,
    labelNames: ImmutableMap<Long, String>,
    selected: Boolean,
    previewSize: PreviewSize,
    previewFont: EditorFont,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalAppearance.current
    val container = noteContainerColor(note.color, appearance.dark, appearance.amoled)
    val colors = MaterialTheme.colorScheme
    val border = when {
        selected -> BorderStroke(2.dp, colors.primary)
        appearance.highContrast -> BorderStroke(1.5.dp, colors.onSurface)
        container == Color.Unspecified -> BorderStroke(1.dp, colors.outlineVariant)
        else -> null
    }
    val textColor = if (appearance.highContrast) colors.onSurface else colors.onSurface.copy(alpha = 0.87f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (container == Color.Unspecified) colors.surface else container,
        border = border,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (note.title.isNotBlank()) {
                Text(
                    note.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.preview.isNotBlank()) {
                Text(
                    note.preview.trimEnd(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = previewFont.fontFamily()),
                    color = textColor,
                    maxLines = previewSize.maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.labelIds.isNotEmpty() || note.pinnedToNotification) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (note.pinnedToNotification) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = stringResource(R.string.note_pinned_to_notification),
                            modifier = Modifier.size(16.dp),
                            tint = textColor,
                        )
                    }
                    note.labelIds.forEach { id ->
                        labelNames[id]?.let { LabelChip(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelChip(name: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
