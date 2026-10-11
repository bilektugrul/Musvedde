package dev.aliarslan.musvedde.ui.storage

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.data.db.NoteStorageRow
import dev.aliarslan.musvedde.data.note.VersionRepository
import dev.aliarslan.musvedde.data.storage.StorageReport
import dev.aliarslan.musvedde.data.storage.StorageRepository
import dev.aliarslan.musvedde.domain.NoteState
import dev.aliarslan.musvedde.ui.notes.ConfirmDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storage: StorageRepository,
    private val versions: VersionRepository,
) : ViewModel() {
    private val _report = MutableStateFlow<StorageReport?>(null)
    val report = _report.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _report.value = storage.report() }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            versions.clearAll()
            _report.value = storage.report()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(onBack: () -> Unit, onOpenHistory: (Long) -> Unit, viewModel: StorageViewModel = hiltViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val current = report
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Summary(current, onClearHistory = { confirmClear = true }) }
            item {
                Text(
                    stringResource(R.string.storage_notes_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                )
            }
            items(current.notes, key = { it.id }) { row ->
                NoteRow(row, onClick = { onOpenHistory(row.id) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            text = stringResource(R.string.storage_clear_all_confirm),
            confirm = stringResource(R.string.storage_clear_all),
            onConfirm = viewModel::clearAllHistory,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun Summary(report: StorageReport, onClearHistory: () -> Unit) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatFileSize(context, bytes)
    ElevatedCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.storage_on_device), style = MaterialTheme.typography.labelLarge)
            Text(size(report.totalOnDiskBytes), style = MaterialTheme.typography.headlineMedium)
            HorizontalDivider()
            SummaryLine(stringResource(R.string.storage_database), size(report.databaseBytes))
            SummaryLine(stringResource(R.string.storage_safety_backups), size(report.safetyBackupBytes))
            HorizontalDivider()
            SummaryLine(
                pluralStringResource(R.plurals.storage_notes_count, report.noteCount, report.noteCount),
                size(report.noteBytes),
            )
            SummaryLine(
                pluralStringResource(R.plurals.storage_versions_count, report.versionCount, report.versionCount),
                size(report.versionBytes),
            )
            Text(
                stringResource(R.string.storage_text_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onClearHistory, enabled = report.versionCount > 0, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.storage_clear_all))
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NoteRow(row: NoteStorageRow, onClick: () -> Unit) {
    val context = LocalContext.current
    val untitled = stringResource(R.string.notification_untitled)
    val name = row.title.ifBlank { row.preview.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }.ifBlank { untitled }
    val stateLabel = when (row.state) {
        NoteState.ARCHIVED.name -> stringResource(R.string.nav_archive)
        NoteState.TRASHED.name -> stringResource(R.string.nav_trash)
        else -> null
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    R.string.storage_note_detail,
                    Formatter.formatShortFileSize(context, row.noteBytes),
                    pluralStringResource(R.plurals.storage_versions_count, row.versionCount, row.versionCount),
                    Formatter.formatShortFileSize(context, row.versionBytes),
                ) + (stateLabel?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            Formatter.formatShortFileSize(context, row.noteBytes + row.versionBytes),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
