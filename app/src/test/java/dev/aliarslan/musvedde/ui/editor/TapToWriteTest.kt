package dev.aliarslan.musvedde.ui.editor

import androidx.compose.ui.geometry.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TapToWriteTest {
    private val title = Rect(left = 20f, top = 10f, right = 380f, bottom = 50f)
    private val body = Rect(left = 20f, top = 70f, right = 380f, bottom = 300f)

    @Test
    fun tapsOnTheFieldsAreLeftToTheTextField() {
        assertThat(resolveTap(200f, 30f, title, body)).isEqualTo(TapAction.Ignore)
        assertThat(resolveTap(200f, 150f, title, body)).isEqualTo(TapAction.Ignore)
    }

    @Test
    fun tapBesideABodyLineGoesToThatLinesEnd() {
        assertThat(resolveTap(5f, 150f, title, body)).isEqualTo(TapAction.BodyLineEnd(80f))
        assertThat(resolveTap(395f, 71f, title, body)).isEqualTo(TapAction.BodyLineEnd(1f))
    }

    @Test
    fun tapBelowTheTextGoesToTheEndOfTheBody() {
        assertThat(resolveTap(200f, 600f, title, body)).isEqualTo(TapAction.BodyEnd)
    }

    @Test
    fun tapAroundTheTitleGoesToTheEndOfTheTitle() {
        assertThat(resolveTap(5f, 30f, title, body)).isEqualTo(TapAction.TitleEnd)
        assertThat(resolveTap(200f, 2f, title, body)).isEqualTo(TapAction.TitleEnd)
        assertThat(resolveTap(200f, 60f, title, body)).isEqualTo(TapAction.TitleEnd)
    }

    @Test
    fun withoutATitleEverythingOutsideTheBodyGoesToTheBody() {
        assertThat(resolveTap(200f, 2f, null, body)).isEqualTo(TapAction.BodyEnd)
        assertThat(resolveTap(200f, 600f, null, body)).isEqualTo(TapAction.BodyEnd)
    }
}
