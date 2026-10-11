package dev.aliarslan.musvedde.domain

enum class NoteColor {
    DEFAULT, RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, DARK_BLUE, PURPLE, PINK, BROWN, GRAY;

    companion object {
        fun fromName(name: String): NoteColor = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
