package dev.aliarslan.musvedde.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import dev.aliarslan.musvedde.domain.ContrastLevel
import dev.aliarslan.musvedde.domain.PaletteStyleOption
import dev.aliarslan.musvedde.domain.ThemePalette

/**
 * Builds the app's color scheme. The wallpaper palette with the default style and contrast uses the system's
 * own Monet colors when they exist; every other combination is generated here from a seed color.
 */
fun buildColorScheme(
    palette: ThemePalette,
    style: PaletteStyleOption,
    contrast: ContrastLevel,
    dark: Boolean,
    wallpaperSeed: Int?,
    systemDynamic: ColorScheme?,
): ColorScheme {
    val defaults = style == PaletteStyleOption.TONAL_SPOT && contrast == ContrastLevel.STANDARD
    if (palette == ThemePalette.WALLPAPER && defaults && systemDynamic != null) return systemDynamic
    val effective = if (palette == ThemePalette.WALLPAPER && wallpaperSeed == null) ThemePalette.PAPER else palette
    if (effective == ThemePalette.PAPER && defaults) return paperScheme(dark)
    val seed = if (effective == ThemePalette.WALLPAPER) wallpaperSeed!! else effective.seed!!.toInt()
    return dynamicScheme(seed, style, contrast, dark).toColorScheme()
}

private fun dynamicScheme(seed: Int, style: PaletteStyleOption, contrast: ContrastLevel, dark: Boolean): DynamicScheme {
    val hct = Hct.fromInt(seed)
    val level = contrast.value
    return when (style) {
        PaletteStyleOption.TONAL_SPOT -> SchemeTonalSpot(hct, dark, level)
        PaletteStyleOption.VIBRANT -> SchemeVibrant(hct, dark, level)
        PaletteStyleOption.EXPRESSIVE -> SchemeExpressive(hct, dark, level)
        PaletteStyleOption.RAINBOW -> SchemeRainbow(hct, dark, level)
        PaletteStyleOption.FRUIT_SALAD -> SchemeFruitSalad(hct, dark, level)
        PaletteStyleOption.NEUTRAL -> SchemeNeutral(hct, dark, level)
        PaletteStyleOption.MONOCHROME -> SchemeMonochrome(hct, dark, level)
        PaletteStyleOption.FIDELITY -> SchemeFidelity(hct, dark, level)
        PaletteStyleOption.CONTENT -> SchemeContent(hct, dark, level)
    }
}

private fun DynamicScheme.toColorScheme(): ColorScheme {
    val base = paperScheme(isDark)
    return base.copy(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(surfaceTint),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        scrim = Color(scrim),
        surfaceBright = Color(surfaceBright),
        surfaceDim = Color(surfaceDim),
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
        surfaceContainerLow = Color(surfaceContainerLow),
        surfaceContainerLowest = Color(surfaceContainerLowest),
    )
}
