package dev.aliarslan.musvedde.data.note

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val DAY = 86_400_000L

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {
    private val db = inMemoryDatabase()
    private var now = 100 * DAY
    private val repository = NoteRepository(db, clock = { now }, io = Dispatchers.Unconfined)
    private val labels = LabelRepository(db.labelDao(), io = Dispatchers.Unconfined)

    @After
    fun tearDown() = db.close()

    private suspend fun active() = repository.observe(NoteFilter.InState(NoteState.ACTIVE)).first()

    @Test
    fun createThenUpdateContent() = runTest {
        val id = repository.create("Başlık", "ilk satır")
        now += 10
        repository.updateContent(id, "Başlık", "ikinci satır")

        val note = repository.get(id)!!
        assertThat(note.body).isEqualTo("ikinci satır")
        assertThat(note.updatedAt).isEqualTo(now)
        assertThat(note.createdAt).isEqualTo(now - 10)
    }

    @Test
    fun newNotesGoOnTop() = runTest {
        val first = repository.create("", "a")
        val second = repository.create("", "b")

        assertThat(active().map { it.id }).containsExactly(second, first).inOrder()
    }

    @Test
    fun unchangedUpdateKeepsUpdatedAt() = runTest {
        val id = repository.create("t", "b")
        val before = repository.get(id)!!.updatedAt
        now += 10

        repository.updateContent(id, "t", "b")

        assertThat(repository.get(id)!!.updatedAt).isEqualTo(before)
    }

    @Test
    fun trashSetsTrashedAtAndRestoreClearsIt() = runTest {
        val id = repository.create("", "x")
        repository.setPinnedToNotification(id, true)

        repository.trash(listOf(id))
        val trashed = repository.get(id)!!
        assertThat(trashed.state).isEqualTo(NoteState.TRASHED)
        assertThat(trashed.trashedAt).isEqualTo(now)
        assertThat(trashed.pinnedToNotification).isFalse()

        repository.restore(listOf(id))
        val restored = repository.get(id)!!
        assertThat(restored.state).isEqualTo(NoteState.ACTIVE)
        assertThat(restored.trashedAt).isNull()
    }

    @Test
    fun purgeRemovesOnlyOlderThan7Days() = runTest {
        val old = repository.create("", "old")
        repository.trash(listOf(old))
        now += 6 * DAY
        val recent = repository.create("", "recent")
        repository.trash(listOf(recent))
        now += 1 * DAY + 1

        val removed = repository.purgeTrashOlderThan(7)

        assertThat(removed).isEqualTo(1)
        assertThat(repository.get(old)).isNull()
        assertThat(repository.get(recent)).isNotNull()
    }

    @Test
    fun moveBetweenNeighbors() = runTest {
        val a = repository.create("", "a")
        val b = repository.create("", "b")
        val c = repository.create("", "c")

        repository.move(a, aboveId = c, belowId = b)

        assertThat(active().map { it.id }).containsExactly(c, a, b).inOrder()
    }

    @Test
    fun moveRenumbersWhenNoGap() = runTest {
        val ids = (1..3).map { repository.create("", "n$it") }
        db.noteDao().setPosition(ids[0], 1)
        db.noteDao().setPosition(ids[1], 2)
        db.noteDao().setPosition(ids[2], 3)

        repository.move(ids[0], aboveId = ids[2], belowId = ids[1])

        assertThat(active().map { it.id }).containsExactly(ids[2], ids[0], ids[1]).inOrder()
    }

    @Test
    fun deleteIfBlankRemovesEmptyNote() = runTest {
        val blank = repository.create("  ", "\n")
        val filled = repository.create("", "satır")

        repository.deleteIfBlank(blank)
        repository.deleteIfBlank(filled)

        assertThat(repository.get(blank)).isNull()
        assertThat(repository.get(filled)).isNotNull()
    }

    @Test
    fun searchFiltersTrashed() = runTest {
        val kept = repository.create("", "yağmur sesi")
        val trashed = repository.create("", "yağmur damlası")
        repository.trash(listOf(trashed))

        val found = repository.observe(NoteFilter.Search("yağ")).first().map { it.id }

        assertThat(found).containsExactly(kept)
    }

    @Test
    fun searchWithOnlyOperatorsReturnsNothing() = runTest {
        repository.create("", "satır")

        assertThat(repository.observe(NoteFilter.Search("\"*-")).first()).isEmpty()
        assertThat(repository.observe(NoteFilter.Search("OR AND NOT")).first()).isEmpty()
    }

    @Test
    fun colorChangeCountsAsChange() = runTest {
        val id = repository.create("", "x")
        val created = now
        now += 5

        repository.setColor(listOf(id), NoteColor.TEAL)

        assertThat(repository.get(id)!!.color).isEqualTo(NoteColor.TEAL)
        assertThat(repository.hasChangesSince(created)).isTrue()
        assertThat(repository.hasChangesSince(now)).isFalse()
    }

    @Test
    fun labelFilterAndDuplicateLabelRejected() = runTest {
        val id = repository.create("", "x")
        val label = labels.create("Aşk").getOrThrow()
        labels.setLabel(listOf(id), label, add = true)

        assertThat(labels.create(" Aşk ").isFailure).isTrue()
        assertThat(repository.observe(NoteFilter.WithLabel(label)).first().map { it.id }).containsExactly(id)
        assertThat(repository.get(id)!!.labelIds).containsExactly(label)
    }
}
