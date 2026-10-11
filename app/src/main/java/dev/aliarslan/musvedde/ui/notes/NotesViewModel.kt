package dev.aliarslan.musvedde.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.data.note.LabelRepository
import dev.aliarslan.musvedde.data.note.NoteFilter
import dev.aliarslan.musvedde.data.note.NoteEvent
import dev.aliarslan.musvedde.data.note.NoteEventBus
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.VersionRepository
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import dev.aliarslan.musvedde.di.ApplicationScope
import dev.aliarslan.musvedde.domain.EditorFont
import dev.aliarslan.musvedde.domain.Label
import dev.aliarslan.musvedde.domain.ListLayout
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteState
import dev.aliarslan.musvedde.domain.NoteSummary
import dev.aliarslan.musvedde.domain.PreviewSize
import dev.aliarslan.musvedde.ui.NotesRoute
import dev.aliarslan.musvedde.ui.NotesSection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesUiState(
    val section: NotesSection,
    val labelId: Long = NotesRoute.NO_LABEL,
    val loaded: Boolean = false,
    val pinned: ImmutableList<NoteSummary> = persistentListOf(),
    val others: ImmutableList<NoteSummary> = persistentListOf(),
    val layout: ListLayout = ListLayout.GRID,
    val previewSize: PreviewSize = PreviewSize.MEDIUM,
    val previewFont: EditorFont = EditorFont.SERIF,
    val selection: ImmutableSet<Long> = persistentSetOf(),
    val query: String = "",
    val canReorder: Boolean = false,
)

sealed interface NotesEvent {
    data class Trashed(val count: Int) : NotesEvent
    data class Archived(val count: Int) : NotesEvent
    data class Unarchived(val count: Int) : NotesEvent
    data class Restored(val count: Int) : NotesEvent
    data object EmptiedToTrash : NotesEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notes: NoteRepository,
    private val versions: VersionRepository,
    private val noteEvents: NoteEventBus,
    private val labels: LabelRepository,
    private val settings: SettingsRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val section: NotesSection = savedStateHandle.get<NotesSection>("section") ?: NotesSection.ACTIVE
    private val labelId: Long = savedStateHandle.get<Long>("labelId") ?: NotesRoute.NO_LABEL

    private val baseFilter: NoteFilter = when (section) {
        NotesSection.ACTIVE -> NoteFilter.InState(NoteState.ACTIVE)
        NotesSection.ARCHIVED -> NoteFilter.InState(NoteState.ARCHIVED)
        NotesSection.TRASHED -> NoteFilter.InState(NoteState.TRASHED)
        NotesSection.LABEL -> NoteFilter.WithLabel(labelId)
    }

    private val query = MutableStateFlow("")
    private val selection = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    private val visibleNotes: Flow<ImmutableList<NoteSummary>> = query
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { q -> notes.observe(if (q.isBlank()) baseFilter else NoteFilter.Search(q)) }

    val uiState: StateFlow<NotesUiState> = combine(
        visibleNotes,
        selection,
        query,
        settings.settings,
    ) { list, selected, q, s ->
        val groupPinned = q.isBlank() && (section == NotesSection.ACTIVE || section == NotesSection.LABEL)
        NotesUiState(
            section = section,
            labelId = labelId,
            loaded = true,
            pinned = if (groupPinned) list.filter { it.pinnedToTop }.toImmutableList() else persistentListOf(),
            others = if (groupPinned) list.filterNot { it.pinnedToTop }.toImmutableList() else list,
            layout = s.listLayout,
            previewSize = s.previewSize,
            previewFont = s.editorFont,
            selection = selected,
            query = q,
            canReorder = q.isBlank() && section == NotesSection.ACTIVE,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState(section, labelId))

    val allLabels: StateFlow<ImmutableList<Label>> = labels.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

    private val eventChannel = Channel<NotesEvent>(Channel.BUFFERED)
    val events: Flow<NotesEvent> = eventChannel.receiveAsFlow()

    private var lastUndo: (suspend () -> Unit)? = null

    init {
        viewModelScope.launch {
            noteEvents.events.collect { event ->
                when (event) {
                    is NoteEvent.EmptiedToTrash -> remember(NotesEvent.EmptiedToTrash) {
                        notes.restore(listOf(event.noteId))
                        versions.restoreLatest(event.noteId)
                    }
                }
            }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleSelect(id: Long) = selection.update { if (id in it) it.removing(id) else it.adding(id) }

    fun select(id: Long) = selection.update { it.adding(id) }

    fun clearSelection() {
        selection.value = persistentSetOf()
    }

    fun toggleLayout() = appScope.launch {
        settings.update { it.copy(listLayout = if (it.listLayout == ListLayout.GRID) ListLayout.LIST else ListLayout.GRID) }
    }

    fun colorSelected(color: NoteColor) = onSelection { notes.setColor(it, color) }

    fun pinSelected() = onSelection { ids ->
        val all = uiState.value.pinned + uiState.value.others
        val allPinned = all.filter { it.id in ids }.all { it.pinnedToTop }
        notes.setPinnedToTop(ids, !allPinned)
    }

    fun labelSelected(labelId: Long, add: Boolean) = onSelection(clear = false) { labels.setLabel(it, labelId, add) }

    fun archiveSelected() = onSelection { ids ->
        if (section == NotesSection.ARCHIVED) {
            notes.unarchive(ids)
            remember(NotesEvent.Unarchived(ids.size)) { notes.archive(ids) }
        } else {
            notes.archive(ids)
            remember(NotesEvent.Archived(ids.size)) { notes.unarchive(ids) }
        }
    }

    fun trashSelected() = onSelection { ids ->
        notes.trash(ids)
        remember(NotesEvent.Trashed(ids.size)) { notes.restore(ids) }
    }

    fun restoreSelected() = onSelection { ids ->
        notes.restore(ids)
        remember(NotesEvent.Restored(ids.size)) { notes.trash(ids) }
    }

    fun deleteForeverSelected() = onSelection { notes.deleteForever(it) }

    fun emptyTrash() = appScope.launch { notes.emptyTrash() }

    fun move(id: Long, aboveId: Long?, belowId: Long?) = appScope.launch { notes.move(id, aboveId, belowId) }

    fun undoLast() {
        val undo = lastUndo ?: return
        lastUndo = null
        appScope.launch { undo() }
    }

    private suspend fun remember(event: NotesEvent, undo: suspend () -> Unit) {
        lastUndo = undo
        eventChannel.send(event)
    }

    private fun onSelection(clear: Boolean = true, block: suspend (List<Long>) -> Unit) {
        val ids = selection.value.toList()
        if (ids.isEmpty()) return
        if (clear) clearSelection()
        appScope.launch { block(ids) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
    }
}
