package dev.aliarslan.musvedde.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.domain.BackupFrequency
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class BackupSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: BackupScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = BackupScheduler(workManager)
    }

    private fun periodic() = workManager.getWorkInfosForUniqueWork(BackupScheduler.PERIODIC).get()
        .filter { it.state != WorkInfo.State.CANCELLED }

    @Test
    fun weeklyEnqueuesSevenDayPeriod() {
        scheduler.apply(BackupFrequency.WEEKLY, driveEnabled = false)

        val info = periodic().single()
        assertThat(info.periodicityInfo!!.repeatIntervalMillis).isEqualTo(TimeUnit.DAYS.toMillis(7))
        assertThat(info.constraints.requiredNetworkType).isEqualTo(NetworkType.NOT_REQUIRED)
        assertThat(info.constraints.requiresBatteryNotLow()).isTrue()
    }

    @Test
    fun monthlyWithDriveNeedsNetwork() {
        scheduler.apply(BackupFrequency.MONTHLY, driveEnabled = true)

        val info = periodic().single()
        assertThat(info.periodicityInfo!!.repeatIntervalMillis).isEqualTo(TimeUnit.DAYS.toMillis(30))
        assertThat(info.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun offCancels() {
        scheduler.apply(BackupFrequency.DAILY, driveEnabled = false)

        scheduler.apply(BackupFrequency.OFF, driveEnabled = false)

        assertThat(periodic()).isEmpty()
    }
}
