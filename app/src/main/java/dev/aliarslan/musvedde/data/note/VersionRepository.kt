package dev.aliarslan.musvedde.data.note

import androidx.room.withTransaction
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.NoteVersionEntity
import dev.aliarslan.musvedde.data.db.PendingVersionEntity
import dev.aliarslan.musvedde.data.db.utf8Size
import dev.aliarslan.musvedde.di.IoDispatcher
import dev.aliarslan.musvedde.domain.NoteVersion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one version per editing session: a session starts with the first save after a note is opened and
 * ends when the note is closed. A session that never ended (process death) is closed by [recoverPending].
 */
@Singleton
class VersionRepository @Inject constructor(
    private val db: MusveddeDatabase,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = db.noteVersionDao()
    private val notes = db.noteDao()

    fun observe(noteId: Long): Flow<List<NoteVersion>> =
        dao.observeForNote(noteId).map { list -> list.map { it.toDomain() } }.flowOn(io)

    suspend fun beginSession(noteId: Long, baselineTitle: String, baselineBody: String, baselineAt: Long) = withContext(io) {
        dao.insertPending(PendingVersionEntity(noteId, baselineTitle, baselineBody, baselineAt))
    }

    suspend fun endSession(noteId: Long) = withContext(io) {
        db.withTransaction { closeSession(noteId) }
    }

    suspend fun recoverPending() = withContext(io) {
        db.withTransaction {
            dao.allPending().forEach { closeSession(it.noteId) }
        }
    }

    suspend fun restore(versionId: Long) = withContext(io) {
        db.withTransaction {
            val version = dao.getById(versionId) ?: return@withTransaction
            val note = notes.getById(version.noteId) ?: return@withTransaction
            val now = clock.now()
            recordIfChanged(note.id, note.title, note.body, now)
            notes.updateContent(note.id, version.title, version.body, now)
            recordIfChanged(note.id, version.title, version.body, now)
        }
    }

    suspend fun hasVersions(noteId: Long): Boolean = withContext(io) { dao.countForNote(noteId) > 0 }

    suspend fun restoreLatest(noteId: Long) = withContext(io) {
        val latest = dao.latestForNote(noteId) ?: return@withContext
        notes.updateContent(noteId, latest.title, latest.body, clock.now())
    }

    suspend fun delete(versionId: Long) = withContext(io) { dao.delete(versionId) }

    suspend fun clearNote(noteId: Long) = withContext(io) { dao.deleteForNote(noteId) }

    suspend fun clearAll() = withContext(io) { dao.deleteAll() }

    private suspend fun closeSession(noteId: Long) {
        val pending = dao.pending(noteId) ?: return
        dao.deletePending(noteId)
        val note = notes.getById(noteId) ?: return
        if (note.title == pending.baselineTitle && note.body == pending.baselineBody) return
        if (dao.latestForNote(noteId) == null && (pending.baselineTitle.isNotBlank() || pending.baselineBody.isNotBlank())) {
            insert(noteId, pending.baselineTitle, pending.baselineBody, pending.baselineAt)
        }
        recordIfChanged(noteId, note.title, note.body, clock.now())
    }

    private suspend fun recordIfChanged(noteId: Long, title: String, body: String, at: Long) {
        if (title.isBlank() && body.isBlank()) return
        val latest = dao.latestForNote(noteId)
        if (latest != null && latest.title == title && latest.body == body) return
        insert(noteId, title, body, at)
    }

    private suspend fun insert(noteId: Long, title: String, body: String, at: Long) {
        dao.insert(NoteVersionEntity(noteId = noteId, title = title, body = body, createdAt = at, sizeBytes = utf8Size(title, body)))
    }

    private fun NoteVersionEntity.toDomain() = NoteVersion(id, noteId, title, body, createdAt, sizeBytes)
}
