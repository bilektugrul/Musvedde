package dev.aliarslan.musvedde.ui.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SliderRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val committed = mutableListOf<Float>()

    private fun show(value: Float) {
        compose.setContent {
            SliderRow(
                title = "Size",
                value = value,
                range = 14f..32f,
                step = 1f,
                format = { "${it.toInt()} sp" },
                defaultValue = 18f,
                onValueChangeFinished = { committed += it },
            )
        }
    }

    private fun resetButton() = compose.onNode(hasText("Default") and hasClickAction())

    @Test
    fun resetButtonRestoresTheDefault() {
        show(value = 26f)

        resetButton().assertIsEnabled().performClick()

        assertThat(committed).containsExactly(18f)
        compose.onNode(hasText("18 sp")).assertExists()
    }

    @Test
    fun resetButtonIsDisabledAtTheDefault() {
        show(value = 18f)

        resetButton().assertIsNotEnabled()
    }
}
