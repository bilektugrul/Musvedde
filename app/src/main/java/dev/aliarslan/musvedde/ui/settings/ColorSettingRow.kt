package dev.aliarslan.musvedde.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.domain.ThemePalette
import android.graphics.Color as AndroidColor

fun Int.toHex(): String = "#%06x".format(this and 0xFFFFFF)

/** Parses `#rrggbb`, `rrggbb`, `#aarrggbb` or `#rgb`; the result is always opaque. */
fun parseHexColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    val rgb = when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("")
        6 -> hex
        8 -> hex.substring(2)
        else -> return null
    }
    return rgb.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
}

@Composable
fun ColorSettingRow(title: String, color: Int, defaultColor: Int, onColorChange: (Int) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable { editing = true }
            .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(color.toHex(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onColorChange(defaultColor) }, enabled = color != defaultColor) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.settings_reset_value))
        }
        ColorDot(Color(color), Modifier.padding(start = 4.dp).size(40.dp))
    }
    if (editing) {
        ColorPickerDialog(
            title = title,
            initial = color,
            onConfirm = {
                onColorChange(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun ColorDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
}

@Composable
private fun ColorPickerDialog(title: String, initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val current = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    var hexText by remember { mutableStateOf(initial.toHex()) }
    var hexValid by remember { mutableStateOf(true) }

    fun setColor(argb: Int, updateText: Boolean = true) {
        val hsv = FloatArray(3).also { AndroidColor.colorToHSV(argb, it) }
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        if (updateText) hexText = argb.toHex()
        hexValid = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(current))
                        .semantics { contentDescription = current.toHex() },
                )
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text
                        val parsed = parseHexColor(text)
                        hexValid = parsed != null
                        if (parsed != null) setColor(parsed, updateText = false)
                    },
                    label = { Text(stringResource(R.string.color_hex)) },
                    isError = !hexValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                HsvSlider(stringResource(R.string.color_hue), hue, 0f..360f, "%.0f°".format(hue)) {
                    hue = it
                    hexText = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)).toHex()
                }
                HsvSlider(stringResource(R.string.color_saturation), saturation, 0f..1f, "%.0f%%".format(saturation * 100)) {
                    saturation = it
                    hexText = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)).toHex()
                }
                HsvSlider(stringResource(R.string.color_value), value, 0f..1f, "%.0f%%".format(value * 100)) {
                    value = it
                    hexText = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)).toHex()
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                ) {
                    ThemePalette.entries.mapNotNull { it.seed?.toInt() }.forEach { preset ->
                        ColorDot(Color(preset), Modifier.size(32.dp).clickable { setColor(preset) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun HsvSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
