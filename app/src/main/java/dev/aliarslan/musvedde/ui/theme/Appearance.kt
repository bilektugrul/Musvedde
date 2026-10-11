package dev.aliarslan.musvedde.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class Appearance(
    val dark: Boolean = false,
    val amoled: Boolean = false,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
)

val LocalAppearance = staticCompositionLocalOf { Appearance() }
