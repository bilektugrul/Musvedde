package dev.aliarslan.musvedde.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
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
import dev.aliarslan.musvedde.domain.MonetTuning
import dev.aliarslan.musvedde.domain.PaletteStyleOption
import dev.aliarslan.musvedde.domain.ThemePalette

/**
 * Builds the app's color scheme. The wallpaper palette with the default style, contrast and tuning uses the
 * system's own Monet colors when they exist; every other combination is generated here from a seed color.
 */
fun buildColorScheme(
    palette: ThemePalette,
    style: PaletteStyleOption,
    contrast: ContrastLevel,
    dark: Boolean,
    wallpaperSeed: Int?,
    systemDynamic: ColorScheme?,
    monet: MonetTuning = MonetTuning(),
): ColorScheme {
    val defaults = style == PaletteStyleOption.TONAL_SPOT && contrast == ContrastLevel.STANDARD && !monet.changesScheme
    if (palette == ThemePalette.WALLPAPER && defaults && systemDynamic != null) return systemDynamic
    val effective = if (palette == ThemePalette.WALLPAPER && wallpaperSeed == null) ThemePalette.PAPER else palette
    if (effective == ThemePalette.PAPER && defaults) return paperScheme(dark)
    val seed = when (effective) {
        ThemePalette.WALLPAPER -> wallpaperSeed!!
        ThemePalette.CUSTOM -> monet.accentColor
        else -> effective.seed!!.toInt()
    }
    return dynamicScheme(seed, style, contrast, dark).tuned(monet).toColorScheme().withLuminance(monet)
}

private fun DynamicScheme.tuned(monet: MonetTuning): DynamicScheme {
    if (!monet.changesScheme) return this
    val chromaScale = 1 + monet.chroma / 100.0
    fun TonalPalette.scaled() = if (monet.chroma == 0) this else TonalPalette.fromHueAndChroma(hue, chroma * chromaScale)
    val background = if (monet.customBackground) Hct.fromInt(monet.backgroundColor) else null
    fun TonalPalette.neutral(maxChroma: Double): TonalPalette {
        val source = background?.let { TonalPalette.fromHueAndChroma(it.hue, minOf(it.chroma, maxChroma)) } ?: this
        return when {
            !monet.tintedBackground -> TonalPalette.fromHueAndChroma(source.hue, 0.0)
            monet.wholePalette -> source.scaled()
            else -> source
        }
    }
    return DynamicScheme(
        sourceColorHct,
        variant,
        isDark,
        contrastLevel,
        primaryPalette.scaled(),
        secondaryPalette.scaled(),
        tertiaryPalette.scaled(),
        neutralPalette.neutral(NEUTRAL_MAX_CHROMA),
        neutralVariantPalette.neutral(NEUTRAL_VARIANT_MAX_CHROMA),
        platform,
        specVersion,
        errorPalette,
    )
}

private fun ColorScheme.withLuminance(monet: MonetTuning): ColorScheme {
    if (monet.luminance == 0) return this
    val shift = monet.luminance / 100.0 * MAX_TONE_SHIFT
    fun Color.shifted(): Color {
        val hct = Hct.fromInt(toArgb())
        return Color(hct.withTone((hct.tone + shift).coerceIn(0.0, 100.0)).toInt())
    }
    val accents = copy(
        primary = primary.shifted(),
        onPrimary = onPrimary.shifted(),
        primaryContainer = primaryContainer.shifted(),
        onPrimaryContainer = onPrimaryContainer.shifted(),
        inversePrimary = inversePrimary.shifted(),
        secondary = secondary.shifted(),
        onSecondary = onSecondary.shifted(),
        secondaryContainer = secondaryContainer.shifted(),
        onSecondaryContainer = onSecondaryContainer.shifted(),
        tertiary = tertiary.shifted(),
        onTertiary = onTertiary.shifted(),
        tertiaryContainer = tertiaryContainer.shifted(),
        onTertiaryContainer = onTertiaryContainer.shifted(),
        surfaceTint = surfaceTint.shifted(),
    )
    if (!monet.wholePalette) return accents
    return accents.copy(
        background = background.shifted(),
        onBackground = onBackground.shifted(),
        surface = surface.shifted(),
        onSurface = onSurface.shifted(),
        surfaceVariant = surfaceVariant.shifted(),
        onSurfaceVariant = onSurfaceVariant.shifted(),
        inverseSurface = inverseSurface.shifted(),
        inverseOnSurface = inverseOnSurface.shifted(),
        outline = outline.shifted(),
        outlineVariant = outlineVariant.shifted(),
        surfaceBright = surfaceBright.shifted(),
        surfaceDim = surfaceDim.shifted(),
        surfaceContainer = surfaceContainer.shifted(),
        surfaceContainerHigh = surfaceContainerHigh.shifted(),
        surfaceContainerHighest = surfaceContainerHighest.shifted(),
        surfaceContainerLow = surfaceContainerLow.shifted(),
        surfaceContainerLowest = surfaceContainerLowest.shifted(),
    )
}

private const val MAX_TONE_SHIFT = 20.0
private const val NEUTRAL_MAX_CHROMA = 16.0
private const val NEUTRAL_VARIANT_MAX_CHROMA = 24.0

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
