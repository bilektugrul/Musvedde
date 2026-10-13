package dev.aliarslan.musvedde.data.note

import androidx.room.withTransaction
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.NoteEntity
import dev.aliarslan.musvedde.data.db.toDomain
import dev.aliarslan.musvedde.di.IoDispatcher
import dev.aliarslan.musvedde.domain.Note
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteState
import dev.aliarslan.musvedde.domain.NoteSummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface NoteFilter {
    /** Notes in [state]; the active notes leave out hidden ones, like the main page. */
    data class InState(val state: NoteState) : NoteFilter
    data class WithLabel(val labelId: Long) : NoteFilter
    data object Secret : NoteFilter
    /** Searches either the secret folder or everything else, never both. */
    data class Search(val query: String, val secret: Boolean = false) : NoteFilter
}

@Singleton
class NoteRepository @Inject constructor(
    private val db: MusveddeDatabase,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = db.noteDao()
    private val labelDao = db.labelDao()

    /** Secret notes are only listed by [NoteFilter.Secret], and in the archive or trash while [unlocked]. */
    fun observe(filter: NoteFilter, unlocked: Boolean = false): Flow<ImmutableList<NoteSummary>> {
        val rows = when (filter) {
            is NoteFilter.InState ->
                dao.observeSummaries(filter.state.name, mainPage = filter.state == NoteState.ACTIVE, unlocked = unlocked)
            is NoteFilter.WithLabel -> dao.observeSummariesByLabel(filter.labelId)
            NoteFilter.Secret -> dao.observeSecretSummaries()
            is NoteFilter.Search -> toFtsQuery(filter.query)?.let { dao.searchSummaries(it, filter.secret) }
                ?: return flowOf(persistentListOf())
        }
        return rows
            .map { list -> list.map { it.toDomain() }.toImmutableList() }
            .distinctUntilChanged()
            .flowOn(io)
    }

    fun observeIsLocked(id: Long): Flow<Boolean> =
        dao.observeIsLocked(id).map { it == true }.distinctUntilChanged().flowOn(io)

    fun observePinnedToNotification(): Flow<List<Note>> =
        dao.observePinnedToNotification()
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(io)

    suspend fun get(id: Long): Note? = withContext(io) {
        dao.getById(id)?.toDomain(labelDao.labelIdsFor(id))
    }

    suspend fun create(title: String, body: String, secret: Boolean = false): Long = withContext(io) {
        db.withTransaction {
            val now = clock.now()
            dao.insert(
                NoteEntity(
                    title = title,
                    body = body,
                    color = NoteColor.DEFAULT.name,
                    pinnedToTop = false,
                    pinnedToNotification = false,
                    state = NoteState.ACTIVE.name,
                    position = (dao.maxPosition() ?: 0) + Positions.STEP,
                    createdAt = now,
                    updatedAt = now,
                    trashedAt = null,
                    locked = secret,
                ),
            )
        }
    }

    suspend fun setHidden(ids: List<Long>, hidden: Boolean) = withContext(io) { dao.setHidden(ids, hidden) }

    suspend fun setLocked(ids: List<Long>, locked: Boolean) = withContext(io) { dao.setLocked(ids, locked) }

    suspend fun hasLockedNotes(): Boolean = withContext(io) { dao.countLocked() > 0 }

    suspend fun updateContent(id: Long, title: String, body: String) {
        withContext(io) { dao.updateContent(id, title, body, clock.now()) }
    }

    suspend fun setColor(ids: List<Long>, color: NoteColor) = withContext(io) {
        dao.setColor(ids, color.name, clock.now())
    }

    suspend fun setPinnedToTop(ids: List<Long>, pinned: Boolean) = withContext(io) {
        dao.setPinnedToTop(ids, pinned, clock.now())
    }

    suspend fun setPinnedToNotification(id: Long, pinned: Boolean) = withContext(io) {
        dao.setPinnedToNotification(id, pinned)
    }

    suspend fun archive(ids: List<Long>) = setState(ids, NoteState.ARCHIVED)

    suspend fun unarchive(ids: List<Long>) = setState(ids, NoteState.ACTIVE)

    suspend fun trash(ids: List<Long>) = setState(ids, NoteState.TRASHED)

    suspend fun restore(ids: List<Long>) = setState(ids, NoteState.ACTIVE)

    suspend fun deleteForever(ids: List<Long>) = withContext(io) { dao.deleteByIds(ids) }

    suspend fun emptyTrash() = withContext(io) { dao.deleteAllTrashed() }

    suspend fun purgeTrashOlderThan(days: Int = 7): Int = withContext(io) {
        dao.deleteTrashedBefore(clock.now() - days * DAY_MILLIS)
    }

    suspend fun move(id: Long, aboveId: Long?, belowId: Long?) = withContext(io) {
        db.withTransaction {
            val target = positionBetween(aboveId, belowId) ?: run {
                renumber()
                positionBetween(aboveId, belowId)
            }
            if (target != null) dao.setPosition(id, target)
        }
    }

    suspend fun deleteIfBlank(id: Long) = withContext(io) { dao.deleteIfBlank(id) }

    suspend fun hasChangesSince(timestamp: Long): Boolean = withContext(io) {
        dao.countChangedSince(timestamp) > 0
    }

    private suspend fun setState(ids: List<Long>, state: NoteState) = withContext(io) {
        val now = clock.now()
        dao.setState(ids, state.name, trashedAt = if (state == NoteState.TRASHED) now else null, updatedAt = now)
    }

    private suspend fun positionBetween(aboveId: Long?, belowId: Long?): Long? = Positions.between(
        above = aboveId?.let { dao.getById(it)?.position },
        below = belowId?.let { dao.getById(it)?.position },
    )

    private suspend fun renumber() {
        dao.idsByPositionAscending().forEachIndexed { index, noteId ->
            dao.setPosition(noteId, (index + 1) * Positions.STEP)
        }
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
