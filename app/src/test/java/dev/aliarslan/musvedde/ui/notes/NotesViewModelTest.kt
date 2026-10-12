package dev.aliarslan.musvedde.ui.notes

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.lock.FakeKeyWrapper
import dev.aliarslan.musvedde.data.lock.LockConfig
import dev.aliarslan.musvedde.data.lock.LockManager
import dev.aliarslan.musvedde.data.note.FolderRepository
import dev.aliarslan.musvedde.data.note.LabelRepository
import dev.aliarslan.musvedde.data.note.NoteEvent
import dev.aliarslan.musvedde.data.note.NoteEventBus
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.VersionRepository
import dev.aliarslan.musvedde.data.settings.InMemoryPreferencesDataStore
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteState
import dev.aliarslan.musvedde.ui.NotesSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())
    private lateinit var db: MusveddeDatabase
    private lateinit var notes: NoteRepository
    private lateinit var versions: VersionRepository
    private val noteEvents = NoteEventBus()
    private val lockManager = LockManager(InMemoryPreferencesDataStore(), FakeKeyWrapper(), clock = { 1L }, LockConfig(1_000, 1_000))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = inMemoryDatabase(dispatcher)
        notes = NoteRepository(db, clock = { 1L }, io = dispatcher)
        versions = VersionRepository(db, clock = { 2L }, io = dispatcher)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(section: NotesSection = NotesSection.ACTIVE, folderId: Long = -1L) = NotesViewModel(
        savedStateHandle = SavedStateHandle(mapOf("section" to section, "labelId" to -1L, "folderId" to folderId)),
        notes = notes,
        versions = versions,
        noteEvents = noteEvents,
        labels = LabelRepository(db.labelDao(), dispatcher),
        settings = SettingsRepository(InMemoryPreferencesDataStore()),
        folders = FolderRepository(db, dispatcher),
        lockManager = lockManager,
        appScope = appScope,
    )

    private fun TestScope.collect(vm: NotesViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
    }

    @Test
    fun hidingSelectedNotesTakesThemOffTheMainPageAndBack() = runTest(dispatcher) {
        val a = notes.create("", "a")
        val b = notes.create("", "b")
        val vm = viewModel()
        collect(vm)
        advanceUntilIdle()

        vm.select(a)
        advanceUntilIdle()
        assertThat(vm.uiState.value.selectionHidden).isFalse()
        vm.toggleHiddenSelected()
        advanceUntilIdle()
        assertThat(vm.uiState.value.others.map { it.id }).containsExactly(b)

        vm.setQuery("a")
        advanceUntilIdle()
        vm.select(a)
        advanceUntilIdle()
        assertThat(vm.uiState.value.selectionHidden).isTrue()
        vm.toggleHiddenSelected()
        vm.setQuery("")
        advanceUntilIdle()
        assertThat(vm.uiState.value.others.map { it.id }).containsExactly(a, b)
    }

    @Test
    fun folderSectionShowsItsNotesAndTheFolder() = runTest(dispatcher) {
        val folders = FolderRepository(db, dispatcher)
        val folder = folders.create("Şiirler").getOrThrow()
        val inside = notes.create("", "içeride")
        notes.create("", "dışarıda")
        val main = viewModel()
        collect(main)
        advanceUntilIdle()
        main.select(inside)
        main.moveSelectedToFolder(folder)
        advanceUntilIdle()

        val vm = viewModel(NotesSection.FOLDER, folder)
        collect(vm)
        advanceUntilIdle()

        assertThat(vm.uiState.value.folder?.name).isEqualTo("Şiirler")
        assertThat(vm.uiState.value.others.map { it.id }).containsExactly(inside)
    }

    @Test
    fun lockedNotesOpenUpWhenTheSessionUnlocks() = runTest(dispatcher) {
        lockManager.setPin("1234")
        val id = notes.create("Başlık", "gizli")
        val vm = viewModel()
        collect(vm)
        advanceUntilIdle()
        vm.select(id)
        vm.toggleLockedSelected()
        advanceUntilIdle()
        lockManager.lock()
        advanceUntilIdle()

        assertThat(vm.uiState.value.others.single().title).isEmpty()
        assertThat(vm.uiState.value.others.single().locked).isTrue()

        lockManager.unlock("1234")
        advanceUntilIdle()
        assertThat(vm.uiState.value.others.single().title).isEqualTo("Başlık")
    }

    @Test
    fun splitsPinnedAndOthers() = runTest(dispatcher) {
        val a = notes.create("", "a")
        val b = notes.create("", "b")
        notes.setPinnedToTop(listOf(a), true)
        val vm = viewModel()
        collect(vm)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.pinned.map { it.id }).containsExactly(a)
        assertThat(state.others.map { it.id }).containsExactly(b)
    }

    @Test
    fun searchIsDebounced150ms() = runTest(dispatcher) {
        notes.create("", "yağmur")
        notes.create("", "güneş")
        val vm = viewModel()
        collect(vm)
        advanceUntilIdle()

        vm.setQuery("yağ")
        advanceTimeBy(100)
        assertThat(vm.uiState.value.others).hasSize(2)

        advanceTimeBy(60)
        advanceUntilIdle()
        assertThat(vm.uiState.value.others.map { it.preview }).containsExactly("yağmur")
    }

    @Test
    fun trashSelectedEmitsUndoEventAndUndoRestores() = runTest(dispatcher) {
        val id = notes.create("", "satır")
        val vm = viewModel()
        collect(vm)
        advanceUntilIdle()

        vm.toggleSelect(id)
        vm.trashSelected()
        advanceUntilIdle()
        val event = vm.events.first()
        assertThat(event).isEqualTo(NotesEvent.Trashed(count = 1))
        assertThat(notes.get(id)!!.state).isEqualTo(NoteState.TRASHED)
        assertThat(vm.uiState.value.selection).isEmpty()

        vm.undoLast()
        advanceUntilIdle()
        assertThat(notes.get(id)!!.state).isEqualTo(NoteState.ACTIVE)
    }

    @Test
    fun reorderDisabledWhileSearching() = runTest(dispatcher) {
        notes.create("", "a")
        val vm = viewModel()
        collect(vm)
        advanceUntilIdle()
        assertThat(vm.uiState.value.canReorder).isTrue()

        vm.setQuery("a")
        advanceUntilIdle()

        assertThat(vm.uiState.value.canReorder).isFalse()
    }

    @Test
    fun trashSectionCannotReorder() = runTest(dispatcher) {
        val vm = viewModel(NotesSection.TRASHED)
        collect(vm)
        advanceUntilIdle()

        assertThat(vm.uiState.value.canReorder).isFalse()
    }

    @Test
    fun emptiedNoteOffersUndoThatRestoresItsText() = runTest(dispatcher) {
        val id = notes.create("", "not")
        versions.beginSession(id, "", "not", 1)
        notes.updateContent(id, "", "")
        versions.endSession(id)
        notes.trash(listOf(id))
        val vm = viewModel()
        collect(vm)
        advanceUntilIdle()

        noteEvents.send(NoteEvent.EmptiedToTrash(id))
        advanceUntilIdle()
        assertThat(vm.events.first()).isEqualTo(NotesEvent.EmptiedToTrash)

        vm.undoLast()
        advanceUntilIdle()
        val note = notes.get(id)!!
        assertThat(note.state).isEqualTo(NoteState.ACTIVE)
        assertThat(note.body).isEqualTo("not")
    }

    @Test
    fun selectingTwiceKeepsTheNoteSelected() = runTest(dispatcher) {
        val id = notes.create("", "a")
        val vm = viewModel()
        collect(vm)
        advanceUntilIdle()

        vm.select(id)
        vm.select(id)
        advanceUntilIdle()

        assertThat(vm.uiState.value.selection).containsExactly(id)
    }

    @Test
    fun colorChangeKeepsSelectionAndPickerShowsTheColor() = runTest(dispatcher) {
        val a = notes.create("", "a")
        val b = notes.create("", "b")
        val vm = viewModel()
        collect(vm)
        advanceUntilIdle()
        vm.select(a)
        vm.select(b)
        advanceUntilIdle()
        assertThat(vm.uiState.value.selectedColor).isEqualTo(NoteColor.DEFAULT)

        vm.colorSelected(NoteColor.TEAL)
        advanceUntilIdle()

        assertThat(vm.uiState.value.selection).containsExactly(a, b)
        assertThat(vm.uiState.value.selectedColor).isEqualTo(NoteColor.TEAL)
    }

    @Test
    fun mixedColorsShowNoColorAsSelected() = runTest(dispatcher) {
        val a = notes.create("", "a")
        val b = notes.create("", "b")
        notes.setColor(listOf(a), NoteColor.RED)
        val vm = viewModel()
        collect(vm)
        advanceUntilIdle()

        vm.select(a)
        vm.select(b)
        advanceUntilIdle()

        assertThat(vm.uiState.value.selectedColor).isNull()
    }
}
