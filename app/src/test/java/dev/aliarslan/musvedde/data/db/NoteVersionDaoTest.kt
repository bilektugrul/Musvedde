package dev.aliarslan.musvedde.data.db

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteVersionDaoTest {
    private val db = inMemoryDatabase()
    private val notes = db.noteDao()
    private val versions = db.noteVersionDao()

    @After
    fun tearDown() = db.close()

    private fun version(noteId: Long, body: String, at: Long) =
        NoteVersionEntity(noteId = noteId, title = "", body = body, createdAt = at, sizeBytes = body.toByteArray().size.toLong())

    @Test
    fun versionsAreNewestFirstAndDeletedWithTheNote() = runTest {
        val id = notes.insert(noteEntity(body = "x"))
        versions.insert(version(id, "a", at = 1))
        versions.insert(version(id, "b", at = 2))

        assertThat(versions.observeForNote(id).first().map { it.body }).containsExactly("b", "a").inOrder()
        assertThat(versions.latestForNote(id)!!.body).isEqualTo("b")

        notes.deleteByIds(listOf(id))
        assertThat(versions.observeForNote(id).first()).isEmpty()
    }

    @Test
    fun storageRowsSumNoteAndVersionBytes() = runTest {
        val small = notes.insert(noteEntity(title = "a", body = "bb"))
        val big = notes.insert(noteEntity(title = "", body = "ş".repeat(100)))
        versions.insert(version(big, "c".repeat(50), at = 1))
        versions.insert(version(big, "d".repeat(30), at = 2))

        val rows = notes.storageRows()

        assertThat(rows.map { it.id }).containsExactly(big, small).inOrder()
        val bigRow = rows.first()
        assertThat(bigRow.noteBytes).isEqualTo(200)
        assertThat(bigRow.versionCount).isEqualTo(2)
        assertThat(bigRow.versionBytes).isEqualTo(80)
        assertThat(rows.last().noteBytes).isEqualTo(3)
        assertThat(versions.totals()).isEqualTo(VersionTotals(count = 2, bytes = 80))
    }

    @Test
    fun pendingKeepsTheFirstBaseline() = runTest {
        val id = notes.insert(noteEntity(body = "x"))
        versions.insertPending(PendingVersionEntity(noteId = id, baselineTitle = "", baselineBody = "ilk", baselineAt = 1))
        versions.insertPending(PendingVersionEntity(noteId = id, baselineTitle = "", baselineBody = "ikinci", baselineAt = 2))

        assertThat(versions.pending(id)!!.baselineBody).isEqualTo("ilk")
        assertThat(versions.allPending().map { it.noteId }).containsExactly(id)
    }
}
