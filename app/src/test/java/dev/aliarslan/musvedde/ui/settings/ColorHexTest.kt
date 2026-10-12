package dev.aliarslan.musvedde.ui.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorHexTest {
    @Test
    fun parsesCommonHexForms() {
        assertThat(parseHexColor("#ff0dd6d1")).isEqualTo(0xFF0DD6D1.toInt())
        assertThat(parseHexColor("0DD6D1")).isEqualTo(0xFF0DD6D1.toInt())
        assertThat(parseHexColor(" #fc0 ")).isEqualTo(0xFFFFCC00.toInt())
        assertThat(parseHexColor("#80ffc90f")).isEqualTo(0xFFFFC90F.toInt())
    }

    @Test
    fun rejectsInvalidInput() {
        assertThat(parseHexColor("")).isNull()
        assertThat(parseHexColor("#12345")).isNull()
        assertThat(parseHexColor("#gggggg")).isNull()
    }

    @Test
    fun formatsAsLowercaseRgb() {
        assertThat(0xFF0DD6D1.toInt().toHex()).isEqualTo("#0dd6d1")
    }
}
