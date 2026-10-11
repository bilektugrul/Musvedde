package dev.aliarslan.musvedde.ui.settings

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.data.backup.BackupTarget
import dev.aliarslan.musvedde.data.backup.RestoreMode

private sealed interface RestoreSource {
    data class Candidate(val candidate: RestoreCandidate) : RestoreSource
    data class File(val uri: Uri) : RestoreSource
}

@Composable
fun RestoreDialog(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val candidates by viewModel.restoreCandidates.collectAsStateWithLifecycle()
    var source by remember { mutableStateOf<RestoreSource?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) source = RestoreSource.File(uri)
    }
    LaunchedEffect(Unit) { viewModel.loadRestoreCandidates() }

    fun run(mode: RestoreMode) {
        when (val s = source) {
            is RestoreSource.Candidate -> viewModel.restore(s.candidate, mode)
            is RestoreSource.File -> viewModel.restore(s.uri, mode)
            null -> Unit
        }
        onDismiss()
    }

    when {
        confirmReplace -> AlertDialog(
            onDismissRequest = { confirmReplace = false },
            text = { Text(stringResource(R.string.restore_replace_confirm)) },
            confirmButton = { TextButton(onClick = { run(RestoreMode.REPLACE) }) { Text(stringResource(R.string.restore_replace)) } },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
        source != null -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.restore_mode_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.restore_merge_summary))
                    Text(stringResource(R.string.restore_replace_summary), modifier = Modifier.padding(top = 12.dp))
                }
            },
            confirmButton = { TextButton(onClick = { run(RestoreMode.MERGE) }) { Text(stringResource(R.string.restore_merge)) } },
            dismissButton = { TextButton(onClick = { confirmReplace = true }) { Text(stringResource(R.string.restore_replace)) } },
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.backup_restore)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    val list = candidates
                    when {
                        list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        list.isEmpty() -> Text(stringResource(R.string.restore_none_found))
                        else -> list.forEach { candidate ->
                            ActionRow(
                                candidate.file.name.removePrefix("musvedde-backup-").removeSuffix(".zip"),
                                stringResource(
                                    if (candidate.targetId == BackupTarget.DRIVE) R.string.backup_drive else R.string.backup_folder,
                                ) + " · " + Formatter.formatShortFileSize(context, candidate.file.sizeBytes),
                            ) { source = RestoreSource.Candidate(candidate) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { filePicker.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                    Text(stringResource(R.string.restore_choose_file))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
