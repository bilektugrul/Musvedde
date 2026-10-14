package dev.aliarslan.musvedde.web

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.Settings
import dev.aliarslan.musvedde.domain.ThemeMode
import dev.aliarslan.musvedde.ui.theme.appColorScheme
import dev.aliarslan.musvedde.ui.theme.isDarkTheme
import dev.aliarslan.musvedde.ui.theme.noteContainerColor

/** The phone's current theme as hex colors for the web editor. */
fun webTheme(context: Context, settings: Settings): WebTheme {
    val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val dark = isDarkTheme(settings.themeMode, systemDark)
    val scheme = appColorScheme(context, settings, systemDark)
    return WebTheme(
        dark = dark,
        colors = scheme.hexColors(),
        noteColors = NoteColor.entries
            .filter { it != NoteColor.DEFAULT }
            .associate { it.name to noteContainerColor(it, dark, settings.themeMode == ThemeMode.AMOLED).hex() },
    )
}

private fun ColorScheme.hexColors(): Map<String, String> = mapOf(
    "primary" to primary,
    "onPrimary" to onPrimary,
    "primaryContainer" to primaryContainer,
    "onPrimaryContainer" to onPrimaryContainer,
    "secondary" to secondary,
    "onSecondary" to onSecondary,
    "secondaryContainer" to secondaryContainer,
    "onSecondaryContainer" to onSecondaryContainer,
    "tertiary" to tertiary,
    "onTertiary" to onTertiary,
    "tertiaryContainer" to tertiaryContainer,
    "onTertiaryContainer" to onTertiaryContainer,
    "background" to background,
    "onBackground" to onBackground,
    "surface" to surface,
    "onSurface" to onSurface,
    "surfaceVariant" to surfaceVariant,
    "onSurfaceVariant" to onSurfaceVariant,
    "surfaceContainerLowest" to surfaceContainerLowest,
    "surfaceContainerLow" to surfaceContainerLow,
    "surfaceContainer" to surfaceContainer,
    "surfaceContainerHigh" to surfaceContainerHigh,
    "surfaceContainerHighest" to surfaceContainerHighest,
    "inverseSurface" to inverseSurface,
    "inverseOnSurface" to inverseOnSurface,
    "inversePrimary" to inversePrimary,
    "outline" to outline,
    "outlineVariant" to outlineVariant,
    "error" to error,
    "onError" to onError,
    "errorContainer" to errorContainer,
    "onErrorContainer" to onErrorContainer,
    "scrim" to scrim,
).mapValues { it.value.hex() }

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
