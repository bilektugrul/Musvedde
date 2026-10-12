package dev.aliarslan.musvedde.data.backup

import dev.aliarslan.musvedde.core.ErrorLogger
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import dev.aliarslan.musvedde.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A backup location that can be read without showing the user any UI. */
interface SilentBackupSource {
    /** Returns the backups, or null if access would need the user's consent. */
    suspend fun listSilently(): List<BackupFile>?

    suspend fun openSilently(file: BackupFile): InputStream
}

sealed interface AutoRestoreState {
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : AutoRestoreState
    data object Restoring : AutoRestoreState
    data class Done(val noteCount: Int, val backupName: String, val backupTime: Long) : AutoRestoreState
    data object Failed : AutoRestoreState
}

/** On the first launch of a fresh install, quietly brings back the newest Drive backup if Drive is already authorized. */
@Singleton
class AutoRestore @Inject constructor(
    private val settings: SettingsRepository,
    private val db: MusveddeDatabase,
    private val source: SilentBackupSource,
    private val serializer: BackupSerializer,
    private val restoreService: RestoreService,
    private val logger: ErrorLogger,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AutoRestoreState?>(null)
    val state: StateFlow<AutoRestoreState?> = _state.asStateFlow()

    fun dismiss() {
        _state.value = null
    }

    suspend fun runOnFirstLaunch() {
        if (settings.settings.first().autoRestoreChecked) return
        settings.update { it.copy(autoRestoreChecked = true) }
        if (db.noteDao().count() > 0) return

        val latest = runCatching { source.listSilently() }
            .onFailure { logger.warning("Drive", "Could not look for a backup to restore", it) }
            .getOrNull()
            ?.filter { it.name.startsWith(BackupSerializer.FILE_PREFIX) }
            ?.maxByOrNull { it.name }
            ?: return

        _state.value = AutoRestoreState.Downloading(0, latest.sizeBytes)
        val result = runCatching {
            val content = source.openSilently(latest).use { raw ->
                val counting = ProgressInputStream(raw) { read -> _state.value = AutoRestoreState.Downloading(read, latest.sizeBytes) }
                withContext(Dispatchers.IO) { serializer.read(counting) }
            }
            _state.value = AutoRestoreState.Restoring
            restoreService.restore(content, RestoreMode.MERGE).getOrThrow()
        }
        result
            .onSuccess { count ->
                settings.update { it.copy(driveEnabled = true, lastDriveBackupError = null) }
                logger.info("Drive", "Restored $count notes from ${latest.name}")
                finish(AutoRestoreState.Done(count, latest.name, latest.modifiedAt))
            }
            .onFailure {
                logger.error("Drive", "Restoring the latest backup failed: ${it.message}", it)
                finish(AutoRestoreState.Failed)
            }
    }

    private fun finish(final: AutoRestoreState) {
        _state.value = final
        scope.launch {
            delay(HIDE_AFTER_MILLIS)
            _state.compareAndSet(final, null)
        }
    }

    private class ProgressInputStream(input: InputStream, private val onProgress: (Long) -> Unit) : FilterInputStream(input) {
        private var read = 0L
        private var reported = 0L

        override fun read(): Int = super.read().also { if (it >= 0) advance(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) advance(it.toLong()) }

        private fun advance(count: Long) {
            read += count
            if (read - reported >= REPORT_EVERY_BYTES) {
                reported = read
                onProgress(read)
            }
        }
    }

    companion object {
        const val HIDE_AFTER_MILLIS = 30_000L
        private const val REPORT_EVERY_BYTES = 32 * 1024L
    }
}
