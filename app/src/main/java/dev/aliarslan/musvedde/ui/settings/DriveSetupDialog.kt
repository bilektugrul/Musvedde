package dev.aliarslan.musvedde.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.aliarslan.musvedde.R
import kotlinx.coroutines.launch
import java.security.MessageDigest

private const val CLOUD_CONSOLE_URL = "https://console.cloud.google.com/apis/credentials"

fun sha1Fingerprint(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-1").digest(bytes).joinToString(":") { "%02X".format(it) }

fun signingSha1(context: Context): String? = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()?.let(::sha1Fingerprint)
}.getOrNull()

@Composable
fun DriveSetupDialog(onConnect: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sha1 = remember { signingSha1(context) ?: "—" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drive_setup_title)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.drive_setup_intro))
                listOf(
                    R.string.drive_setup_step1,
                    R.string.drive_setup_step2,
                    R.string.drive_setup_step3,
                    R.string.drive_setup_step4,
                ).forEachIndexed { index, step ->
                    Text("${index + 1}. ${stringResource(step)}", modifier = Modifier.padding(top = 12.dp))
                }
                CopyableValue(stringResource(R.string.drive_setup_package), context.packageName)
                CopyableValue(stringResource(R.string.drive_setup_sha1), sha1)
                Text(
                    stringResource(R.string.drive_setup_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                TextButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, CLOUD_CONSOLE_URL.toUri())) },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text(stringResource(R.string.drive_setup_open_console)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConnect()
            }) { Text(stringResource(R.string.drive_setup_connect)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
private fun CopyableValue(label: String, value: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
        IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value))) } }) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.drive_setup_copy, label))
        }
    }
}
