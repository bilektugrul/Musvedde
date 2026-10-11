package dev.aliarslan.musvedde.ui.history

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.ui.notes.ConfirmDialog
import dev.aliarslan.musvedde.ui.theme.LocalAppearance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = state.selected

    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it == HistoryEvent.Restored) onBack() }
    }
    BackHandler(enabled = selected != null) { viewModel.select(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selected == null) stringResource(R.string.history_title) else formatTime(selected.version.createdAt))
                },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) viewModel.select(null) else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (selected == null && state.items.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.history_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                selected != null -> VersionDetail(
                    item = selected,
                    onRestore = { viewModel.restore(selected.version.id) },
                    onDelete = { confirmDelete = selected.version.id },
                )
                state.loaded && state.items.isEmpty() -> Text(
                    stringResource(R.string.history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> VersionList(state, onOpen = { viewModel.select(it) }, onDelete = { confirmDelete = it })
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            text = stringResource(R.string.history_clear_confirm),
            confirm = stringResource(R.string.history_clear),
            onConfirm = viewModel::clear,
            onDismiss = { confirmClear = false },
        )
    }
    confirmDelete?.let { id ->
        ConfirmDialog(
            text = stringResource(R.string.history_delete_confirm),
            confirm = stringResource(R.string.history_delete),
            onConfirm = { viewModel.delete(id) },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun VersionList(state: HistoryUiState, onOpen: (Long) -> Unit, onDelete: (Long) -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.items, key = { it.version.id }) { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item.version.id) }
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(formatTime(item.version.createdAt), style = MaterialTheme.typography.titleSmall)
                    Text(
                        item.version.fullText().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val size = Formatter.formatShortFileSize(context, item.version.sizeBytes)
                    val change = item.stats?.let { stringResource(R.string.history_change, it.added, it.removed) }
                        ?: stringResource(R.string.history_first)
                    Text(
                        "$size · $change",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onDelete(item.version.id) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.history_delete))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun VersionDetail(item: VersionItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    var showChanges by rememberSaveable(item.version.id) { mutableStateOf(item.previous != null) }
    val lines = remember(item, showChanges) {
        if (showChanges && item.previous != null) {
            lineDiff(item.previous.fullText(), item.version.fullText())
        } else {
            item.version.fullText().lines().map { DiffLine(DiffType.SAME, it) }
        }
    }
    val dark = LocalAppearance.current.dark
    val addedColor = if (dark) Color(0xFF1E4D2B) else Color(0xFFD7F5DD)
    val removedColor = if (dark) Color(0xFF5C1F24) else Color(0xFFFADADD)

    Column(Modifier.fillMaxSize()) {
        if (item.previous != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.history_show_changes), modifier = Modifier.weight(1f))
                Switch(checked = showChanges, onCheckedChange = { showChanges = it })
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items(lines) { line ->
                val background = when (line.type) {
                    DiffType.ADDED -> addedColor
                    DiffType.REMOVED -> removedColor
                    DiffType.SAME -> Color.Transparent
                }
                Text(
                    line.text.ifEmpty { " " },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (line.type == DiffType.REMOVED) TextDecoration.LineThrough else null,
                    ),
                    modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 4.dp),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.history_delete))
            }
            Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.history_restore))
            }
        }
    }
}

@Composable
private fun formatTime(millis: Long): String {
    val context = LocalContext.current
    return remember(millis) {
        DateUtils.formatDateTime(
            context,
            millis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR,
        )
    }
}
