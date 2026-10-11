package dev.aliarslan.musvedde.ui

import kotlinx.serialization.Serializable

@Serializable
data class NotesRoute(val section: NotesSection = NotesSection.ACTIVE, val labelId: Long = NO_LABEL) {
    companion object {
        const val NO_LABEL = -1L
    }
}

@Serializable
enum class NotesSection { ACTIVE, ARCHIVED, TRASHED, LABEL }

@Serializable
data class EditorRoute(val noteId: Long = NEW_NOTE) {
    companion object {
        const val NEW_NOTE = 0L
    }
}

@Serializable
data object SettingsRoute

@Serializable
data object ErrorLogRoute
