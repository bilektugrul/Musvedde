package dev.aliarslan.musvedde.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderBackupTarget @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : BackupTarget {

    override val id = BackupTarget.FOLDER

    override suspend fun isReady(): Boolean = folder() != null

    override suspend fun write(name: String, writer: suspend (OutputStream) -> Unit) {
        val dir = folder() ?: throw BackupTargetUnavailable("Backup folder is not set or access was revoked")
        val file = withContext(Dispatchers.IO) { dir.createFile(MIME, name) }
            ?: throw BackupTargetUnavailable("Could not create $name in the backup folder")
        try {
            val out = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(file.uri, "w") }
                ?: throw BackupTargetUnavailable("Could not open $name for writing")
            out.use { writer(it) }
        } catch (e: Exception) {
            withContext(Dispatchers.IO) { file.delete() }
            throw e
        }
    }

    override suspend fun list(): List<BackupFile> = withContext(Dispatchers.IO) {
        val dir = folder() ?: return@withContext emptyList()
        dir.listFiles().filter { it.isFile }.map {
            BackupFile(it.name.orEmpty(), it.length(), it.lastModified(), it.uri.toString())
        }
    }

    override suspend fun open(file: BackupFile): InputStream = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(Uri.parse(file.ref))
            ?: throw BackupTargetUnavailable("Could not open ${file.name}")
    }

    override suspend fun delete(file: BackupFile) {
        withContext(Dispatchers.IO) { DocumentFile.fromSingleUri(context, Uri.parse(file.ref))?.delete() }
    }

    private suspend fun folder(): DocumentFile? {
        val uri = settings.settings.first().backupFolderUri?.let(Uri::parse) ?: return null
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        if (!granted) return null
        return withContext(Dispatchers.IO) {
            DocumentFile.fromTreeUri(context, uri)?.takeIf { it.exists() && it.canWrite() }
        }
    }

    private companion object {
        const val MIME = "application/zip"
    }
}
