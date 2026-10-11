package dev.aliarslan.musvedde.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartWith10kNotes() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { seedNotes() },
    ) {
        pressHome()
        startActivityAndWait()
        waitForNotes()
    }
}

@RunWith(AndroidJUnit4::class)
class ListScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun flingWith10kNotes() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            seedNotes()
            pressHome()
            startActivityAndWait()
            waitForNotes()
        },
    ) {
        flingNotes()
    }
}

@RunWith(AndroidJUnit4::class)
class TypingBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun typingWith10kNotes() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            seedNotes()
            pressHome()
            startActivityAndWait()
            waitForNotes()
            openFirstNote()
        },
    ) {
        typeInEditor(characters = 200)
    }
}
