package dev.aliarslan.musvedde.ui.settings

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aliarslan.musvedde.core.ErrorLogger
import dev.aliarslan.musvedde.data.backup.BackupFile
import dev.aliarslan.musvedde.data.backup.BackupManager
import dev.aliarslan.musvedde.data.backup.BackupScheduler
import dev.aliarslan.musvedde.data.backup.BackupSerializer
import dev.aliarslan.musvedde.data.backup.BackupPinRequiredException
import dev.aliarslan.musvedde.data.backup.KeepImporter
import dev.aliarslan.musvedde.data.backup.WrongBackupPinException
import dev.aliarslan.musvedde.data.lock.LockManager
import dev.aliarslan.musvedde.data.backup.RestoreMode
import dev.aliarslan.musvedde.data.backup.RestoreService
import dev.aliarslan.musvedde.data.backup.drive.DriveAuth
import dev.aliarslan.musvedde.data.backup.drive.DriveAuthResult
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import dev.aliarslan.musvedde.di.ApplicationScope
import dev.aliarslan.musvedde.domain.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

data class RestoreCandidate(val targetId: String, val file: BackupFile)

sealed interface SettingsEvent {
    data class DriveConsent(val pendingIntent: PendingIntent) : SettingsEvent
    data object DriveConnected : SettingsEvent
    data class DriveFailed(val message: String) : SettingsEvent
    data class Restored(val count: Int) : SettingsEvent
    data class RestoreFailed(val message: String) : SettingsEvent
    data class KeepImported(val count: Int) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scheduler: BackupScheduler,
    private val backupManager: BackupManager,
    private val restoreService: RestoreService,
    private val keepImporter: KeepImporter,
    private val lockManager: LockManager,
    private val driveAuth: DriveAuth,
    private val logger: ErrorLogger,
    workManager: WorkManager,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val settings: StateFlow<Settings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val backupRunning: StateFlow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(BackupScheduler.NOW)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _restoreCandidates = MutableStateFlow<List<RestoreCandidate>?>(null)
    val restoreCandidates: StateFlow<List<RestoreCandidate>?> = _restoreCandidates.asStateFlow()

    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    /** Set while a backup with locked notes waits for its PIN; true once a wrong PIN was entered. */
    private val _backupPinRequest = MutableStateFlow<Boolean?>(null)
    val backupPinRequest: StateFlow<Boolean?> = _backupPinRequest.asStateFlow()

    private var pendingRestore: Pair<RestoreMode, suspend () -> InputStream>? = null

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    val retentionOptions: List<Int> = Settings.RETENTION_OPTIONS

    fun update(transform: (Settings) -> Settings) {
        appScope.launch {
            val before = settingsRepository.settings.first()
            settingsRepository.update(transform)
            val after = settingsRepository.settings.first()
            if (before.backupFrequency != after.backupFrequency || before.driveEnabled != after.driveEnabled) {
                scheduler.apply(after.backupFrequency, after.driveEnabled)
            }
        }
    }

    fun resetCategory(category: SettingsCategory) = update { category.reset(it) ?: it }

    fun folderName(uri: String?): String? = uri?.let {
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull()
    }

