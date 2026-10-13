package dev.aliarslan.musvedde.data.note

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.domain.NoteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecretFolderTest {
    private val db = inMemoryDatabase()
    private val notes = NoteRepository(db, clock = { 1L }, io = Dispatchers.Unconfined)
    private val labels = LabelRepository(db.labelDao(), Dispatchers.Unconfined)

    @After
    fun tearDown() = db.close()

    private suspend fun ids(filter: NoteFilter, unlocked: Boolean = false) =
        notes.observe(filter, unlocked).first().map { it.id }

    private val main = NoteFilter.InState(NoteState.ACTIVE)

    @Test
    fun secretNotesOnlyShowInTheSecretFolder() = runTest {
        val label = labels.create("Deniz").getOrThrow()
        val secret = notes.create("sır", "deniz")
        val open = notes.create("açık", "deniz")
        labels.setLabel(listOf(secret, open), label, add = true)
        notes.setLocked(listOf(secret), true)

        assertThat(ids(main)).containsExactly(open)
        assertThat(ids(main, unlocked = true)).containsExactly(open)
        assertThat(ids(NoteFilter.WithLabel(label))).containsExactly(open)
        assertThat(ids(NoteFilter.Secret)).containsExactly(secret)
    }

    @Test
    fun searchNeverMixesTheSecretFolderWithTheRest() = runTest {
        val secret = notes.create("sır", "deniz")
        val open = notes.create("açık", "deniz")
        notes.setLocked(listOf(secret), true)

        assertThat(ids(NoteFilter.Search("deniz"))).containsExactly(open)
        assertThat(ids(NoteFilter.Search("deniz"), unlocked = true)).containsExactly(open)
        assertThat(ids(NoteFilter.Search("deniz", secret = true))).containsExactly(secret)
    }

    @Test
    fun hiddenNotesStayOffTheMainPageButShowInSearch() = runTest {
        val hidden = notes.create("gizli", "deniz")
        val visible = notes.create("açık", "deniz")
        notes.setHidden(listOf(hidden), true)

        assertThat(ids(main)).containsExactly(visible)
        assertThat(ids(NoteFilter.Search("deniz"))).containsExactly(hidden, visible)
    }

    @Test
    fun archivedAndTrashedSecretNotesNeedTheFolderOpen() = runTest {
        val archived = notes.create("", "arşiv")
        val trashed = notes.create("", "çöp")
        notes.setLocked(listOf(archived, trashed), true)
        notes.archive(listOf(archived))
        notes.trash(listOf(trashed))

        assertThat(ids(NoteFilter.InState(NoteState.ARCHIVED))).isEmpty()
        assertThat(ids(NoteFilter.InState(NoteState.TRASHED))).isEmpty()
        assertThat(ids(NoteFilter.InState(NoteState.ARCHIVED), unlocked = true)).containsExactly(archived)
        assertThat(ids(NoteFilter.InState(NoteState.TRASHED), unlocked = true)).containsExactly(trashed)
        assertThat(ids(NoteFilter.Secret)).isEmpty()
    }

    @Test
    fun secretNotesLeaveTheNotificationShade() = runTest {
        val id = notes.create("a", "")
        notes.setPinnedToNotification(id, true)

        notes.setLocked(listOf(id), true)

        assertThat(notes.observePinnedToNotification().first()).isEmpty()
        assertThat(notes.get(id)!!.pinnedToNotification).isFalse()
        assertThat(notes.observeIsLocked(id).first()).isTrue()
        assertThat(notes.hasLockedNotes()).isTrue()
    }

    @Test
    fun notesCanBeCreatedInsideTheSecretFolder() = runTest {
        val id = notes.create("", "doğrudan", secret = true)

        assertThat(ids(NoteFilter.Secret)).containsExactly(id)
        assertThat(ids(main)).isEmpty()
    }
}
