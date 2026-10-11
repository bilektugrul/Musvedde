package dev.aliarslan.musvedde.data.backup

import androidx.room.withTransaction
import dev.aliarslan.musvedde.data.db.LabelEntity
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.NoteContentKey
import dev.aliarslan.musvedde.data.db.NoteEntity
import dev.aliarslan.musvedde.data.db.NoteLabelCrossRef
import dev.aliarslan.musvedde.data.note.Positions
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteState
import java.io.InputStream
import javax.inject.Inject

class RestoreService @Inject constructor(
    private val db: MusveddeDatabase,
    private val serializer: BackupSerializer,
) {
    suspend fun restoreFrom(input: InputStream, mode: RestoreMode): Result<Int> =
        runCatching { serializer.read(input) }.fold(
            onSuccess = { restore(it, mode) },
            onFailure = { Result.failure(it) },
        )

    suspend fun restore(content: BackupContent, mode: RestoreMode): Result<Int> = runCatching {
        db.withTransaction {
            val notes = db.noteDao()
            val labels = db.labelDao()
            if (mode == RestoreMode.REPLACE) {
                notes.deleteAll()
                labels.deleteAll()
            }

            val labelIds = labels.all().associate { it.name to it.id }.toMutableMap()
            val wantedNames = content.labels.map { it.name } + content.notes.flatMap { it.labelNames }
            wantedNames.distinct().filter { it !in labelIds }.forEach { name ->
                labelIds[name] = labels.insert(LabelEntity(name = name))
            }

            val existing = if (mode == RestoreMode.MERGE) notes.contentKeys().toHashSet() else hashSetOf()
            val offset = if (mode == RestoreMode.MERGE) (notes.maxPosition() ?: 0) + Positions.STEP else 0
            val toInsert = content.notes.filter { NoteContentKey(it.title, it.body, it.createdAt) !in existing }
            val minPosition = toInsert.minOfOrNull { it.position } ?: 0

            toInsert.chunked(CHUNK).forEach { chunk ->
                val ids = notes.insertAll(
                    chunk.map { note ->
                        val state = NoteState.entries.firstOrNull { it.name == note.state } ?: NoteState.ACTIVE
                        NoteEntity(
                            title = note.title,
                            body = note.body,
                            color = NoteColor.fromName(note.color).name,
                            pinnedToTop = note.pinnedToTop,
                            pinnedToNotification = note.pinnedToNotification,
                            state = state.name,
                            position = if (mode == RestoreMode.MERGE) offset + (note.position - minPosition) else note.position,
                            createdAt = note.createdAt,
                            updatedAt = note.updatedAt,
                            trashedAt = if (state == NoteState.TRASHED) note.trashedAt ?: note.updatedAt else null,
                        )
                    },
                )
                labels.insertCrossRefs(
                    chunk.zip(ids).flatMap { (note, id) ->
                        note.labelNames.map { NoteLabelCrossRef(noteId = id, labelId = labelIds.getValue(it)) }
                    },
                )
            }
            toInsert.size
        }
    }

    private companion object {
        const val CHUNK = 500
    }
}
