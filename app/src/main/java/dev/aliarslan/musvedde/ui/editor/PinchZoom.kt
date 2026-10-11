package dev.aliarslan.musvedde.ui.editor

import kotlin.math.roundToInt

fun nextZoomScale(current: Float, zoom: Float, baseSize: Float, range: ClosedFloatingPointRange<Float>): Float =
    (current * zoom).coerceIn(range.start / baseSize, range.endInclusive / baseSize)

fun zoomedFontSize(baseSize: Float, scale: Float, range: ClosedFloatingPointRange<Float>): Float =
    ((baseSize * scale * 2).roundToInt() / 2f).coerceIn(range)
