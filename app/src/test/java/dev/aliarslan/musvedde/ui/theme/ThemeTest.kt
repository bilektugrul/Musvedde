package dev.aliarslan.musvedde.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.ThemeMode
import org.junit.Test

class ThemeTest {
    @Test
    fun amoledBackgroundIsPureBlack() {
        val scheme = colorSchemeFor(ThemeMode.AMOLED, systemDark = false)

        assertThat(scheme.background).isEqualTo(Color.Black)
        assertThat(scheme.surface).isEqualTo(Color.Black)
    }

    @Test
    fun systemModeFollowsSystemDarkness() {
        assertThat(isDarkTheme(ThemeMode.SYSTEM, systemDark = true)).isTrue()
        assertThat(isDarkTheme(ThemeMode.SYSTEM, systemDark = false)).isFalse()
        assertThat(isDarkTheme(ThemeMode.LIGHT, systemDark = true)).isFalse()
        assertThat(isDarkTheme(ThemeMode.AMOLED, systemDark = false)).isTrue()
    }

    @Test
    fun defaultNoteColorIsUnspecified() {
        assertThat(noteContainerColor(NoteColor.DEFAULT, dark = false, amoled = false)).isEqualTo(Color.Unspecified)
    }

    @Test
    fun amoledNoteColorsAreDarkerThanDark() {
        val dark = noteContainerColor(NoteColor.TEAL, dark = true, amoled = false)
        val amoled = noteContainerColor(NoteColor.TEAL, dark = true, amoled = true)

        assertThat(amoled.red + amoled.green + amoled.blue).isLessThan(dark.red + dark.green + dark.blue)
    }
}
