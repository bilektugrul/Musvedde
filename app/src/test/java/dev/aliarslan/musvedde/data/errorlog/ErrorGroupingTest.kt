package dev.aliarslan.musvedde.data.errorlog

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.domain.ErrorEntry
import dev.aliarslan.musvedde.domain.ErrorLevel
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class ErrorGroupingTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 10, 11, 12, 0).toInstant(zone).toEpochMilli()

    private fun entry(id: Long, at: LocalDateTime) =
        ErrorEntry(id, at.toInstant(zone).toEpochMilli(), ErrorLevel.ERROR, "Test", "m$id", null)

    @Test
    fun groupsTodayYesterdayAndOlderNewestFirst() {
        val entries = listOf(
            entry(1, LocalDateTime.of(2026, 10, 1, 9, 0)),
            entry(2, LocalDateTime.of(2026, 10, 11, 8, 0)),
            entry(3, LocalDateTime.of(2026, 10, 10, 23, 59)),
            entry(4, LocalDateTime.of(2026, 10, 11, 0, 1)),
        )

        val groups = groupByDay(entries, now, zone)

        assertThat(groups.map { it.key }).containsExactly(
            DayKey.Today,
            DayKey.Yesterday,
            DayKey.Date(LocalDate.of(2026, 10, 1)),
        ).inOrder()
        assertThat(groups[0].entries.map { it.id }).containsExactly(2L, 4L).inOrder()
    }
}
