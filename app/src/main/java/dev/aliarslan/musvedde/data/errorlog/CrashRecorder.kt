package dev.aliarslan.musvedde.data.errorlog

import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.domain.ErrorEntry
import dev.aliarslan.musvedde.domain.ErrorLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class CrashRecorder(private val file: File, private val clock: Clock) {

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun write(thread: Thread, throwable: Throwable) {
        val crash = PendingCrash(
            timestamp = clock.now(),
            thread = thread.name,
            message = throwable.message ?: throwable.javaClass.name,
            stackTrace = throwable.stackTraceToString(),
        )
        file.writeText(Json.encodeToString(PendingCrash.serializer(), crash))
    }

    fun readAndDelete(): ErrorEntry? {
        if (!file.exists()) return null
        val crash = runCatching { Json.decodeFromString(PendingCrash.serializer(), file.readText()) }.getOrNull()
        file.delete()
        return crash?.let {
            ErrorEntry(
                id = 0,
                timestamp = it.timestamp,
                level = ErrorLevel.ERROR,
                source = SOURCE,
                message = it.message,
                stackTrace = "Thread: ${it.thread}\n${it.stackTrace}",
            )
        }
    }

    @Serializable
    private data class PendingCrash(
        val timestamp: Long,
        val thread: String,
        val message: String,
        val stackTrace: String,
    )

    companion object {
        const val SOURCE = "Crash"
    }
}
