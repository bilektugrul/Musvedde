package dev.aliarslan.musvedde.data.note

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface NoteEvent {
    data class EmptiedToTrash(val noteId: Long) : NoteEvent
}

@Singleton
class NoteEventBus @Inject constructor() {
    private val flow = MutableSharedFlow<NoteEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NoteEvent> = flow.asSharedFlow()

    suspend fun send(event: NoteEvent) = flow.emit(event)
}
