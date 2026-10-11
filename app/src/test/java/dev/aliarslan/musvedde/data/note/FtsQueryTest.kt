package dev.aliarslan.musvedde.data.note

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FtsQueryTest {
    @Test
    fun searchSanitizesFtsSyntax() {
        assertThat(toFtsQuery("\"aşk")).isEqualTo("\"aşk*\"")
        assertThat(toFtsQuery("a-b OR*")).isEqualTo("\"a*\" \"b*\" \"OR*\"")
        assertThat(toFtsQuery("  ")).isNull()
        assertThat(toFtsQuery("-*\"")).isNull()
    }
}
