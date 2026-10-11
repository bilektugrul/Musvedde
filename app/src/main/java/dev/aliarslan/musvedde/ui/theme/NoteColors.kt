package dev.aliarslan.musvedde.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.aliarslan.musvedde.domain.NoteColor

private val lightNoteColors = mapOf(
    NoteColor.RED to Color(0xFFF8B4AE),
    NoteColor.ORANGE to Color(0xFFF5C29C),
    NoteColor.YELLOW to Color(0xFFFBF0B0),
    NoteColor.GREEN to Color(0xFFDDF2CE),
    NoteColor.TEAL to Color(0xFFBDE3D8),
    NoteColor.BLUE to Color(0xFFD5E5EF),
    NoteColor.DARK_BLUE to Color(0xFFB4CCE0),
    NoteColor.PURPLE to Color(0xFFD9C6E2),
    NoteColor.PINK to Color(0xFFF7DCE3),
    NoteColor.BROWN to Color(0xFFE8DDCB),
    NoteColor.GRAY to Color(0xFFE8E6E3),
)

private val darkNoteColors = mapOf(
    NoteColor.RED to Color(0xFF6E2229),
    NoteColor.ORANGE to Color(0xFF6A3518),
    NoteColor.YELLOW to Color(0xFF6B5110),
    NoteColor.GREEN to Color(0xFF2B4A33),
    NoteColor.TEAL to Color(0xFF17524E),
    NoteColor.BLUE to Color(0xFF285468),
    NoteColor.DARK_BLUE to Color(0xFF283E57),
    NoteColor.PURPLE to Color(0xFF47305A),
    NoteColor.PINK to Color(0xFF66344A),
    NoteColor.BROWN to Color(0xFF4A3F33),
    NoteColor.GRAY to Color(0xFF34332F),
)

fun noteContainerColor(color: NoteColor, dark: Boolean, amoled: Boolean): Color {
    if (color == NoteColor.DEFAULT) return Color.Unspecified
    if (!dark) return lightNoteColors.getValue(color)
    val base = darkNoteColors.getValue(color)
    return if (amoled) lerp(base, Color.Black, 0.45f) else base
}

fun noteSwatchColor(color: NoteColor, dark: Boolean): Color =
    if (color == NoteColor.DEFAULT) Color.Transparent
    else if (dark) darkNoteColors.getValue(color) else lightNoteColors.getValue(color)
