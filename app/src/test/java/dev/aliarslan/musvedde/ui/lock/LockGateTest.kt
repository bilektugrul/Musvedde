package dev.aliarslan.musvedde.ui.lock

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.aliarslan.musvedde.data.lock.FakeKeyWrapper
import dev.aliarslan.musvedde.data.lock.LockConfig
import dev.aliarslan.musvedde.data.lock.LockManager
import dev.aliarslan.musvedde.data.settings.InMemoryPreferencesDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LockGateTest {
    @get:Rule
    val compose = createComposeRule()

    private val lock = LockManager(InMemoryPreferencesDataStore(), FakeKeyWrapper(), clock = { 1L }, LockConfig(1_000, 1_000))
    private val viewModel = LockViewModel(lock, clock = { 1L })

    private fun showGate() {
        runBlocking {
            lock.setPin("1234")
            lock.lock()
        }
        compose.setContent { LockGate(locked = true, viewModel = viewModel) { Text("gizli içerik") } }
    }

    @Test
    fun theRightPinOpensTheContent() {
        showGate()
        compose.onNodeWithText("gizli içerik").assertDoesNotExist()

        compose.onNodeWithTag("pin_field").performTextInput("1234")
        compose.onNodeWithTag("unlock_confirm").performClick()
        compose.waitUntil(5_000) { lock.unlocked.value }

        compose.onNodeWithText("gizli içerik").assertIsDisplayed()
    }

    @Test
    fun aWrongPinKeepsItLocked() {
        showGate()

        compose.onNodeWithTag("pin_field").performTextInput("9999")
        compose.onNodeWithTag("unlock_confirm").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Wrong PIN", substring = true)).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("gizli içerik").assertDoesNotExist()
        compose.onNodeWithTag("lock_gate").assertIsDisplayed()
    }
}
