package dev.aliarslan.musvedde.ui.settings

import android.content.ClipData
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.data.db.WebDeviceEntity
import dev.aliarslan.musvedde.web.WebEditorController
import dev.aliarslan.musvedde.web.WebEditorStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebEditorViewModel @Inject constructor(private val controller: WebEditorController) : ViewModel() {
    val status: StateFlow<WebEditorStatus> = controller.status
    val pairingCode: StateFlow<String> = controller.auth.pairingCode
    val devices: StateFlow<List<WebDeviceEntity>> =
        controller.auth.devices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun start() = controller.start()

    fun stop() = controller.stop()

    fun remove(id: Long) {
        viewModelScope.launch { controller.auth.remove(id) }
    }

    fun removeAll() {
        viewModelScope.launch { controller.auth.removeAll() }
    }
}

@Composable
fun WebEditorSection(viewModel: WebEditorViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val code by viewModel.pairingCode.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var confirmRemoveAll by rememberSaveable { mutableStateOf(false) }

    Text(
        stringResource(R.string.web_editor_explanation),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        when (val s = status) {
            WebEditorStatus.Stopped -> Button(onClick = viewModel::start) { Text(stringResource(R.string.web_editor_start)) }
            WebEditorStatus.Failed -> {
                StatusMessage(stringResource(R.string.web_editor_failed))
                Button(onClick = viewModel::start, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.web_editor_start)) }
            }
            WebEditorStatus.Starting -> Text(stringResource(R.string.web_editor_starting), style = MaterialTheme.typography.bodyLarge)
            WebEditorStatus.NoWifi -> StatusMessage(stringResource(R.string.web_editor_no_wifi))
            WebEditorStatus.NoFreePort -> StatusMessage(stringResource(R.string.web_editor_no_port))
            is WebEditorStatus.Running -> {
                Text(stringResource(R.string.web_editor_address), style = MaterialTheme.typography.labelLarge)
                Text(
                    s.address,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            scope.launch { clipboard.setClipEntry(ClipData.newPlainText(s.address, s.address).toClipEntry()) }
                        }
                        .padding(vertical = 4.dp),
                )
                Text(
                    stringResource(R.string.web_editor_pairing_code),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    code.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.semantics { contentDescription = code.toList().joinToString(" ") },
                )
                Text(
                    stringResource(R.string.web_editor_pairing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (status != WebEditorStatus.Stopped && status != WebEditorStatus.Failed) {
            FilledTonalButton(onClick = viewModel::stop, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.web_editor_stop))
            }
        }
    }

    SectionHeader(stringResource(R.string.web_editor_devices))
    if (devices.isEmpty()) {
        Text(
            stringResource(R.string.web_editor_no_devices),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
    devices.forEach { device -> DeviceRow(device, onRemove = { viewModel.remove(device.id) }) }
    if (devices.size > 1) {
        ActionRow(stringResource(R.string.web_editor_remove_all)) { confirmRemoveAll = true }
    }
    Text(
        stringResource(R.string.web_editor_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )

    if (confirmRemoveAll) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false },
            text = { Text(stringResource(R.string.web_editor_remove_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveAll = false
                    viewModel.removeAll()
                }) { Text(stringResource(R.string.web_editor_remove_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveAll = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun StatusMessage(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun DeviceRow(device: WebDeviceEntity, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 24.dp, end = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.web_editor_last_seen,
                    DateUtils.getRelativeTimeSpanString(device.lastSeenAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.web_editor_remove, device.name))
        }
    }
}
