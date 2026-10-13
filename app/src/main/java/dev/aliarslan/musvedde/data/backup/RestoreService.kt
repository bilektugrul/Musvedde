package dev.aliarslan.musvedde.data.backup

import androidx.room.withTransaction
import dev.aliarslan.musvedde.data.db.LabelEntity
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.NoteContentKey
import dev.aliarslan.musvedde.data.db.NoteEntity
import dev.aliarslan.musvedde.data.db.NoteLabelCrossRef
import dev.aliarslan.musvedde.data.db.NoteVersionEntity
import dev.aliarslan.musvedde.data.db.utf8Size
import dev.aliarslan.musvedde.data.lock.PinKdf
import dev.aliarslan.musvedde.data.note.Positions
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

class RestoreService @Inject constructor(
    private val db: MusveddeDatabase,
    private val serializer: BackupSerializer,
) {
    suspend fun restoreFrom(input: InputStream, mode: RestoreMode, pin: String? = null): Result<Int> =
        runCatching { serializer.read(input) }.fold(
            onSuccess = { restore(it, mode, pin) },
            onFailure = { Result.failure(it) },
        )

    /**
     * Restores [content]. A backup with secret notes needs the [pin] it was made with: without it the result is
     * [BackupPinRequiredException], with the wrong one [WrongBackupPinException], and nothing is changed. Every
     * note from the vault goes back into the secret folder.
     */
    suspend fun restore(content: BackupContent, mode: RestoreMode, pin: String? = null): Result<Int> = runCatching {
        val lockedNotes = content.vault?.let { vault ->
            if (pin == null) throw BackupPinRequiredException()
            val key = withContext(Dispatchers.Default) { PinKdf.derive(pin, vault.salt.unbase64(), vault.iterations) }
            serializer.openVault(vault, key).map { it.copy(locked = true) }
        }.orEmpty()
        val allNotes = content.notes + lockedNotes
        db.withTransaction {
            val notes = db.noteDao()
            val labels = db.labelDao()
            if (mode == RestoreMode.REPLACE) {
                notes.deleteAll()
                labels.deleteAll()
            }

            val labelIds = labels.all().associate { it.name to it.id }.toMutableMap()

            val wantedNames = content.labels.map { it.name } + allNotes.flatMap { it.labelNames }
            wantedNames.distinct().filter { it !in labelIds }.forEach { name ->
                labelIds[name] = labels.insert(LabelEntity(name = name))
            }

            val existing = if (mode == RestoreMode.MERGE) notes.contentKeys().toHashSet() else hashSetOf()
            val offset = if (mode == RestoreMode.MERGE) (notes.maxPosition() ?: 0) + Positions.STEP else 0
            val toInsert = allNotes.filter { NoteContentKey(it.title, it.body, it.createdAt) !in existing }
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
                            pinnedToNotification = note.pinnedToNotification && !note.locked,
                            state = state.name,
                            position = if (mode == RestoreMode.MERGE) offset + (note.position - minPosition) else note.position,
                            createdAt = note.createdAt,
                            updatedAt = note.updatedAt,
                            trashedAt = if (state == NoteState.TRASHED) note.trashedAt ?: note.updatedAt else null,
                            hidden = note.hidden,
                            locked = note.locked,
                        )
                    },
                )
                labels.insertCrossRefs(
                    chunk.zip(ids).flatMap { (note, id) ->
                        note.labelNames.map { NoteLabelCrossRef(noteId = id, labelId = labelIds.getValue(it)) }
                    },
                )
                db.noteVersionDao().insertAll(
                    chunk.zip(ids).flatMap { (note, id) ->
                        note.versions.map {
                            NoteVersionEntity(
                                noteId = id,
                                title = it.title,
                                body = it.body,
                                createdAt = it.createdAt,
                                sizeBytes = utf8Size(it.title, it.body),
                            )
                        }
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
