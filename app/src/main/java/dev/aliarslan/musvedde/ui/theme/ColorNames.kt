package dev.aliarslan.musvedde.ui.theme

import androidx.annotation.StringRes
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.domain.NoteColor

@StringRes
fun NoteColor.colorNameRes(): Int = when (this) {
    NoteColor.DEFAULT -> R.string.color_default
    NoteColor.RED -> R.string.color_red
    NoteColor.ORANGE -> R.string.color_orange
    NoteColor.YELLOW -> R.string.color_yellow
    NoteColor.GREEN -> R.string.color_green
    NoteColor.TEAL -> R.string.color_teal
    NoteColor.BLUE -> R.string.color_blue
    NoteColor.DARK_BLUE -> R.string.color_dark_blue
    NoteColor.PURPLE -> R.string.color_purple
    NoteColor.PINK -> R.string.color_pink
    NoteColor.BROWN -> R.string.color_brown
    NoteColor.GRAY -> R.string.color_gray
}
