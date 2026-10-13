package dev.aliarslan.musvedde.data.backup

import dev.aliarslan.musvedde.domain.Settings
import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val formatVersion: Int = BackupSerializer.FORMAT_VERSION,
    val appVersion: String,
    val createdAt: Long,
    val noteCount: Int,
    val lockedNoteCount: Int = 0,
)

@Serializable
data class BackupNote(
    val title: String,
    val body: String,
    val color: String,
    val pinnedToTop: Boolean,
    val pinnedToNotification: Boolean,
    val state: String,
    val position: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long? = null,
    val labelNames: List<String> = emptyList(),
    val versions: List<BackupVersion> = emptyList(),
    val locked: Boolean = false,
)

@Serializable
data class BackupVersion(
    val title: String,
    val body: String,
    val createdAt: Long,
)

@Serializable
data class BackupLabel(val name: String)

@Serializable
data class BackupData(
    val labels: List<BackupLabel>,
    val notes: List<BackupNote>,
)

/** Secret folder notes, encrypted with a key derived from the PIN; [data] is AES-GCM over the JSON list of notes. */
@Serializable
data class BackupVault(
    val salt: String,
    val iterations: Int,
    val data: String,
)

data class BackupContent(
    val manifest: BackupManifest,
    val labels: List<BackupLabel>,
    val notes: List<BackupNote>,
    val vault: BackupVault? = null,
    val settings: Settings? = null,
)

/** Where backups take app settings from and put them back; the PIN and lock keys are never part of it. */
interface BackupSettingsStore {
    suspend fun current(): Settings?

    suspend fun restore(settings: Settings)

    companion object {
        val NONE = object : BackupSettingsStore {
            override suspend fun current(): Settings? = null
            override suspend fun restore(settings: Settings) = Unit
        }
    }
}

/** The backup has locked notes and the PIN was not given. */
class BackupPinRequiredException : Exception("This backup has locked notes; enter the PIN")

class WrongBackupPinException : Exception("The PIN does not open this backup's locked notes")

/** Locked notes exist but the key to encrypt them is not available until the PIN is entered once. */
class BackupKeyUnavailableException : Exception("Enter the PIN once so locked notes can be backed up")

enum class RestoreMode { MERGE, REPLACE }

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
