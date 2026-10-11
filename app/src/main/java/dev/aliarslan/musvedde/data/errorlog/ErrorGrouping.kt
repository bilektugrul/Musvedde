package dev.aliarslan.musvedde.data.errorlog

import dev.aliarslan.musvedde.domain.ErrorEntry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface DayKey {
    data object Today : DayKey
    data object Yesterday : DayKey
    data class Date(val date: LocalDate) : DayKey
}

data class ErrorDayGroup(val key: DayKey, val entries: ImmutableList<ErrorEntry>)

fun groupByDay(entries: List<ErrorEntry>, now: Long, zone: ZoneId): ImmutableList<ErrorDayGroup> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return entries
        .sortedWith(compareByDescending<ErrorEntry> { it.timestamp }.thenByDescending { it.id })
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        .map { (date, dayEntries) ->
            val key = when (date) {
                today -> DayKey.Today
                today.minusDays(1) -> DayKey.Yesterday
                else -> DayKey.Date(date)
            }
            ErrorDayGroup(key, dayEntries.toImmutableList())
        }
        .toImmutableList()
}
