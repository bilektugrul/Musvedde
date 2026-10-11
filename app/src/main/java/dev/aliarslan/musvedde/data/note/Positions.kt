package dev.aliarslan.musvedde.data.note

object Positions {
    const val STEP = 1024L

    fun between(above: Long?, below: Long?): Long? = when {
        above == null && below == null -> 0
        above == null -> below!! + STEP
        below == null -> above - STEP
        above - below < 2 -> null
        else -> below + (above - below) / 2
    }
}
