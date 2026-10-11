package dev.aliarslan.musvedde.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.data.note.LabelRepository
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.VersionRepository
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import dev.aliarslan.musvedde.di.ApplicationScope
import dev.aliarslan.musvedde.domain.Label
import dev.aliarslan.musvedde.domain.Note
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteState
import dev.aliarslan.musvedde.domain.Settings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class EditorUiState(
    val loaded: Boolean = false,
    val color: NoteColor = NoteColor.DEFAULT,
    val pinnedToTop: Boolean = false,
    val pinnedToNotification: Boolean = false,
    val state: NoteState = NoteState.ACTIVE,
    val labelIds: ImmutableSet<Long> = persistentSetOf(),
    val updatedAt: Long? = null,
)

sealed interface EditorEvent {
    data object Close : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notes: NoteRepository,
    private val versions: VersionRepository,
    private val labels: LabelRepository,
    private val settings: SettingsRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    private val clock: Clock,
) : ViewModel() {

    val title = TextFieldState()
    val body = TextFieldState()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val editorSettings: StateFlow<Settings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val allLabels: StateFlow<ImmutableList<Label>> = labels.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val idLock = Mutex()

    @Volatile
    private var noteId: Long? = savedStateHandle.get<Long>("noteId")?.takeIf { it > 0 }

    val isNewNote: Boolean = noteId == null

    private val saver = PendingSaver(appScope) { draft -> persist(draft) }

    private var finished = false

    private var baseline = Draft("", "")
    private var baselineAt = clock.now()

    @Volatile
    private var sessionStarted = false

    init {
        viewModelScope.launch {
            noteId?.let { id -> notes.get(id)?.let(::applyLoaded) }
            _uiState.update { it.copy(loaded = true) }
            snapshotFlow { Draft(title.text.toString(), body.text.toString()) }
                .drop(1)
                .collect(saver::submit)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun applyLoaded(note: Note) {
        if (!sessionStarted) {
            baseline = Draft(note.title, note.body)
            baselineAt = note.updatedAt
        }
        title.setTextAndPlaceCursorAtEnd(note.title)
        body.setTextAndPlaceCursorAtEnd(note.body)
        body.undoState.clearHistory()
        title.undoState.clearHistory()
        _uiState.update {
            it.copy(
                color = note.color,
                pinnedToTop = note.pinnedToTop,
                pinnedToNotification = note.pinnedToNotification,
                state = note.state,
                labelIds = note.labelIds.toImmutableSet(),
                updatedAt = note.updatedAt,
            )
        }
    }

    private suspend fun persist(draft: Draft) {
        val id = idLock.withLock {
            noteId ?: run {
                if (draft.title.isBlank() && draft.body.isBlank()) return
                notes.create(draft.title, draft.body).also { noteId = it }
            }
        }
        if (!sessionStarted) {
            sessionStarted = true
            versions.beginSession(id, baseline.title, baseline.body, baselineAt)
        }
        notes.updateContent(id, draft.title, draft.body)
        _uiState.update { it.copy(updatedAt = clock.now()) }
    }

    fun currentNoteId(): Long? = noteId

    fun reloadIfChanged() {
        val id = noteId ?: return
        if (!_uiState.value.loaded) return
        viewModelScope.launch {
            saver.flush().join()
            val note = notes.get(id) ?: return@launch
            if (note.title != title.text.toString() || note.body != body.text.toString()) applyLoaded(note)
        }
    }

    private suspend fun ensureNote(): Long = idLock.withLock {
        noteId ?: notes.create(title.text.toString(), body.text.toString()).also { noteId = it }
    }

    fun flush() {
        saver.flush()
    }

    fun setColor(color: NoteColor) = mutate {
        notes.setColor(listOf(it), color)
        _uiState.update { s -> s.copy(color = color) }
    }

    fun togglePinnedToTop() = mutate {
        val pinned = !_uiState.value.pinnedToTop
        notes.setPinnedToTop(listOf(it), pinned)
        _uiState.update { s -> s.copy(pinnedToTop = pinned) }
    }

    fun setPinnedToNotification(pinned: Boolean) = mutate {
        saver.flush().join()
        notes.setPinnedToNotification(it, pinned)
        _uiState.update { s -> s.copy(pinnedToNotification = pinned) }
    }

    fun toggleLabel(labelId: Long) = mutate {
        val add = labelId !in _uiState.value.labelIds
        labels.setLabel(listOf(it), labelId, add)
        _uiState.update { s ->
            s.copy(labelIds = (if (add) s.labelIds + labelId else s.labelIds - labelId).toImmutableSet())
        }
    }

    fun createLabel(name: String) = mutate { id ->
        labels.create(name).onSuccess { labelId ->
            labels.setLabel(listOf(id), labelId, add = true)
            _uiState.update { s -> s.copy(labelIds = (s.labelIds + labelId).toImmutableSet()) }
        }
    }

    fun archive() = mutateAndClose { id ->
        if (_uiState.value.state == NoteState.ARCHIVED) notes.unarchive(listOf(id)) else notes.archive(listOf(id))
    }

    fun trash() = mutateAndClose { id -> notes.trash(listOf(id)) }

    fun setFontSize(sizeSp: Float) {
        appScope.launch { settings.update { it.copy(editorFontSizeSp = sizeSp) } }
    }

    private fun mutate(block: suspend (Long) -> Unit) {
        viewModelScope.launch { block(ensureNote()) }
    }

    private fun mutateAndClose(block: suspend (Long) -> Unit) {
        appScope.launch {
            saver.flush().join()
            block(ensureNote())
        }
        viewModelScope.launch { events.send(EditorEvent.Close) }
    }

    fun finish() {
        if (finished) return
        finished = true
        appScope.launch {
            saver.flush().join()
            noteId?.let { id ->
                notes.deleteIfBlank(id)
                if (sessionStarted) versions.endSession(id)
            }
        }
    }

    override fun onCleared() = finish()
}