    fun setBackupFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val resolver = context.contentResolver
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
            .onFailure { logger.error("Backup", "Could not keep access to the backup folder", it) }
        appScope.launch {
            val previous = settingsRepository.settings.first().backupFolderUri
            settingsRepository.update { it.copy(backupFolderUri = uri.toString(), lastFolderBackupError = null) }
            if (previous != null && previous != uri.toString()) {
                runCatching { resolver.releasePersistableUriPermission(Uri.parse(previous), flags) }
            }
        }
    }

    fun connectDrive() {
        viewModelScope.launch { handleDrive(driveAuth.authorize()) }
    }

    fun onDriveConsentResult(data: Intent?) {
        viewModelScope.launch { handleDrive(driveAuth.resultFromConsent(data)) }
    }

    fun disconnectDrive() = update { it.copy(driveEnabled = false, lastDriveBackupError = null) }

    private suspend fun handleDrive(result: DriveAuthResult) {
        when (result) {
            is DriveAuthResult.Token -> {
                update { it.copy(driveEnabled = true, lastDriveBackupError = null) }
                eventChannel.send(SettingsEvent.DriveConnected)
            }
            is DriveAuthResult.NeedsConsent -> eventChannel.send(SettingsEvent.DriveConsent(result.pendingIntent))
            is DriveAuthResult.Failed -> {
                logger.error("Drive", "Connecting Google Drive failed: ${result.error.message}", result.error)
                eventChannel.send(SettingsEvent.DriveFailed(result.error.message ?: result.error.javaClass.simpleName))
            }
        }
    }

    fun backupNow() = scheduler.runNow()

    fun loadRestoreCandidates() {
        _restoreCandidates.value = null
        viewModelScope.launch {
            val candidates = backupManager.readyTargets().flatMap { target ->
                runCatching { target.list() }
                    .onFailure { logger.warning(BackupManager.sourceOf(target.id), "Listing backups failed", it) }
                    .getOrDefault(emptyList())
                    .filter { it.name.startsWith(BackupSerializer.FILE_PREFIX) }
                    .map { RestoreCandidate(target.id, it) }
            }
            _restoreCandidates.value = candidates.sortedByDescending { it.file.name }
        }
    }

    fun restore(candidate: RestoreCandidate, mode: RestoreMode) = restore(mode) {
        backupManager.target(candidate.targetId)!!.open(candidate.file)
    }

    fun restore(uri: Uri, mode: RestoreMode) = restore(mode) {
        withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) } ?: error("Could not open the file")
    }

    fun importKeep(uri: Uri) {
        _restoring.value = true
        appScope.launch {
            try {
                runCatching {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { keepImporter.read(it) }
                    } ?: error("Could not open the file")
                    restoreService.restore(content, RestoreMode.MERGE).getOrThrow()
                }
                    .onSuccess { eventChannel.send(SettingsEvent.KeepImported(it)) }
                    .onFailure {
                        logger.error("Keep", "Importing from Google Keep failed: ${it.message}", it)
                        eventChannel.send(SettingsEvent.RestoreFailed(it.message ?: it.javaClass.simpleName))
                    }
            } finally {
                _restoring.value = false
            }
        }
    }

    fun restoreWithPin(pin: String) {
        val (mode, open) = pendingRestore ?: return
        _backupPinRequest.value = null
        restore(mode, pin, open)
    }

    fun cancelBackupPin() {
        pendingRestore = null
        _backupPinRequest.value = null
    }

    private fun restore(mode: RestoreMode, pin: String? = null, open: suspend () -> InputStream) {
        _restoring.value = true
        appScope.launch {
            try {
                if (mode == RestoreMode.REPLACE && pin == null) backupManager.safetyBackup()
                val result = runCatching { open() }.fold(
                    onSuccess = { stream -> stream.use { restoreService.restoreFrom(it, mode, pin) } },
                    onFailure = { Result.failure(it) },
                )
                result
                    .onSuccess {
                        pendingRestore = null
                        if (pin != null && !lockManager.hasPin.first()) lockManager.setPin(pin)
                        eventChannel.send(SettingsEvent.Restored(it))
                    }
                    .onFailure {
                        when (it) {
                            is BackupPinRequiredException -> {
                                pendingRestore = mode to open
                                _backupPinRequest.value = false
                            }
                            is WrongBackupPinException -> {
                                pendingRestore = mode to open
                                _backupPinRequest.value = true
                            }
                            else -> {
                                logger.error("Restore", "Restore failed: ${it.message}", it)
                                eventChannel.send(SettingsEvent.RestoreFailed(it.message ?: it.javaClass.simpleName))
                            }
                        }
                    }
            } finally {
                _restoring.value = false
            }
        }
    }
}
