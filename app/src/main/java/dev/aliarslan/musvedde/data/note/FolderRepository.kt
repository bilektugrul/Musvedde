package dev.aliarslan.musvedde.data.note

import androidx.room.withTransaction
import dev.aliarslan.musvedde.data.db.FolderEntity
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.toDomain
import dev.aliarslan.musvedde.di.IoDispatcher
import dev.aliarslan.musvedde.domain.Folder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val db: MusveddeDatabase,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = db.folderDao()
    private val notes = db.noteDao()

    fun observeAll(): Flow<ImmutableList<Folder>> =
        dao.observeAll().map { list -> list.map { it.toDomain() }.toImmutableList() }.flowOn(io)

    fun observe(id: Long): Flow<Folder?> = dao.observe(id).map { it?.toDomain() }.distinctUntilChanged().flowOn(io)

    suspend fun create(name: String, locked: Boolean = false): Result<Long> = withContext(io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("blank folder"))
        runCatching { dao.insert(FolderEntity(name = trimmed, locked = locked)) }
    }

    suspend fun rename(id: Long, name: String): Result<Unit> = withContext(io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("blank folder"))
        runCatching { dao.rename(id, trimmed) }
    }

    suspend fun setLocked(id: Long, locked: Boolean) = withContext(io) {
        db.withTransaction {
            dao.setLocked(id, locked)
            if (locked) notes.unpinNotificationsInFolder(id)
        }
    }

    /** Deletes the folder; its notes stay and simply leave the folder. */
    suspend fun delete(id: Long) = withContext(io) {
        db.withTransaction {
            notes.clearFolder(id)
            dao.delete(id)
        }
    }
}
