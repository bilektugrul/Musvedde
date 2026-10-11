package dev.aliarslan.musvedde.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SwipeBackTest {
    @Test
    fun commitsPastOneThirdOfTheScreen() {
        assertThat(shouldCommitBack(progress = 0.4f, velocityDpPerSecond = 0f)).isTrue()
        assertThat(shouldCommitBack(progress = 0.2f, velocityDpPerSecond = 0f)).isFalse()
    }

    @Test
    fun fastFlingCommitsEvenWhenShort() {
        assertThat(shouldCommitBack(progress = 0.1f, velocityDpPerSecond = 900f)).isTrue()
    }

    @Test
    fun flingingBackLeftCancels() {
        assertThat(shouldCommitBack(progress = 0.6f, velocityDpPerSecond = -900f)).isFalse()
    }

    @Test
    fun startsOnlyOnMostlyHorizontalRightwardDrag() {
        assertThat(isBackSwipeStart(dx = 30f, dy = 5f, touchSlop = 16f)).isTrue()
        assertThat(isBackSwipeStart(dx = 30f, dy = 20f, touchSlop = 16f)).isFalse()
        assertThat(isBackSwipeStart(dx = -30f, dy = 0f, touchSlop = 16f)).isFalse()
        assertThat(isBackSwipeStart(dx = 10f, dy = 0f, touchSlop = 16f)).isFalse()
    }
}
