package dev.aliarslan.musvedde.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoteColorTest {
    @Test
    fun unknownColorFallsBackToDefault() {
        assertThat(NoteColor.fromName("NOPE")).isEqualTo(NoteColor.DEFAULT)
        assertThat(NoteColor.fromName("TEAL")).isEqualTo(NoteColor.TEAL)
        assertThat(NoteColor.entries).hasSize(12)
    }
}
