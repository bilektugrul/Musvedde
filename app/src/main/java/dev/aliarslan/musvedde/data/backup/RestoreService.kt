package dev.aliarslan.musvedde.data.backup

import androidx.room.withTransaction
import dev.aliarslan.musvedde.data.db.FolderEntity
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
     * Restores [content]. A backup with locked notes needs the [pin] it was made with: without it the result is
     * [BackupPinRequiredException], with the wrong one [WrongBackupPinException], and nothing is changed.
     */
    suspend fun restore(content: BackupContent, mode: RestoreMode, pin: String? = null): Result<Int> = runCatching {
        val lockedNotes = content.vault?.let { vault ->
            if (pin == null) throw BackupPinRequiredException()
            val key = withContext(Dispatchers.Default) { PinKdf.derive(pin, vault.salt.unbase64(), vault.iterations) }
            serializer.openVault(vault, key)
        }.orEmpty()
        val allNotes = content.notes + lockedNotes
        db.withTransaction {
            val notes = db.noteDao()
            val labels = db.labelDao()
            val folders = db.folderDao()
            if (mode == RestoreMode.REPLACE) {
                notes.deleteAll()
                labels.deleteAll()
                folders.deleteAll()
            }

            val labelIds = labels.all().associate { it.name to it.id }.toMutableMap()
            val folderIds = folders.all().associate { it.name to it.id }.toMutableMap()
            val lockedFolders = content.folders.filter { it.locked }.map { it.name }.toSet()
            (content.folders.map { it.name } + allNotes.mapNotNull { it.folderName }).distinct().forEach { name ->
                val existing = folderIds[name]
                if (existing == null) {
                    folderIds[name] = folders.insert(FolderEntity(name = name, locked = name in lockedFolders))
                } else if (name in lockedFolders) {
                    folders.setLocked(existing, true)
                }
            }

            val lockedFolderIds = folders.all().filter { it.locked }.map { it.id }.toSet()

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
                        val folderId = note.folderName?.let(folderIds::get)
                        NoteEntity(
                            title = note.title,
                            body = note.body,
                            color = NoteColor.fromName(note.color).name,
                            pinnedToTop = note.pinnedToTop,
                            pinnedToNotification = note.pinnedToNotification && !note.locked && folderId !in lockedFolderIds,
                            state = state.name,
                            position = if (mode == RestoreMode.MERGE) offset + (note.position - minPosition) else note.position,
                            createdAt = note.createdAt,
                            updatedAt = note.updatedAt,
                            trashedAt = if (state == NoteState.TRASHED) note.trashedAt ?: note.updatedAt else null,
                            folderId = folderId,
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
