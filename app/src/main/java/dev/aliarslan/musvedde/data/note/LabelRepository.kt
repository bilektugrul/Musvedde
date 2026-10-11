package dev.aliarslan.musvedde.data.note

import dev.aliarslan.musvedde.data.db.LabelDao
import dev.aliarslan.musvedde.data.db.LabelEntity
import dev.aliarslan.musvedde.data.db.toDomain
import dev.aliarslan.musvedde.di.IoDispatcher
import dev.aliarslan.musvedde.domain.Label
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabelRepository @Inject constructor(
    private val dao: LabelDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun observeAll(): Flow<ImmutableList<Label>> =
        dao.observeAll().map { list -> list.map { it.toDomain() }.toImmutableList() }.flowOn(io)

    suspend fun create(name: String): Result<Long> = withContext(io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("blank label"))
        runCatching { dao.insert(LabelEntity(name = trimmed)) }
    }

    suspend fun rename(id: Long, name: String): Result<Unit> = withContext(io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("blank label"))
        runCatching { dao.rename(id, trimmed) }
    }

    suspend fun delete(id: Long) = withContext(io) { dao.delete(id) }

    suspend fun setLabel(noteIds: List<Long>, labelId: Long, add: Boolean) = withContext(io) {
        if (add) dao.addToNotes(labelId, noteIds) else dao.removeFromNotes(labelId, noteIds)
    }
}
