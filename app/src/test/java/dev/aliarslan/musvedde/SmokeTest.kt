package dev.aliarslan.musvedde

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmokeTest {
    @Test
    fun smokeTest() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.3.2")
    }
}
