package dev.aliarslan.musvedde.ui.storage

import dev.aliarslan.musvedde.data.db.NoteStorageRow
import java.text.Collator
import java.util.Locale

enum class StorageSort { SIZE, NAME, CONTENT_LENGTH, DATE }

fun NoteStorageRow.displayName(untitled: String): String =
    title.ifBlank { preview.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }.ifBlank { untitled }

fun sortRows(rows: List<NoteStorageRow>, sort: StorageSort, descending: Boolean, untitled: String): List<NoteStorageRow> {
    val comparator: Comparator<NoteStorageRow> = when (sort) {
        StorageSort.SIZE -> compareBy { it.noteBytes + it.versionBytes }
        StorageSort.NAME -> {
            val collator = Collator.getInstance(Locale.forLanguageTag("tr")).apply { strength = Collator.SECONDARY }
            compareBy(collator) { it.displayName(untitled) }
        }
        StorageSort.CONTENT_LENGTH -> compareBy { it.contentLength }
        StorageSort.DATE -> compareBy { it.createdAt }
    }
    return rows.sortedWith(if (descending) comparator.reversed() else comparator)
}
