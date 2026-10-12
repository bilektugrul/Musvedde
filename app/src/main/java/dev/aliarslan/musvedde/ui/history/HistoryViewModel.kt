package dev.aliarslan.musvedde.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.VersionRepository
import dev.aliarslan.musvedde.di.ApplicationScope
import dev.aliarslan.musvedde.domain.NoteVersion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VersionItem(
    val version: NoteVersion,
    val stats: DiffStats?,
    val previous: NoteVersion?,
)

data class HistoryUiState(
    val loaded: Boolean = false,
    val items: ImmutableList<VersionItem> = persistentListOf(),
    val selected: VersionItem? = null,
)

fun NoteVersion.fullText(): String = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")

sealed interface HistoryEvent {
    data object Restored : HistoryEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val versions: VersionRepository,
    notes: NoteRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val noteId: Long = savedStateHandle.get<Long>("noteId") ?: 0L

    /** Null until known; history of a locked note stays behind the PIN like the note itself. */
    val locked: StateFlow<Boolean?> = notes.observeIsLocked(noteId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val selectedId = MutableStateFlow<Long?>(null)

    private val items = versions.observe(noteId)
        .map { list ->
            list.mapIndexed { index, version ->
                val previous = list.getOrNull(index + 1)
                VersionItem(version, previous?.let { diffStats(it.fullText(), version.fullText()) }, previous)
            }.toImmutableList()
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<HistoryUiState> = combine(items, selectedId) { list, id ->
        HistoryUiState(loaded = true, items = list, selected = list.firstOrNull { it.version.id == id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private val eventChannel = Channel<HistoryEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun select(versionId: Long?) {
        selectedId.value = versionId
    }

    fun restore(versionId: Long) {
        appScope.launch {
            versions.restore(versionId)
            eventChannel.send(HistoryEvent.Restored)
        }
    }

    fun delete(versionId: Long) {
        if (selectedId.value == versionId) selectedId.value = null
        appScope.launch { versions.delete(versionId) }
    }

    fun clear() {
        selectedId.value = null
        appScope.launch { versions.clearNote(noteId) }
    }
}
