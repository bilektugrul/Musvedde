package dev.aliarslan.musvedde.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.materialkolor.hct.Hct
import dev.aliarslan.musvedde.domain.ContrastLevel
import dev.aliarslan.musvedde.domain.MonetTuning
import dev.aliarslan.musvedde.domain.PaletteStyleOption
import dev.aliarslan.musvedde.domain.ThemePalette
import org.junit.Test
import kotlin.math.abs

class PaletteTest {
    private fun scheme(
        palette: ThemePalette,
        style: PaletteStyleOption = PaletteStyleOption.TONAL_SPOT,
        contrast: ContrastLevel = ContrastLevel.STANDARD,
        dark: Boolean = false,
        wallpaperSeed: Int? = null,
    ) = buildColorScheme(palette, style, contrast, dark, wallpaperSeed, systemDynamic = null)

    private fun Color.isGray() = abs(red - green) < 0.03f && abs(green - blue) < 0.03f

    @Test
    fun paperWithDefaultsKeepsTheHandTunedColors() {
        assertThat(scheme(ThemePalette.PAPER).background).isEqualTo(Color(0xFFFFFBF6))
        assertThat(scheme(ThemePalette.PAPER, dark = true).background).isEqualTo(Color(0xFF17130E))
    }

    @Test
    fun oceanIsBlue() {
        val primary = scheme(ThemePalette.OCEAN).primary
        assertThat(primary.blue).isGreaterThan(primary.red)
    }

    @Test
    fun monochromeHasNoHue() {
        assertThat(scheme(ThemePalette.ROSE, style = PaletteStyleOption.MONOCHROME).primary.isGray()).isTrue()
    }

    @Test
    fun styleAndContrastChangeTheScheme() {
        val base = scheme(ThemePalette.FOREST)
        assertThat(scheme(ThemePalette.FOREST, style = PaletteStyleOption.VIBRANT).primary).isNotEqualTo(base.primary)
        assertThat(scheme(ThemePalette.FOREST, contrast = ContrastLevel.HIGH).primary).isNotEqualTo(base.primary)
    }

    @Test
    fun wallpaperUsesTheWallpaperColorAndFallsBackToPaper() {
        val fromRed = scheme(ThemePalette.WALLPAPER, wallpaperSeed = 0xFFD32F2F.toInt()).primary
        assertThat(fromRed.red).isGreaterThan(fromRed.blue)
        assertThat(scheme(ThemePalette.WALLPAPER, wallpaperSeed = null).background).isEqualTo(Color(0xFFFFFBF6))
    }

    private fun Color.hct() = Hct.fromInt(toArgb())

    private fun tuned(palette: ThemePalette, monet: MonetTuning, dark: Boolean = false) =
        buildColorScheme(palette, PaletteStyleOption.TONAL_SPOT, ContrastLevel.STANDARD, dark, null, null, monet)

    @Test
    fun customPaletteUsesTheAccentHex() {
        val primary = tuned(ThemePalette.CUSTOM, MonetTuning(accentColor = 0xFFD32F2F.toInt())).primary
        assertThat(primary.red).isGreaterThan(primary.blue)
        assertThat(primary.red).isGreaterThan(primary.green)
    }

    @Test
    fun chromaFactorScalesColorfulness() {
        val base = tuned(ThemePalette.OCEAN, MonetTuning()).primary.hct().chroma
        assertThat(tuned(ThemePalette.OCEAN, MonetTuning(chroma = -100)).primary.isGray()).isTrue()
        assertThat(tuned(ThemePalette.OCEAN, MonetTuning(chroma = 100)).primary.hct().chroma).isGreaterThan(base)
    }

    @Test
    fun luminanceFactorChangesTone() {
        val base = tuned(ThemePalette.OCEAN, MonetTuning()).primary.hct().tone
        assertThat(tuned(ThemePalette.OCEAN, MonetTuning(luminance = 100)).primary.hct().tone).isGreaterThan(base + 5)
        assertThat(tuned(ThemePalette.OCEAN, MonetTuning(luminance = -100)).primary.hct().tone).isLessThan(base - 5)
    }

    @Test
    fun luminanceAndChromaReachTheBackgroundOnlyForTheWholePalette() {
        val base = tuned(ThemePalette.ROSE, MonetTuning(), dark = true).surfaceContainerHighest
        assertThat(tuned(ThemePalette.ROSE, MonetTuning(luminance = 100), dark = true).surfaceContainerHighest).isEqualTo(base)
        val whole = tuned(ThemePalette.ROSE, MonetTuning(luminance = 100, wholePalette = true), dark = true).surfaceContainerHighest
        assertThat(whole.hct().tone).isGreaterThan(base.hct().tone + 5)
    }

    @Test
    fun customBackgroundTintsTheSurfaces() {
        val yellow = MonetTuning(customBackground = true, backgroundColor = 0xFFFFC90F.toInt())
        val surface = tuned(ThemePalette.OCEAN, yellow, dark = true).surfaceContainerHighest
        assertThat(surface.red).isGreaterThan(surface.blue)
        assertThat(tuned(ThemePalette.OCEAN, yellow).primary).isEqualTo(tuned(ThemePalette.OCEAN, MonetTuning()).primary)
    }

    @Test
    fun untintedBackgroundIsGray() {
        val surface = tuned(ThemePalette.ROSE, MonetTuning(tintedBackground = false), dark = true).surfaceContainerHighest
        assertThat(surface.isGray()).isTrue()
        assertThat(tuned(ThemePalette.ROSE, MonetTuning(), dark = true).surfaceContainerHighest.hct().chroma).isGreaterThan(2.0)
    }

    @Test
    fun tuningTheWallpaperPaletteSkipsTheSystemScheme() {
        val system = androidx.compose.material3.lightColorScheme(primary = Color(0xFF123456))
        val result = buildColorScheme(
            ThemePalette.WALLPAPER,
            PaletteStyleOption.TONAL_SPOT,
            ContrastLevel.STANDARD,
            dark = false,
            wallpaperSeed = 0xFFD32F2F.toInt(),
            systemDynamic = system,
            monet = MonetTuning(chroma = 50),
        )
        assertThat(result.primary).isNotEqualTo(Color(0xFF123456))
        assertThat(result.primary.red).isGreaterThan(result.primary.blue)
    }

    @Test
    fun systemMonetIsUsedForTheDefaultWallpaperStyle() {
        val system = androidx.compose.material3.lightColorScheme(primary = Color(0xFF123456))
        val result = buildColorScheme(
            ThemePalette.WALLPAPER,
            PaletteStyleOption.TONAL_SPOT,
            ContrastLevel.STANDARD,
            dark = false,
            wallpaperSeed = 0xFFD32F2F.toInt(),
            systemDynamic = system,
        )
        assertThat(result.primary).isEqualTo(Color(0xFF123456))
    }
}
