package dev.aliarslan.musvedde.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.domain.ListLayout
import dev.aliarslan.musvedde.ui.NotesSection

@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    layout: ListLayout,
    onToggleLayout: () -> Unit,
    onMenu: () -> Unit,
    title: String?,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.action_open_menu))
            }
            Box(Modifier.weight(1f)) {
                val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = style,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                title ?: stringResource(R.string.search_hint),
                                style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                            )
                        }
                        inner()
                    },
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
            IconButton(onClick = onToggleLayout) {
                if (layout == ListLayout.GRID) {
                    Icon(Icons.Outlined.ViewAgenda, contentDescription = stringResource(R.string.action_list_view))
                } else {
                    Icon(Icons.Outlined.GridView, contentDescription = stringResource(R.string.action_grid_view))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    section: NotesSection,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onColor: () -> Unit,
    onLabel: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onMoveToFolder: () -> Unit = {},
    selectionHidden: Boolean = false,
    onToggleHidden: () -> Unit = {},
    selectionLocked: Boolean = false,
    onToggleLocked: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_selection))
            }
        },
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count)) },
        actions = {
            if (section == NotesSection.TRASHED) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Outlined.RestoreFromTrash, contentDescription = stringResource(R.string.action_restore))
                }
                IconButton(onClick = onDeleteForever) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = stringResource(R.string.action_delete_forever))
                }
                return@TopAppBar
            }
            if (section != NotesSection.ARCHIVED) {
                IconButton(onClick = onPin) {
                    Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.action_pin))
                }
            }
            IconButton(onClick = onColor) {
                Icon(Icons.Outlined.Palette, contentDescription = stringResource(R.string.action_color))
            }
            IconButton(onClick = onLabel) {
                Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = stringResource(R.string.action_labels))
            }
            IconButton(onClick = onArchive) {
                if (section == NotesSection.ARCHIVED) {
                    Icon(Icons.Outlined.Unarchive, contentDescription = stringResource(R.string.action_unarchive))
                } else {
                    Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.action_archive))
                }
            }
            IconButton(onClick = onTrash) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_trash))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_to_folder)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onMoveToFolder()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (selectionHidden) R.string.action_show_on_main else R.string.action_hide_from_main)) },
                        leadingIcon = {
                            Icon(if (selectionHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onToggleHidden()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (selectionLocked) R.string.action_unlock_note else R.string.action_lock_note)) },
                        leadingIcon = { Icon(if (selectionLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onToggleLocked()
                        },
                    )
                }
            }
        },
    )
}
