package dev.aliarslan.musvedde.ui.storage

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.NoteStorageRow
import org.junit.Test

class StorageSortTest {
    private fun row(id: Long, title: String, noteBytes: Long, versionBytes: Long, length: Int, createdAt: Long) = NoteStorageRow(
        id = id,
        title = title,
        preview = "",
        state = "ACTIVE",
        noteBytes = noteBytes,
        versionCount = 0,
        versionBytes = versionBytes,
        contentLength = length,
        createdAt = createdAt,
    )

    private val rows = listOf(
        row(1, "Çiçek", noteBytes = 10, versionBytes = 500, length = 10, createdAt = 300),
        row(2, "armut", noteBytes = 400, versionBytes = 0, length = 200, createdAt = 100),
        row(3, "Zeytin", noteBytes = 50, versionBytes = 50, length = 40, createdAt = 200),
        row(4, "", noteBytes = 5, versionBytes = 0, length = 5, createdAt = 400),
    )

    private fun ids(sort: StorageSort, descending: Boolean) = sortRows(rows, sort, descending, untitled = "Not").map { it.id }

    @Test
    fun sizeCountsNoteAndHistory() {
        assertThat(ids(StorageSort.SIZE, descending = true)).containsExactly(1L, 2L, 3L, 4L).inOrder()
        assertThat(ids(StorageSort.SIZE, descending = false)).containsExactly(4L, 3L, 2L, 1L).inOrder()
    }

    @Test
    fun nameSortsAlphabeticallyInTurkishIgnoringCase() {
        assertThat(ids(StorageSort.NAME, descending = false)).containsExactly(2L, 1L, 4L, 3L).inOrder()
    }

    @Test
    fun contentLengthUsesCharactersOfTheNoteItself() {
        assertThat(ids(StorageSort.CONTENT_LENGTH, descending = true)).containsExactly(2L, 3L, 1L, 4L).inOrder()
    }

    @Test
    fun dateSortsNewestOrOldestFirst() {
        assertThat(ids(StorageSort.DATE, descending = true)).containsExactly(4L, 1L, 3L, 2L).inOrder()
        assertThat(ids(StorageSort.DATE, descending = false)).containsExactly(2L, 3L, 1L, 4L).inOrder()
    }
}
