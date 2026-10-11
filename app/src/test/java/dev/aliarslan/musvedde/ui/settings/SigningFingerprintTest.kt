package dev.aliarslan.musvedde.ui.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SigningFingerprintTest {
    @Test
    fun formatsSha1AsColonSeparatedUppercaseHex() {
        assertThat(sha1Fingerprint("abc".toByteArray()))
            .isEqualTo("A9:99:3E:36:47:06:81:6A:BA:3E:25:71:78:50:C2:6C:9C:D0:D8:9D")
    }
}
