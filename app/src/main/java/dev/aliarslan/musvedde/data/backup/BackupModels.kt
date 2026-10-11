package dev.aliarslan.musvedde.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val formatVersion: Int = BackupSerializer.FORMAT_VERSION,
    val appVersion: String,
    val createdAt: Long,
    val noteCount: Int,
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
)

@Serializable
data class BackupLabel(val name: String)

@Serializable
data class BackupData(
    val labels: List<BackupLabel>,
    val notes: List<BackupNote>,
)

data class BackupContent(
    val manifest: BackupManifest,
    val labels: List<BackupLabel>,
    val notes: List<BackupNote>,
)

enum class RestoreMode { MERGE, REPLACE }

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
