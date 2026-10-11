package dev.aliarslan.musvedde.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.domain.Label
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AppDrawer(
    current: NotesRoute?,
    labels: ImmutableList<Label>,
    onNotes: (NotesRoute) -> Unit,
    onEditLabels: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet {
        androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
            )
            DrawerItem(Icons.Outlined.Lightbulb, stringResource(R.string.nav_notes), current?.section == NotesSection.ACTIVE) {
                onNotes(NotesRoute(NotesSection.ACTIVE))
            }
            if (labels.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                Text(
                    stringResource(R.string.nav_labels),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
            labels.forEach { label ->
                DrawerItem(
                    Icons.AutoMirrored.Outlined.Label,
                    label.name,
                    current?.section == NotesSection.LABEL && current.labelId == label.id,
                ) { onNotes(NotesRoute(NotesSection.LABEL, label.id)) }
            }
            DrawerItem(Icons.Outlined.Edit, stringResource(R.string.nav_edit_labels), false, onEditLabels)
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            DrawerItem(Icons.Outlined.Archive, stringResource(R.string.nav_archive), current?.section == NotesSection.ARCHIVED) {
                onNotes(NotesRoute(NotesSection.ARCHIVED))
            }
            DrawerItem(Icons.Outlined.Delete, stringResource(R.string.nav_trash), current?.section == NotesSection.TRASHED) {
                onNotes(NotesRoute(NotesSection.TRASHED))
            }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            DrawerItem(Icons.Outlined.Settings, stringResource(R.string.nav_settings), false, onSettings)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
