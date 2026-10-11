package dev.aliarslan.musvedde.data.backup

import java.io.InputStream
import java.io.OutputStream

data class BackupFile(val name: String, val sizeBytes: Long, val modifiedAt: Long, val ref: String)

class BackupTargetUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

interface BackupTarget {
    val id: String

    suspend fun isReady(): Boolean

    suspend fun write(name: String, writer: suspend (OutputStream) -> Unit)

    suspend fun list(): List<BackupFile>

    suspend fun open(file: BackupFile): InputStream

    suspend fun delete(file: BackupFile)

    companion object {
        const val FOLDER = "folder"
        const val DRIVE = "drive"
    }
}
