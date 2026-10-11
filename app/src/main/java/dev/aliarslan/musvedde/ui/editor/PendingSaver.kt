package dev.aliarslan.musvedde.ui.editor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

data class Draft(val title: String, val body: String)

class PendingSaver(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 300,
    private val save: suspend (Draft) -> Unit,
) {
    private val pending = AtomicReference<Draft?>(null)
    private val saving = Mutex()
    private var timer: Job? = null

    fun submit(draft: Draft) {
        pending.set(draft)
        timer?.cancel()
        // The save runs in its own job so that a newer submit cancelling the timer never cancels a write in progress.
        timer = scope.launch {
            delay(debounceMs)
            scope.launch { drain() }
        }
    }

    fun flush(): Job {
        timer?.cancel()
        return scope.launch { drain() }
    }

    private suspend fun drain() = saving.withLock {
        val draft = pending.getAndSet(null) ?: return@withLock
        save(draft)
    }
}
