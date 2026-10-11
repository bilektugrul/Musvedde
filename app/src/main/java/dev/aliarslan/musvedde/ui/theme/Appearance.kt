package dev.aliarslan.musvedde.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.aliarslan.musvedde.domain.AnimationSpeed
import kotlin.math.roundToInt

@Immutable
data class Appearance(
    val dark: Boolean = false,
    val amoled: Boolean = false,
    val highContrast: Boolean = false,
    val animationScale: Float = AnimationSpeed.NORMAL.factor,
) {
    val reduceMotion: Boolean get() = animationScale == 0f

    fun motionMillis(baseMillis: Int): Int = (baseMillis * animationScale).roundToInt()
}

fun appearanceScale(speed: AnimationSpeed, reduceMotion: Boolean): Float = if (reduceMotion) 0f else speed.factor

val LocalAppearance = staticCompositionLocalOf { Appearance() }
