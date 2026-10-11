package dev.aliarslan.musvedde.ui.editor

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingSaverTest {
    private val saved = mutableListOf<Draft>()

    @Test
    fun debouncesToLatest() = runTest {
        val saver = PendingSaver(backgroundScope, debounceMs = 300) { saved += it }

        saver.submit(Draft("", "a"))
        advanceTimeBy(50)
        saver.submit(Draft("", "ab"))
        advanceTimeBy(50)
        saver.submit(Draft("", "abc"))
        advanceTimeBy(299)
        assertThat(saved).isEmpty()

        advanceTimeBy(2)
        assertThat(saved).containsExactly(Draft("", "abc"))
    }

    @Test
    fun flushSavesImmediately() = runTest {
        val saver = PendingSaver(backgroundScope, debounceMs = 300) { saved += it }

        saver.submit(Draft("t", "b"))
        saver.flush()
        runCurrent()

        assertThat(saved).containsExactly(Draft("t", "b"))
    }

    @Test
    fun flushOnStopPersistsPendingText() = runTest {
        val saver = PendingSaver(backgroundScope, debounceMs = 300) { saved += it }

        saver.submit(Draft("", "satır"))
        advanceTimeBy(100)
        saver.flush()
        runCurrent()
        advanceTimeBy(1_000)

        assertThat(saved).containsExactly(Draft("", "satır"))
    }

    @Test
    fun flushWithNothingPendingSavesNothing() = runTest {
        val saver = PendingSaver(backgroundScope, debounceMs = 300) { saved += it }

        saver.flush()
        runCurrent()

        assertThat(saved).isEmpty()
    }

    @Test
    fun savesAreSerialized() = runTest {
        var inFlight = 0
        var maxInFlight = 0
        val saver = PendingSaver(backgroundScope, debounceMs = 300) {
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            delay(1_000)
            saved += it
            inFlight--
        }

        saver.submit(Draft("", "a"))
        advanceTimeBy(301)
        saver.submit(Draft("", "ab"))
        advanceTimeBy(301)
        advanceTimeBy(3_000)

        assertThat(saved).containsExactly(Draft("", "a"), Draft("", "ab")).inOrder()
        assertThat(maxInFlight).isEqualTo(1)
    }

    @Test
    fun newSubmitDoesNotCancelSaveInFlight() = runTest {
        val saver = PendingSaver(backgroundScope, debounceMs = 300) {
            delay(500)
            saved += it
        }

        saver.submit(Draft("", "a"))
        advanceTimeBy(400)
        saver.submit(Draft("", "ab"))
        saver.flush()
        advanceTimeBy(2_000)

        assertThat(saved).containsExactly(Draft("", "a"), Draft("", "ab")).inOrder()
    }
}
