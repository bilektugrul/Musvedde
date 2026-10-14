package dev.aliarslan.musvedde.ui.errorlog

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.errorlog.ErrorLogRepository
import dev.aliarslan.musvedde.domain.ErrorLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ErrorLogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())
    private lateinit var db: MusveddeDatabase
    private lateinit var repository: ErrorLogRepository
    private var now = 1_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = inMemoryDatabase(dispatcher)
        repository = ErrorLogRepository(
            dao = db.errorLogDao(),
            clock = { now++ },
            scope = appScope,
            pendingCrashFile = File.createTempFile("crash", ".json").apply { delete() },
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = ErrorLogViewModel(repository, clock = { now }, zone = ZoneOffset.UTC).also { vm ->
        backgroundScope.launch { vm.uiState.collect {} }
    }

    private suspend fun seed() {
        repository.record(ErrorLevel.ERROR, "Backup", "folder missing", null)
        repository.record(ErrorLevel.WARNING, "Drive", "slow", null)
        repository.record(ErrorLevel.INFO, "Backup", "done", null)
        repository.record(ErrorLevel.ERROR, "Crash", "boom", IllegalStateException("x"))
    }

    @Test
    fun filtersByLevelAndSource() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.groups.flatMap { it.entries }).hasSize(4)
        assertThat(vm.uiState.value.sources).containsExactly("Backup", "Crash", "Drive").inOrder()

        vm.toggleLevel(ErrorLevel.INFO)
        vm.toggleLevel(ErrorLevel.WARNING)
        advanceUntilIdle()
        assertThat(vm.uiState.value.groups.flatMap { it.entries }.map { it.message }).containsExactly("boom", "folder missing").inOrder()

        vm.setSource("Backup")
        advanceUntilIdle()
        assertThat(vm.uiState.value.groups.flatMap { it.entries }.map { it.message }).containsExactly("folder missing")
    }

    @Test
    fun shareTextContainsHeaderAndEntriesNewestFirst() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        advanceUntilIdle()

        val text = vm.shareText()

        assertThat(text.lines().first()).startsWith("Müsvedde 0.8.0 · ")
        assertThat(text.lines().first()).contains("(API ")
        assertThat(text.indexOf("boom")).isLessThan(text.indexOf("folder missing"))
        assertThat(text).contains("IllegalStateException: x")
    }

    @Test
    fun clearRemovesEverything() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        advanceUntilIdle()

        vm.clear()
        advanceUntilIdle()

        assertThat(vm.uiState.value.groups).isEmpty()
    }
}
