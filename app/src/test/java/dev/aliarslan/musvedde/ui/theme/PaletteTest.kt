package dev.aliarslan.musvedde.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.domain.ContrastLevel
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
