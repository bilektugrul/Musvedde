package dev.aliarslan.musvedde.data.backup

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.aliarslan.musvedde.domain.BackupFrequency
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BackupScheduler @Inject constructor(private val workManager: WorkManager) {

    fun apply(frequency: BackupFrequency, driveEnabled: Boolean) {
        val days = when (frequency) {
            BackupFrequency.OFF -> {
                workManager.cancelUniqueWork(PERIODIC)
                return
            }
            BackupFrequency.DAILY -> 1L
            BackupFrequency.WEEKLY -> 7L
            BackupFrequency.MONTHLY -> 30L
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS)
            .setConstraints(constraints(driveEnabled))
            .setInputData(workDataOf(BackupWorker.KEY_SCHEDULED to true))
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun runNow() {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(workDataOf(BackupWorker.KEY_SCHEDULED to false))
            .build()
        workManager.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    private fun constraints(driveEnabled: Boolean) = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiredNetworkType(if (driveEnabled) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
        .build()

    companion object {
        const val PERIODIC = "backup-periodic"
        const val NOW = "backup-now"
    }
}
