package dev.aliarslan.musvedde.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps what the web editor sees and numbers every change, so a browser can ask only for what changed since the
 * revision it has. Revisions start at [firstRevision], which should grow across sessions: a browser holding a
 * revision from an earlier session then gets a full reload instead of a wrong diff.
 */
class WebNoteSync(private val firstRevision: Long, private val logSize: Int = 5_000) {
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private var current: Map<Long, WebNote> = emptyMap()
    private val log = ArrayDeque<Pair<Long, Long>>()
    private var floor = firstRevision
    private val _revision = MutableStateFlow(firstRevision)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    suspend fun follow(notes: Flow<List<WebNote>>) = notes.collect { apply(it) }

    suspend fun apply(notes: List<WebNote>) = mutex.withLock {
        val next = notes.associateBy { it.id }
        if (!ready.isCompleted) {
            current = next
            ready.complete(Unit)
            return@withLock
        }
        val changed = next.filter { (id, note) -> current[id] != note }.keys + current.keys.filter { it !in next }
        if (changed.isEmpty()) return@withLock
        val revision = _revision.value + 1
        changed.forEach { log.addLast(revision to it) }
        while (log.size > logSize) floor = maxOf(floor, log.removeFirst().first)
        current = next
        _revision.value = revision
    }

    suspend fun snapshot(): Pair<List<WebNote>, Long> {
        ready.await()
        return mutex.withLock { current.values.toList() to _revision.value }
    }

    /** The [ids] the web editor may touch; anything in the secret folder or gone is dropped. */
    suspend fun visible(ids: List<Long>): List<Long> {
        ready.await()
        return mutex.withLock { ids.filter { it in current } }
    }

    /** What changed after [revision], or null when that revision is unknown and the browser must reload. */
    suspend fun since(revision: Long): NotesSince? {
        ready.await()
        return mutex.withLock {
            if (revision < floor || revision > _revision.value) return@withLock null
            val ids = log.filter { it.first > revision }.map { it.second }.toSet()
            NotesSince(
                notes = ids.mapNotNull { current[it] },
                deletedIds = ids.filter { it !in current },
                revision = _revision.value,
            )
        }
    }
}
