package dev.aliarslan.musvedde.ui.notes

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.data.backup.AutoRestore
import dev.aliarslan.musvedde.data.backup.AutoRestoreState
import javax.inject.Inject

@HiltViewModel
class AutoRestoreViewModel @Inject constructor(private val autoRestore: AutoRestore) : ViewModel() {
    val state = autoRestore.state

    fun dismiss() = autoRestore.dismiss()
}

@Composable
fun AutoRestoreBanner(state: AutoRestoreState?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val lastShown = remember { arrayOfNulls<AutoRestoreState>(1) }
    if (state != null) lastShown[0] = state
    val shown = state ?: lastShown[0]
    AnimatedVisibility(
        visible = state != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        shown?.let { BannerContent(it, onDismiss) }
    }
}

@Composable
private fun BannerContent(state: AutoRestoreState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val failed = state is AutoRestoreState.Failed
    Surface(
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("auto_restore_banner"),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (state) {
                        is AutoRestoreState.Done -> Icons.Outlined.CloudDone
                        AutoRestoreState.Failed -> Icons.Outlined.CloudOff
                        else -> Icons.Outlined.CloudDownload
                    },
                    contentDescription = null,
                )
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    val title = when (state) {
                        is AutoRestoreState.Done ->
                            resources.getQuantityString(R.plurals.auto_restore_done, state.noteCount, state.noteCount)
                        AutoRestoreState.Failed -> stringResource(R.string.auto_restore_failed)
                        else -> stringResource(R.string.auto_restore_running)
                    }
                    val detail = when (state) {
                        is AutoRestoreState.Downloading -> if (state.totalBytes > 0) {
                            stringResource(
                                R.string.auto_restore_progress,
                                Formatter.formatShortFileSize(context, state.bytesRead),
                                Formatter.formatShortFileSize(context, state.totalBytes),
                            )
                        } else {
                            Formatter.formatShortFileSize(context, state.bytesRead)
                        }
                        AutoRestoreState.Restoring -> stringResource(R.string.auto_restore_adding)
                        is AutoRestoreState.Done -> if (state.backupTime > 0) {
                            val date = DateUtils.formatDateTime(
                                context,
                                state.backupTime,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                            )
                            stringResource(R.string.auto_restore_from, date)
                        } else {
                            null
                        }
                        AutoRestoreState.Failed -> stringResource(R.string.auto_restore_failed_hint)
                    }
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (detail != null) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (state is AutoRestoreState.Done || failed) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_dismiss))
                    }
                }
            }
            when (state) {
                is AutoRestoreState.Downloading -> if (state.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (state.bytesRead.toFloat() / state.totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, end = 12.dp),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp, end = 12.dp))
                }
                AutoRestoreState.Restoring -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp, end = 12.dp))
                else -> Unit
            }
        }
    }
}
