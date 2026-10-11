package dev.aliarslan.musvedde.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val manager: BackupManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = manager.backupNow(scheduled = inputData.getBoolean(KEY_SCHEDULED, false))
        if (result !is BackupRunResult.Done || result.failed.isEmpty()) return Result.success()
        val transient = result.failed.values.any { it is IOException && it !is BackupAuthException }
        return if (transient && runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_SCHEDULED = "scheduled"
        private const val MAX_ATTEMPTS = 3
    }
}

class BackupAuthException(message: String, cause: Throwable? = null) : IOException(message, cause)
