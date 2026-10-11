package dev.aliarslan.musvedde.data.storage

import dev.aliarslan.musvedde.data.backup.BackupManager
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.NoteStorageRow
import dev.aliarslan.musvedde.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named

fun interface DatabaseFiles {
    fun files(): List<File>
}

data class StorageReport(
    val databaseBytes: Long,
    val safetyBackupBytes: Long,
    val noteCount: Int,
    val noteBytes: Long,
    val versionCount: Int,
    val versionBytes: Long,
    val notes: List<NoteStorageRow>,
) {
    val totalOnDiskBytes: Long get() = databaseBytes + safetyBackupBytes
}

class StorageRepository @Inject constructor(
    private val db: MusveddeDatabase,
    private val databaseFiles: DatabaseFiles,
    @param:Named(BackupManager.SAFETY_DIR) private val safetyBackupDir: File,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun report(): StorageReport = withContext(io) {
        val rows = db.noteDao().storageRows()
        val versions = db.noteVersionDao().totals()
        StorageReport(
            databaseBytes = databaseFiles.files().sumOf { if (it.isFile) it.length() else 0L },
            safetyBackupBytes = safetyBackupDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
            noteCount = rows.size,
            noteBytes = rows.sumOf { it.noteBytes },
            versionCount = versions.count,
            versionBytes = versions.bytes,
            notes = rows,
        )
    }
}
