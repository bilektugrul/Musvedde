package dev.aliarslan.musvedde.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.data.note.FolderRepository
import dev.aliarslan.musvedde.domain.Folder
import dev.aliarslan.musvedde.ui.editor.NewLabelField
import dev.aliarslan.musvedde.ui.lock.rememberRequireUnlock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoldersViewModel @Inject constructor(private val repository: FolderRepository) : ViewModel() {
    val folders = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

    fun create(name: String) = viewModelScope.launch { repository.create(name) }
    fun rename(id: Long, name: String) = viewModelScope.launch { repository.rename(id, name) }
    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
    fun setLocked(id: Long, locked: Boolean) = viewModelScope.launch { repository.setLocked(id, locked) }
}

@Composable
fun EditFoldersDialog(onDismiss: () -> Unit, viewModel: FoldersViewModel = hiltViewModel()) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val requireUnlock = rememberRequireUnlock()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nav_edit_folders)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                NewLabelField(onCreate = { viewModel.create(it) }, hint = stringResource(R.string.folder_new_hint))
                folders.forEach { folder ->
                    FolderRow(
                        folder = folder,
                        onRename = { viewModel.rename(folder.id, it) },
                        onToggleLock = { requireUnlock { viewModel.setLocked(folder.id, !folder.locked) } },
                        onDelete = { if (folder.locked) requireUnlock { viewModel.delete(folder.id) } else viewModel.delete(folder.id) },
                    )
                }
                Text(
                    stringResource(R.string.folder_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
private fun FolderRow(folder: Folder, onRename: (String) -> Unit, onToggleLock: () -> Unit, onDelete: () -> Unit) {
    var name by remember(folder.name) { mutableStateOf(folder.name) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank() && name != folder.name) onRename(name) }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleLock) {
            Icon(
                if (folder.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                contentDescription = stringResource(if (folder.locked) R.string.folder_unlock else R.string.folder_lock, folder.name),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.folder_delete, folder.name))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    folders: ImmutableList<Folder>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(
                stringResource(R.string.folder_move_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            FolderOption(stringResource(R.string.folder_none), selected == null, locked = false) { onSelect(null) }
            folders.forEach { folder ->
                FolderOption(folder.name, folder.id == selected, folder.locked) { onSelect(folder.id) }
            }
            NewLabelField(onCreate, hint = stringResource(R.string.folder_new_hint))
        }
    }
}

@Composable
private fun FolderOption(name: String, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (locked) Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
    }
}
