package dev.aliarslan.musvedde.data.errorlog

import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.core.ErrorLogger
import dev.aliarslan.musvedde.data.db.ErrorLogDao
import dev.aliarslan.musvedde.data.db.ErrorLogEntity
import dev.aliarslan.musvedde.data.db.toDomain
import dev.aliarslan.musvedde.di.ApplicationScope
import dev.aliarslan.musvedde.di.CoreModule
import dev.aliarslan.musvedde.domain.ErrorEntry
import dev.aliarslan.musvedde.domain.ErrorLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ErrorLogRepository @Inject constructor(
    private val dao: ErrorLogDao,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:Named(CoreModule.PENDING_CRASH_FILE) private val pendingCrashFile: File,
) : ErrorLogger {

    fun observe(): Flow<List<ErrorEntry>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun error(source: String, message: String, throwable: Throwable?) =
        launchRecord(ErrorLevel.ERROR, source, message, throwable)

    override fun warning(source: String, message: String, throwable: Throwable?) =
        launchRecord(ErrorLevel.WARNING, source, message, throwable)

    override fun info(source: String, message: String) =
        launchRecord(ErrorLevel.INFO, source, message, null)

    suspend fun record(level: ErrorLevel, source: String, message: String, throwable: Throwable?) {
        insert(
            ErrorLogEntity(
                timestamp = clock.now(),
                level = level.name,
                source = source,
                message = message,
                stackTrace = throwable?.stackTraceToString(),
            ),
        )
    }

    suspend fun importPendingCrash() {
        val crash = CrashRecorder(pendingCrashFile, clock).readAndDelete() ?: return
        insert(
            ErrorLogEntity(
                timestamp = crash.timestamp,
                level = crash.level.name,
                source = crash.source,
                message = crash.message,
                stackTrace = crash.stackTrace,
            ),
        )
    }

    suspend fun clear() = dao.clear()

    private fun launchRecord(level: ErrorLevel, source: String, message: String, throwable: Throwable?) {
        scope.launch { record(level, source, message, throwable) }
    }

    private suspend fun insert(entity: ErrorLogEntity) {
        dao.insert(entity)
        dao.pruneTo(MAX_ENTRIES)
    }

    companion object {
        const val MAX_ENTRIES = 500
    }
}
