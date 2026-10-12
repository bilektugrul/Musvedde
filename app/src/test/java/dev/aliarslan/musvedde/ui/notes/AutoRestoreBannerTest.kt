package dev.aliarslan.musvedde.ui.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.aliarslan.musvedde.data.backup.AutoRestoreState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoRestoreBannerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsProgressThenTheResultAndCanBeDismissed() {
        var state by mutableStateOf<AutoRestoreState?>(AutoRestoreState.Downloading(512, 2048))
        compose.setContent { AutoRestoreBanner(state, onDismiss = { state = null }) }

        compose.onNodeWithText("Restoring your latest Google Drive backup").assertIsDisplayed()

        state = AutoRestoreState.Done(noteCount = 12, backupName = "musvedde-backup-x.zip", backupTime = 1_700_000_000_000)
        compose.onNodeWithText("Restored 12 notes from Google Drive").assertIsDisplayed()

        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("auto_restore_banner").assertDoesNotExist()
    }
}
