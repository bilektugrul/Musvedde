package dev.aliarslan.musvedde.data.note

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PositionsTest {
    @Test
    fun midpointBetweenNeighbors() {
        assertThat(Positions.between(above = 2048, below = 1024)).isEqualTo(1536)
        assertThat(Positions.between(above = null, below = 1024)).isEqualTo(2048)
        assertThat(Positions.between(above = 1024, below = null)).isEqualTo(0)
        assertThat(Positions.between(above = null, below = null)).isEqualTo(0)
    }

    @Test
    fun nullWhenNoGap() {
        assertThat(Positions.between(above = 11, below = 10)).isNull()
        assertThat(Positions.between(above = 10, below = 10)).isNull()
    }
}
