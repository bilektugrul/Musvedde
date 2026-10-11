package dev.aliarslan.musvedde.ui.editor

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.note.LabelRepository
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.NoteEventBus
import dev.aliarslan.musvedde.data.note.OpenNoteTracker
import dev.aliarslan.musvedde.data.note.NoteEvent
import dev.aliarslan.musvedde.data.note.VersionRepository
import dev.aliarslan.musvedde.domain.NoteState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import dev.aliarslan.musvedde.data.settings.InMemoryPreferencesDataStore
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class EditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: MusveddeDatabase
    private lateinit var notes: NoteRepository
    private lateinit var versions: VersionRepository
    private val noteEvents = NoteEventBus()
    private val tracker = OpenNoteTracker()
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())

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

    private fun viewModel(noteId: Long) = EditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("noteId" to noteId)),
        notes = notes,
        versions = versions,
        labels = LabelRepository(db.labelDao(), dispatcher),
        settings = SettingsRepository(InMemoryPreferencesDataStore()),
        appScope = appScope,
        clock = { 1L },
        noteEvents = noteEvents,
        openNotes = tracker,
    )

    private suspend fun count() = db.noteDao().count()

    @Test
    fun newNoteIsNotCreatedUntilFirstChar() = runTest(dispatcher) {
        val vm = viewModel(noteId = 0)
        advanceUntilIdle()
        assertThat(count()).isEqualTo(0)

        vm.body.setTextAndPlaceCursorAtEnd("i")
        Snapshot.sendApplyNotifications()
        advanceTimeBy(250)
        assertThat(count()).isEqualTo(0)
        advanceUntilIdle()

        assertThat(count()).isEqualTo(1)
        assertThat(db.noteDao().pageAll(10, 0).single().body).isEqualTo("i")
    }

    @Test
    fun existingNoteLoadsAndSavesEdits() = runTest(dispatcher) {
        val id = notes.create("Başlık", "eski")
        val vm = viewModel(noteId = id)
        advanceUntilIdle()
        assertThat(vm.title.text.toString()).isEqualTo("Başlık")

        vm.body.setTextAndPlaceCursorAtEnd("yeni")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertThat(notes.get(id)!!.body).isEqualTo("yeni")
    }

    @Test
    fun loadingDoesNotTriggerSave() = runTest(dispatcher) {
        val id = notes.create("t", "b")
        val before = db.noteDao().getById(id)!!.updatedAt
        viewModel(noteId = id)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertThat(db.noteDao().getById(id)!!.updatedAt).isEqualTo(before)
    }

    @Test
    fun blankNoteDeletedOnClear() = runTest(dispatcher) {
        val id = notes.create("", "")
        val vm = viewModel(noteId = id)
        advanceUntilIdle()

        vm.finish()
        advanceUntilIdle()

        assertThat(notes.get(id)).isNull()
    }

    @Test
    fun finishFlushesPendingEdit() = runTest(dispatcher) {
        val id = notes.create("", "a")
        val vm = viewModel(noteId = id)
        advanceUntilIdle()

        vm.body.setTextAndPlaceCursorAtEnd("ab")
        Snapshot.sendApplyNotifications()
        advanceTimeBy(10)
        vm.finish()
        advanceUntilIdle()

        assertThat(notes.get(id)!!.body).isEqualTo("ab")
    }

    @Test
    fun closingAfterEditingStoresAVersion() = runTest(dispatcher) {
        val id = notes.create("", "eski")
        val vm = viewModel(noteId = id)
        advanceUntilIdle()

        vm.body.setTextAndPlaceCursorAtEnd("yeni")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        vm.finish()
        advanceUntilIdle()

        assertThat(versions.observe(id).first().map { it.body }).containsExactly("yeni", "eski").inOrder()
    }

    @Test
    fun openingAndClosingWithoutEditsStoresNothing() = runTest(dispatcher) {
        val id = notes.create("", "aynı")
        val vm = viewModel(noteId = id)
        advanceUntilIdle()

        vm.finish()
        advanceUntilIdle()

        assertThat(versions.observe(id).first()).isEmpty()
    }

    @Test
    fun reloadPicksUpRestoredContent() = runTest(dispatcher) {
        val id = notes.create("", "a")
        val vm = viewModel(noteId = id)
        advanceUntilIdle()

        notes.updateContent(id, "", "geri yüklendi")
        vm.reloadIfChanged()
        advanceUntilIdle()

        assertThat(vm.body.text.toString()).isEqualTo("geri yüklendi")
    }

    @Test
    fun emptyingAnExistingNoteMovesItToTrashWithItsHistory() = runTest(dispatcher) {
        val id = notes.create("Başlık", "uzun bir not")
        val received = mutableListOf<NoteEvent>()
        backgroundScope.launch { noteEvents.events.collect { received += it } }
        val vm = viewModel(noteId = id)
        advanceUntilIdle()

        vm.title.setTextAndPlaceCursorAtEnd("")
        vm.body.setTextAndPlaceCursorAtEnd("")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        vm.finish()
        advanceUntilIdle()

        val note = notes.get(id)!!
        assertThat(note.state).isEqualTo(NoteState.TRASHED)
        assertThat(versions.observe(id).first().map { it.body }).containsExactly("uzun bir not")
        assertThat(received).containsExactly(NoteEvent.EmptiedToTrash(id))
    }

    @Test
    fun newNoteTypedAndErasedIsDeleted() = runTest(dispatcher) {
        val vm = viewModel(noteId = 0)
        advanceUntilIdle()

        vm.body.setTextAndPlaceCursorAtEnd("a")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        vm.body.setTextAndPlaceCursorAtEnd("")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        vm.finish()
        advanceUntilIdle()

        assertThat(count()).isEqualTo(0)
    }

    @Test
    fun openNoteIsTrackedUntilFinished() = runTest(dispatcher) {
        val id = notes.create("", "a")
        val vm = viewModel(noteId = id)
        advanceUntilIdle()
        assertThat(tracker.openNotes.value).containsExactly(id)

        vm.finish()
        advanceUntilIdle()
        assertThat(tracker.openNotes.value).isEmpty()
    }
}
