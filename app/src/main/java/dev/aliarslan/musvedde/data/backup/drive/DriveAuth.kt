package dev.aliarslan.musvedde.data.backup.drive

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DriveAuthResult {
    data class Token(val accessToken: String) : DriveAuthResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthResult
    data class Failed(val error: Throwable) : DriveAuthResult
}

@Singleton
class DriveAuth @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        .build()

    suspend fun authorize(): DriveAuthResult = try {
        toResult(Identity.getAuthorizationClient(context).authorize(request).await())
    } catch (e: Exception) {
        DriveAuthResult.Failed(e)
    }

    fun toResult(result: AuthorizationResult): DriveAuthResult {
        val pendingIntent = result.pendingIntent
        val token = result.accessToken
        return when {
            result.hasResolution() && pendingIntent != null -> DriveAuthResult.NeedsConsent(pendingIntent)
            token != null -> DriveAuthResult.Token(token)
            else -> DriveAuthResult.Failed(IllegalStateException("No access token returned"))
        }
    }

    fun resultFromConsent(data: android.content.Intent?): DriveAuthResult = try {
        toResult(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: Exception) {
        DriveAuthResult.Failed(e)
    }

    private companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
