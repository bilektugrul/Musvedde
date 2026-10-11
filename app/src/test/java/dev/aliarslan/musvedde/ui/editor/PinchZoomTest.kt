package dev.aliarslan.musvedde.ui.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinchZoomTest {
    private val range = 14f..32f

    @Test
    fun scaleAccumulatesWithinRange() {
        assertThat(nextZoomScale(current = 1f, zoom = 1.2f, baseSize = 18f, range = range)).isWithin(0.0001f).of(1.2f)
        assertThat(nextZoomScale(current = 1.2f, zoom = 1.1f, baseSize = 18f, range = range)).isWithin(0.0001f).of(1.32f)
    }

    @Test
    fun scaleIsClampedSoTheFontStaysInRange() {
        assertThat(nextZoomScale(current = 1f, zoom = 3f, baseSize = 18f, range = range)).isWithin(0.0001f).of(32f / 18f)
        assertThat(nextZoomScale(current = 1f, zoom = 0.2f, baseSize = 18f, range = range)).isWithin(0.0001f).of(14f / 18f)
    }

    @Test
    fun committedSizeIsRoundedToHalfPoints() {
        assertThat(zoomedFontSize(baseSize = 18f, scale = 1.31f, range = range)).isEqualTo(23.5f)
        assertThat(zoomedFontSize(baseSize = 18f, scale = 5f, range = range)).isEqualTo(32f)
    }
}
