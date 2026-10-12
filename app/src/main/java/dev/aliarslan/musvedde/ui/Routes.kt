package dev.aliarslan.musvedde.ui

import androidx.annotation.Keep
import dev.aliarslan.musvedde.ui.settings.SettingsCategory
import kotlinx.serialization.Serializable

@Serializable
data class NotesRoute(val section: NotesSection = NotesSection.ACTIVE, val labelId: Long = NO_LABEL) {
    companion object {
        const val NO_LABEL = -1L
    }
}

@Keep
@Serializable
enum class NotesSection { ACTIVE, ARCHIVED, TRASHED, LABEL }

@Serializable
data class EditorRoute(val noteId: Long = NEW_NOTE) {
    companion object {
        const val NEW_NOTE = 0L
    }
}

@Serializable
data class HistoryRoute(val noteId: Long)

@Serializable
data object StorageRoute

@Serializable
data object SettingsRoute

@Serializable
data class SettingsCategoryRoute(val category: SettingsCategory)

@Serializable
data object ErrorLogRoute
