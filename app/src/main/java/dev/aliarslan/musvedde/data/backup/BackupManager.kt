package dev.aliarslan.musvedde.data.backup

import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.core.ErrorLogger
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import dev.aliarslan.musvedde.domain.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed interface BackupRunResult {
    data object Skipped : BackupRunResult
    data object NoTargets : BackupRunResult
    data class Done(val succeeded: List<String>, val failed: Map<String, Throwable>) : BackupRunResult
}

@Singleton
class BackupManager @Inject constructor(
    private val targets: Set<@JvmSuppressWildcards BackupTarget>,
    private val serializer: BackupSerializer,
    private val notes: NoteRepository,
    private val settings: SettingsRepository,
    private val logger: ErrorLogger,
    private val clock: Clock,
    @param:Named(CACHE_DIR) private val cacheDir: File,
    @param:Named(SAFETY_DIR) private val safetyDir: File,
) {
    private val running = Mutex()

    fun target(id: String): BackupTarget? = targets.firstOrNull { it.id == id }

    suspend fun readyTargets(): List<BackupTarget> = targets.filter { it.isReady() }

    suspend fun backupNow(scheduled: Boolean): BackupRunResult = running.withLock {
        val current = settings.settings.first()
        val ready = readyTargets()
        if (ready.isEmpty()) return BackupRunResult.NoTargets

        if (scheduled) {
            val since = ready.minOf { current.lastSuccess(it.id) ?: 0L }
            if (since > 0 && !notes.hasChangesSince(since)) return BackupRunResult.Skipped
        }

        val now = clock.now()
        val name = BackupSerializer.fileName(now)
        val temp = withContext(Dispatchers.IO) { File.createTempFile("backup", ".zip", cacheDir) }
        try {
            try {
                temp.outputStream().buffered().use { serializer.write(it) }
            } catch (e: BackupKeyUnavailableException) {
                ready.forEach { record(it.id, at = null, error = e.message) }
                logger.error("Backup", e.message.orEmpty(), e)
                return BackupRunResult.Done(emptyList(), ready.associate { it.id to e })
            }
            val succeeded = mutableListOf<String>()
            val failed = linkedMapOf<String, Throwable>()
            for (target in ready) {
                try {
                    target.write(name) { out -> withContext(Dispatchers.IO) { temp.inputStream().use { it.copyTo(out) } } }
                    applyRetention(target, current.backupRetention)
                    record(target.id, at = now, error = null)
                    succeeded += target.id
                } catch (e: Exception) {
                    record(target.id, at = null, error = e.message ?: e.javaClass.simpleName)
                    logger.error(sourceOf(target.id), "Backup to ${target.id} failed: ${e.message}", e)
                    failed[target.id] = e
                }
            }
            BackupRunResult.Done(succeeded, failed)
        } finally {
            withContext(Dispatchers.IO) { temp.delete() }
        }
    }

    suspend fun safetyBackup(): File = running.withLock {
        withContext(Dispatchers.IO) {
            safetyDir.mkdirs()
            val file = File(safetyDir, BackupSerializer.fileName(clock.now()))
            file.outputStream().buffered().use { serializer.write(it) }
            safetyDir.listFiles { f -> f.name.startsWith(BackupSerializer.FILE_PREFIX) }
                .orEmpty()
                .sortedByDescending { it.name }
                .drop(SAFETY_KEEP)
                .forEach { it.delete() }
            file
        }
    }

    private suspend fun applyRetention(target: BackupTarget, keep: Int) {
        target.list()
            .filter { it.name.startsWith(BackupSerializer.FILE_PREFIX) && it.name.endsWith(".zip") }
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { target.delete(it) }
    }

    private suspend fun record(targetId: String, at: Long?, error: String?) {
        settings.update { s ->
            when (targetId) {
                BackupTarget.FOLDER -> s.copy(
                    lastFolderBackupAt = at ?: s.lastFolderBackupAt,
                    lastFolderBackupError = error,
                )
                BackupTarget.DRIVE -> s.copy(
                    lastDriveBackupAt = at ?: s.lastDriveBackupAt,
                    lastDriveBackupError = error,
                )
                else -> s
            }
        }
    }

    private fun Settings.lastSuccess(targetId: String): Long? = when (targetId) {
        BackupTarget.FOLDER -> lastFolderBackupAt
        BackupTarget.DRIVE -> lastDriveBackupAt
        else -> null
    }

    companion object {
        const val CACHE_DIR = "backupCacheDir"
        const val SAFETY_DIR = "safetyBackupDir"
        private const val SAFETY_KEEP = 3

        fun sourceOf(targetId: String) = if (targetId == BackupTarget.DRIVE) "Drive" else "Backup"
    }
}
