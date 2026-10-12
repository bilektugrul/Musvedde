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
class FolderVisibilityTest {
    private val db = inMemoryDatabase()
    private val notes = NoteRepository(db, clock = { 1L }, io = Dispatchers.Unconfined)
    private val folders = FolderRepository(db, io = Dispatchers.Unconfined)

    @After
    fun tearDown() = db.close()

    private suspend fun ids(filter: NoteFilter, unlocked: Boolean = false) =
        notes.observe(filter, unlocked).first().map { it.id }

    private val main = NoteFilter.InState(NoteState.ACTIVE)

    @Test
    fun hiddenNotesStayOffTheMainPageButShowInSearchAndTheirFolder() = runTest {
        val folder = folders.create("Şiirler").getOrThrow()
        val hidden = notes.create("gizli", "deniz", folderId = folder)
        val visible = notes.create("açık", "deniz")
        notes.setHidden(listOf(hidden), true)

        assertThat(ids(main)).containsExactly(visible)
        assertThat(ids(NoteFilter.Search("deniz"))).containsExactly(hidden, visible)
        assertThat(ids(NoteFilter.InFolder(folder))).containsExactly(hidden)
    }

    @Test
    fun notesInFoldersAlsoShowOnTheMainPage() = runTest {
        val folder = folders.create("İş").getOrThrow()
        val inFolder = notes.create("toplantı", "", folderId = folder)

        assertThat(ids(main)).containsExactly(inFolder)
        assertThat(notes.get(inFolder)!!.folderId).isEqualTo(folder)
    }

    @Test
    fun lockedFoldersAreKeptOutOfListsAndSearchUntilUnlocked() = runTest {
        val folder = folders.create("Özel").getOrThrow()
        val secret = notes.create("sır", "deniz", folderId = folder)
        val archived = notes.create("eski sır", "deniz", folderId = folder)
        notes.archive(listOf(archived))
        folders.setLocked(folder, true)

        assertThat(ids(main)).isEmpty()
        assertThat(ids(main, unlocked = true)).isEmpty()
        assertThat(ids(NoteFilter.Search("deniz"))).isEmpty()
        assertThat(ids(NoteFilter.Search("deniz"), unlocked = true)).containsExactly(secret, archived)
        assertThat(ids(NoteFilter.InState(NoteState.ARCHIVED))).isEmpty()
        assertThat(ids(NoteFilter.InState(NoteState.ARCHIVED), unlocked = true)).containsExactly(archived)
    }

    @Test
    fun lockedNotesShowWithoutContentUntilUnlocked() = runTest {
        val id = notes.create("Başlık", "gizli satır")
        notes.setLocked(listOf(id), true)

        val masked = notes.observe(main).first().single()
        assertThat(masked.locked).isTrue()
        assertThat(masked.title).isEmpty()
        assertThat(masked.preview).isEmpty()
        assertThat(ids(NoteFilter.Search("gizli"))).isEmpty()

        val open = notes.observe(main, unlocked = true).first().single()
        assertThat(open.title).isEqualTo("Başlık")
        assertThat(ids(NoteFilter.Search("gizli"), unlocked = true)).containsExactly(id)
    }

    @Test
    fun lockingRemovesNotesFromTheNotificationShade() = runTest {
        val folder = folders.create("Kasa").getOrThrow()
        val a = notes.create("a", "", folderId = folder)
        val b = notes.create("b", "")
        notes.setPinnedToNotification(a, true)
        notes.setPinnedToNotification(b, true)

        folders.setLocked(folder, true)
        notes.setLocked(listOf(b), true)

        assertThat(notes.observePinnedToNotification().first()).isEmpty()
        assertThat(notes.get(a)!!.pinnedToNotification).isFalse()
        assertThat(notes.get(b)!!.pinnedToNotification).isFalse()
    }

    @Test
    fun noteLockStateFollowsItsFolder() = runTest {
        val folder = folders.create("Kasa").getOrThrow()
        val id = notes.create("a", "", folderId = folder)

        assertThat(notes.observeIsLocked(id).first()).isFalse()
        folders.setLocked(folder, true)
        assertThat(notes.observeIsLocked(id).first()).isTrue()
        assertThat(notes.hasLockedNotes()).isTrue()
    }

    @Test
    fun deletingAFolderKeepsItsNotes() = runTest {
        val folder = folders.create("Geçici").getOrThrow()
        val id = notes.create("kalır", "", folderId = folder)

        folders.delete(folder)

        assertThat(notes.get(id)!!.folderId).isNull()
        assertThat(folders.observeAll().first()).isEmpty()
    }

    @Test
    fun folderNamesMustBeUniqueAndNotBlank() = runTest {
        folders.create("Aynı").getOrThrow()

        assertThat(folders.create("Aynı").isFailure).isTrue()
        assertThat(folders.create("  ").isFailure).isTrue()
    }
}
