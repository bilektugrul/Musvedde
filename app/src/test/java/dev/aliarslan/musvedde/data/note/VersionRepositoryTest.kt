package dev.aliarslan.musvedde.data.note

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VersionRepositoryTest {
    private val db = inMemoryDatabase()
    private var now = 1_000L
    private val notes = NoteRepository(db, clock = { now }, io = Dispatchers.Unconfined)
    private val versions = VersionRepository(db, clock = { now }, io = Dispatchers.Unconfined)

    @After
    fun tearDown() = db.close()

    private suspend fun bodies(noteId: Long) = versions.observe(noteId).first().map { it.body }

    private suspend fun session(noteId: Long, newBody: String) {
        val note = notes.get(noteId)!!
        versions.beginSession(noteId, note.title, note.body, note.updatedAt)
        now += 100
        notes.updateContent(noteId, note.title, newBody)
        versions.endSession(noteId)
    }

    @Test
    fun closingAfterAnEditStoresTheBaselineAndTheNewState() = runTest {
        val id = notes.create("", "ilk")
        session(id, "ikinci")

        assertThat(bodies(id)).containsExactly("ikinci", "ilk").inOrder()
        val newest = versions.observe(id).first().first()
        assertThat(newest.createdAt).isEqualTo(now)
        assertThat(newest.sizeBytes).isEqualTo("ikinci".toByteArray().size.toLong())
    }

    @Test
    fun closingWithoutChangesStoresNothing() = runTest {
        val id = notes.create("", "ilk")
        versions.beginSession(id, "", "ilk", 1_000)
        versions.endSession(id)

        assertThat(bodies(id)).isEmpty()
    }

    @Test
    fun laterSessionsStoreOnlyTheNewState() = runTest {
        val id = notes.create("", "a")
        session(id, "b")
        session(id, "c")

        assertThat(bodies(id)).containsExactly("c", "b", "a").inOrder()
    }

    @Test
    fun newNoteStoresOneVersion() = runTest {
        val id = notes.create("", "")
        versions.beginSession(id, "", "", now)
        notes.updateContent(id, "", "yeni not")
        versions.endSession(id)

        assertThat(bodies(id)).containsExactly("yeni not")
    }

    @Test
    fun interruptedSessionIsRecoveredOnNextStart() = runTest {
        val id = notes.create("", "a")
        versions.beginSession(id, "", "a", now)
        notes.updateContent(id, "", "b")

        versions.recoverPending()
        versions.recoverPending()

        assertThat(bodies(id)).containsExactly("b", "a").inOrder()
    }

    @Test
    fun restoreKeepsTheCurrentStateAndAppliesTheVersion() = runTest {
        val id = notes.create("", "a")
        session(id, "b")
        notes.updateContent(id, "", "c")
        val first = versions.observe(id).first().last()
        now += 100

        versions.restore(first.id)

        assertThat(notes.get(id)!!.body).isEqualTo("a")
        assertThat(bodies(id)).containsExactly("a", "c", "b", "a").inOrder()
    }

    @Test
    fun singleVersionsAndWholeHistoriesCanBeDeleted() = runTest {
        val one = notes.create("", "a")
        session(one, "b")
        val other = notes.create("", "x")
        session(other, "y")

        versions.delete(versions.observe(one).first().first().id)
        assertThat(bodies(one)).containsExactly("a")

        versions.clearNote(one)
        assertThat(bodies(one)).isEmpty()
        assertThat(bodies(other)).hasSize(2)

        versions.clearAll()
        assertThat(bodies(other)).isEmpty()
    }

    @Test
    fun restoreLatestBringsBackTheNewestVersion() = runTest {
        val id = notes.create("", "a")
        session(id, "b")
        notes.updateContent(id, "", "")

        versions.restoreLatest(id)

        assertThat(notes.get(id)!!.body).isEqualTo("b")
    }
}
