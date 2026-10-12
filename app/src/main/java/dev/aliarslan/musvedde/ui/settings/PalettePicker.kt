package dev.aliarslan.musvedde.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.domain.ContrastLevel
import dev.aliarslan.musvedde.domain.PaletteStyleOption
import dev.aliarslan.musvedde.domain.ThemePalette

@Composable
fun PalettePicker(selected: ThemePalette, onSelect: (ThemePalette) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            stringResource(R.string.settings_palette),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            paletteLabel(selected),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            ThemePalette.entries.forEach { palette ->
                Swatch(palette, palette == selected, paletteLabel(palette)) { onSelect(palette) }
            }
        }
    }
}

@Composable
private fun Swatch(palette: ThemePalette, selected: Boolean, label: String, onClick: () -> Unit) {
    val fill = palette.seed?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val content = if (palette.seed == null) MaterialTheme.colorScheme.onSecondaryContainer else Color.White
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(fill)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        when {
            selected -> Icon(Icons.Outlined.Check, contentDescription = null, tint = content)
            palette.seed == null -> Icon(Icons.Outlined.Wallpaper, contentDescription = null, tint = content)
        }
    }
}

@Composable
fun paletteLabel(palette: ThemePalette): String = stringResource(
    when (palette) {
        ThemePalette.WALLPAPER -> R.string.palette_wallpaper
        ThemePalette.CUSTOM -> R.string.palette_custom
        ThemePalette.PAPER -> R.string.palette_paper
        ThemePalette.OCEAN -> R.string.palette_ocean
        ThemePalette.FOREST -> R.string.palette_forest
        ThemePalette.LAVENDER -> R.string.palette_lavender
        ThemePalette.ROSE -> R.string.palette_rose
        ThemePalette.SUNSET -> R.string.palette_sunset
        ThemePalette.SLATE -> R.string.palette_slate
    },
)

@Composable
fun paletteStyleLabel(style: PaletteStyleOption): String = stringResource(
    when (style) {
        PaletteStyleOption.TONAL_SPOT -> R.string.palette_style_tonal_spot
        PaletteStyleOption.VIBRANT -> R.string.palette_style_vibrant
        PaletteStyleOption.EXPRESSIVE -> R.string.palette_style_expressive
        PaletteStyleOption.RAINBOW -> R.string.palette_style_rainbow
        PaletteStyleOption.FRUIT_SALAD -> R.string.palette_style_fruit_salad
        PaletteStyleOption.NEUTRAL -> R.string.palette_style_neutral
        PaletteStyleOption.MONOCHROME -> R.string.palette_style_monochrome
        PaletteStyleOption.FIDELITY -> R.string.palette_style_fidelity
        PaletteStyleOption.CONTENT -> R.string.palette_style_content
    },
)

@Composable
fun contrastLabel(contrast: ContrastLevel): String = stringResource(
    when (contrast) {
        ContrastLevel.STANDARD -> R.string.contrast_standard
        ContrastLevel.MEDIUM -> R.string.contrast_medium
        ContrastLevel.HIGH -> R.string.contrast_high
    },
)
