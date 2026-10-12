package dev.aliarslan.musvedde.data.backup.drive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aliarslan.musvedde.data.backup.BackupAuthException
import dev.aliarslan.musvedde.data.backup.BackupFile
import dev.aliarslan.musvedde.data.backup.BackupTarget
import dev.aliarslan.musvedde.data.backup.BackupTargetUnavailable
import dev.aliarslan.musvedde.data.backup.SilentBackupSource
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveBackupTarget @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val auth: DriveAuth,
    private val api: DriveApi,
) : BackupTarget, SilentBackupSource {

    override val id = BackupTarget.DRIVE

    override suspend fun isReady(): Boolean = settings.settings.first().driveEnabled

    override suspend fun write(name: String, writer: suspend (OutputStream) -> Unit) {
        val token = token()
        val temp = withContext(Dispatchers.IO) { File.createTempFile("drive", ".zip", context.cacheDir) }
        try {
            temp.outputStream().buffered().use { writer(it) }
            api.upload(token, name, temp)
        } finally {
            withContext(Dispatchers.IO) { temp.delete() }
        }
    }

    override suspend fun list(): List<BackupFile> = api.list(token())

    override suspend fun open(file: BackupFile): InputStream = api.download(token(), file.ref)

    override suspend fun delete(file: BackupFile) = api.delete(token(), file.ref)

    override suspend fun listSilently(): List<BackupFile>? {
        val token = (auth.authorize() as? DriveAuthResult.Token)?.accessToken ?: return null
        return api.list(token)
    }

    override suspend fun openSilently(file: BackupFile): InputStream {
        val result = auth.authorize()
        val token = (result as? DriveAuthResult.Token)?.accessToken
            ?: throw BackupAuthException("Google Drive authorization is no longer available")
        return api.download(token, file.ref)
    }

    private suspend fun token(): String {
        if (!settings.settings.first().driveEnabled) throw BackupTargetUnavailable("Google Drive backup is turned off")
        return when (val result = auth.authorize()) {
            is DriveAuthResult.Token -> result.accessToken
            is DriveAuthResult.NeedsConsent -> throw BackupAuthException("Google Drive needs to be reconnected")
            is DriveAuthResult.Failed -> throw BackupAuthException("Google Drive authorization failed: ${result.error.message}", result.error)
        }
    }
}
