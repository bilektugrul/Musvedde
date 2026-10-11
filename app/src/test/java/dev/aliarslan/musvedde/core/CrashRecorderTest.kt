package dev.aliarslan.musvedde.core

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.errorlog.CrashRecorder
import dev.aliarslan.musvedde.domain.ErrorLevel
import org.junit.After
import org.junit.Test
import java.io.File

class CrashRecorderTest {
    private val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val file = File.createTempFile("crash", ".json").apply { delete() }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        file.delete()
    }

    @Test
    fun crashIsWrittenAndReadBack() {
        var delegated = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegated = true }
        val recorder = CrashRecorder(file, clock = { 7L })

        recorder.install()
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertThat(delegated).isTrue()
        val entry = recorder.readAndDelete()!!
        assertThat(entry.message).isEqualTo("boom")
        assertThat(entry.level).isEqualTo(ErrorLevel.ERROR)
        assertThat(entry.source).isEqualTo("Crash")
        assertThat(entry.timestamp).isEqualTo(7L)
        assertThat(entry.stackTrace).contains("RuntimeException")
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun corruptFileIsDiscarded() {
        file.writeText("{not json")

        assertThat(CrashRecorder(file, clock = { 0L }).readAndDelete()).isNull()
        assertThat(file.exists()).isFalse()
    }
}
