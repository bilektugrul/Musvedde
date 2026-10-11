package dev.aliarslan.musvedde.data.note

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenNoteTracker @Inject constructor() {
    private val open = MutableStateFlow<Set<Long>>(emptySet())
    val openNotes: StateFlow<Set<Long>> = open.asStateFlow()

    private val inForeground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = inForeground.asStateFlow()

    fun open(noteId: Long) = open.update { it + noteId }

    fun close(noteId: Long) = open.update { it - noteId }

    fun setForeground(value: Boolean) {
        inForeground.value = value
    }
}
