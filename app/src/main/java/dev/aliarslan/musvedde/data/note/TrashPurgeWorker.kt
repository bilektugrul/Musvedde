package dev.aliarslan.musvedde.data.note

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TrashPurgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notes: NoteRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        notes.purgeTrashOlderThan(TRASH_DAYS)
        return Result.success()
    }

    companion object {
        const val TRASH_DAYS = 7
        private const val NAME = "trash-purge"

        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
