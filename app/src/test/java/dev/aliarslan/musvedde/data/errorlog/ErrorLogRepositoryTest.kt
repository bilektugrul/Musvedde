package dev.aliarslan.musvedde.data.errorlog

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.domain.ErrorLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ErrorLogRepositoryTest {
    private val db = inMemoryDatabase()
    private var now = 1_000L
    private val pendingCrashFile = File.createTempFile("crash", ".json").apply { delete() }
    private val repository = ErrorLogRepository(
        dao = db.errorLogDao(),
        clock = { now++ },
        scope = CoroutineScope(Dispatchers.Unconfined),
        pendingCrashFile = pendingCrashFile,
    )

    @After
    fun tearDown() = db.close()

    @Test
    fun errorStoresStackTrace() = runTest {
        repository.error("Backup", "write failed", IllegalStateException("disk full"))

        val entry = repository.observe().first { it.isNotEmpty() }.single()
        assertThat(entry.level).isEqualTo(ErrorLevel.ERROR)
        assertThat(entry.source).isEqualTo("Backup")
        assertThat(entry.message).isEqualTo("write failed")
        assertThat(entry.stackTrace).contains("IllegalStateException: disk full")
    }

    @Test
    fun prunesAt500() = runTest {
        repeat(505) { repository.record(ErrorLevel.INFO, "Test", "m$it", null) }

        val entries = repository.observe().first()
        assertThat(entries).hasSize(500)
        assertThat(entries.first().message).isEqualTo("m504")
    }

    @Test
    fun importsPendingCrashOnce() = runTest {
        CrashRecorder(pendingCrashFile, clock = { 42L }).write(Thread.currentThread(), RuntimeException("boom"))

        repository.importPendingCrash()
        repository.importPendingCrash()

        val entry = repository.observe().first().single()
        assertThat(entry.source).isEqualTo("Crash")
        assertThat(entry.timestamp).isEqualTo(42L)
        assertThat(pendingCrashFile.exists()).isFalse()
    }
}
