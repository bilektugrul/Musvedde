package dev.aliarslan.musvedde.data.db

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.noteDao()
    private val labelDao = db.labelDao()

    @After
    fun tearDown() = db.close()

    @Test
    fun summariesOrderPinnedFirstThenPositionDesc() = runTest {
        val low = dao.insert(noteEntity(title = "low", position = 1))
        val high = dao.insert(noteEntity(title = "high", position = 2))
        val pinned = dao.insert(noteEntity(title = "pinned", position = 0, pinnedToTop = true))

        val ids = dao.observeSummaries("ACTIVE", mainPage = true, unlocked = false).first().map { it.id }

        assertThat(ids).containsExactly(pinned, high, low).inOrder()
    }

    @Test
    fun summaryPreviewIsTruncated() = runTest {
        dao.insert(noteEntity(body = "ş".repeat(50_000)))

        val summary = dao.observeSummaries("ACTIVE", mainPage = true, unlocked = false).first().single()

        assertThat(summary.preview.length).isEqualTo(400)
    }

    @Test
    fun updateContentTouchesOnlyTargetRow() = runTest {
        val a = dao.insert(noteEntity(title = "a", updatedAt = 1))
        val b = dao.insert(noteEntity(title = "b", updatedAt = 1))

        dao.updateContent(a, "a2", "body", updatedAt = 5)

        assertThat(dao.getById(a)!!.title).isEqualTo("a2")
        assertThat(dao.getById(a)!!.updatedAt).isEqualTo(5)
        assertThat(dao.getById(b)!!.updatedAt).isEqualTo(1)
    }

    @Test
    fun ftsFindsWordPrefix() = runTest {
        val id = dao.insert(noteEntity(body = "gecenin karanlığı"))
        dao.insert(noteEntity(body = "sabah"))

        val ids = dao.searchSummaries("karan*", secret = false).first().map { it.id }

        assertThat(ids).containsExactly(id)
    }

    @Test
    fun ftsSeesUpdatedContent() = runTest {
        val id = dao.insert(noteEntity(body = "eski"))
        dao.updateContent(id, "", "yeni satır", updatedAt = 2)

        assertThat(dao.searchSummaries("eski*", secret = false).first()).isEmpty()
        assertThat(dao.searchSummaries("satır*", secret = false).first().map { it.id }).containsExactly(id)
    }

    @Test
    fun deleteTrashedBeforeRemovesOnlyOldTrash() = runTest {
        val old = dao.insert(noteEntity(state = "TRASHED", trashedAt = 10))
        val recent = dao.insert(noteEntity(state = "TRASHED", trashedAt = 100))
        val active = dao.insert(noteEntity())

        val removed = dao.deleteTrashedBefore(cutoff = 50)

        assertThat(removed).isEqualTo(1)
        assertThat(dao.getById(old)).isNull()
        assertThat(dao.getById(recent)).isNotNull()
        assertThat(dao.getById(active)).isNotNull()
    }

    @Test
    fun labelIdsAreAggregated() = runTest {
        val note = dao.insert(noteEntity())
        val l1 = labelDao.insert(LabelEntity(name = "aşk"))
        val l2 = labelDao.insert(LabelEntity(name = "deniz"))
        labelDao.addToNotes(l1, listOf(note))
        labelDao.addToNotes(l2, listOf(note))

        val summary = dao.observeSummaries("ACTIVE", mainPage = true, unlocked = false).first().single().toDomain()

        assertThat(summary.labelIds).containsExactly(l1, l2)
    }
}
