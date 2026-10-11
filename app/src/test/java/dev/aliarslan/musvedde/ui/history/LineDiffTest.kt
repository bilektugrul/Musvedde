package dev.aliarslan.musvedde.ui.history

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LineDiffTest {
    @Test
    fun marksAddedRemovedAndUnchangedLines() {
        val diff = lineDiff(old = "gece\ndeniz\nay", new = "gece\nyıldız\nay\nsabah")

        assertThat(diff).containsExactly(
            DiffLine(DiffType.SAME, "gece"),
            DiffLine(DiffType.REMOVED, "deniz"),
            DiffLine(DiffType.ADDED, "yıldız"),
            DiffLine(DiffType.SAME, "ay"),
            DiffLine(DiffType.ADDED, "sabah"),
        ).inOrder()
    }

    @Test
    fun statsCountChangedLines() {
        assertThat(diffStats(old = "a\nb\nc", new = "a\nx\nc\nd")).isEqualTo(DiffStats(added = 2, removed = 1))
        assertThat(diffStats(old = "", new = "a\nb")).isEqualTo(DiffStats(added = 2, removed = 0))
        assertThat(diffStats(old = "a", new = "a")).isEqualTo(DiffStats(added = 0, removed = 0))
    }

    @Test
    fun hugeInputsFallBackWithoutBlowingUp() {
        val old = (1..5_000).joinToString("\n") { "eski $it" }
        val new = (1..5_000).joinToString("\n") { "yeni $it" }

        assertThat(diffStats(old, new)).isEqualTo(DiffStats(added = 5_000, removed = 5_000))
    }
}
