package dev.aliarslan.musvedde.ui.theme

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.domain.AnimationSpeed
import org.junit.Test

class AppearanceTest {
    @Test
    fun defaultSpeedIsSlowerThanFast() {
        assertThat(AnimationSpeed.FAST.factor).isEqualTo(1f)
        assertThat(AnimationSpeed.NORMAL.factor).isGreaterThan(1f)
        assertThat(AnimationSpeed.SLOW.factor).isGreaterThan(AnimationSpeed.NORMAL.factor)
    }

    @Test
    fun motionMillisScalesWithSpeed() {
        assertThat(Appearance(animationScale = 1.5f).motionMillis(300)).isEqualTo(450)
        assertThat(Appearance(animationScale = 1f).motionMillis(300)).isEqualTo(300)
    }

    @Test
    fun reduceMotionDisablesAnimations() {
        assertThat(appearanceScale(AnimationSpeed.SLOW, reduceMotion = true)).isEqualTo(0f)
        assertThat(Appearance(animationScale = 0f).motionMillis(300)).isEqualTo(0)
        assertThat(appearanceScale(AnimationSpeed.NORMAL, reduceMotion = false)).isEqualTo(AnimationSpeed.NORMAL.factor)
    }
}
