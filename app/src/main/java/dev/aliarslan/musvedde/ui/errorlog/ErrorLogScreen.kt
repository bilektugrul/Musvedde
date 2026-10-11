package dev.aliarslan.musvedde.ui.errorlog

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.data.errorlog.DayKey
import dev.aliarslan.musvedde.domain.ErrorEntry
import dev.aliarslan.musvedde.domain.ErrorLevel
import dev.aliarslan.musvedde.ui.notes.ConfirmDialog
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ErrorLogScreen(onBack: () -> Unit, viewModel: ErrorLogViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.error_log_share)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_error_log)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = shareTitle)
                    }
                    IconButton(onClick = { confirmClear = true }, enabled = state.groups.isNotEmpty()) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.error_log_clear))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Filters(state, viewModel::toggleLevel, viewModel::setSource)
            if (state.loaded && state.groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_log_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    state.groups.forEach { group ->
                        stickyHeader(key = group.key.toString(), contentType = "header") { DayHeader(group.key) }
                        items(group.entries, key = { it.id }, contentType = { "entry" }) { entry ->
                            EntryRow(
                                entry = entry,
                                expanded = entry.id in state.expanded,
                                onToggle = { viewModel.toggleExpanded(entry.id) },
                                onCopy = {
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Müsvedde", viewModel.entryText(entry))))
                                    }
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            text = stringResource(R.string.error_log_clear_confirm),
            confirm = stringResource(R.string.error_log_clear),
            onConfirm = viewModel::clear,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun Filters(state: ErrorLogUiState, onToggleLevel: (ErrorLevel) -> Unit, onSource: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(ErrorLevel.entries) { level ->
            FilterChip(
                selected = level in state.levels,
                onClick = { onToggleLevel(level) },
                label = { Text(stringResource(level.labelRes())) },
                leadingIcon = { Icon(level.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        items(state.sources) { source ->
            FilterChip(
                selected = state.source == source,
                onClick = { onSource(if (state.source == source) null else source) },
                label = { Text(source) },
            )
        }
    }
}

@Composable
private fun DayHeader(key: DayKey) {
    val text = when (key) {
        DayKey.Today -> stringResource(R.string.day_today)
        DayKey.Yesterday -> stringResource(R.string.day_yesterday)
        is DayKey.Date -> remember(key) { key.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) }
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EntryRow(entry: ErrorEntry, expanded: Boolean, onToggle: () -> Unit, onCopy: () -> Unit) {
    val time = remember(entry.timestamp) { DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(entry.timestamp)) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(entry.level.icon(), contentDescription = stringResource(entry.level.labelRes()), tint = entry.level.color(), modifier = Modifier.size(20.dp))
            Text(time, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
            Text(
                entry.source,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            if (expanded) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.error_log_copy), modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(
            entry.message,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (expanded && entry.stackTrace != null) {
            Text(
                entry.stackTrace,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = false,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
            )
        }
    }
}

private fun ErrorLevel.labelRes() = when (this) {
    ErrorLevel.ERROR -> R.string.level_error
    ErrorLevel.WARNING -> R.string.level_warning
    ErrorLevel.INFO -> R.string.level_info
}

private fun ErrorLevel.icon(): ImageVector = when (this) {
    ErrorLevel.ERROR -> Icons.Outlined.ErrorOutline
    ErrorLevel.WARNING -> Icons.Outlined.WarningAmber
    ErrorLevel.INFO -> Icons.Outlined.Info
}

@Composable
private fun ErrorLevel.color(): Color = when (this) {
    ErrorLevel.ERROR -> MaterialTheme.colorScheme.error
    ErrorLevel.WARNING -> Color(0xFFE0A100)
    ErrorLevel.INFO -> MaterialTheme.colorScheme.primary
}
