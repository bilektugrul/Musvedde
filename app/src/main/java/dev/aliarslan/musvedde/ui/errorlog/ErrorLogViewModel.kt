package dev.aliarslan.musvedde.ui.errorlog

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.BuildConfig
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.errorlog.ErrorDayGroup
import dev.aliarslan.musvedde.data.errorlog.ErrorLogRepository
import dev.aliarslan.musvedde.data.errorlog.groupByDay
import dev.aliarslan.musvedde.domain.ErrorEntry
import dev.aliarslan.musvedde.domain.ErrorLevel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ErrorLogUiState(
    val loaded: Boolean = false,
    val groups: ImmutableList<ErrorDayGroup> = persistentListOf(),
    val levels: ImmutableSet<ErrorLevel> = ErrorLevel.entries.toPersistentSet(),
    val source: String? = null,
    val sources: ImmutableList<String> = persistentListOf(),
    val expanded: ImmutableSet<Long> = persistentSetOf(),
)

@HiltViewModel
class ErrorLogViewModel @Inject constructor(
    private val repository: ErrorLogRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    private val levels = MutableStateFlow<PersistentSet<ErrorLevel>>(ErrorLevel.entries.toPersistentSet())
    private val source = MutableStateFlow<String?>(null)
    private val expanded = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    private var visible: List<ErrorEntry> = emptyList()

    val uiState: StateFlow<ErrorLogUiState> = combine(repository.observe(), levels, source, expanded) { all, lv, src, exp ->
        visible = all.filter { it.level in lv && (src == null || it.source == src) }
        ErrorLogUiState(
            loaded = true,
            groups = groupByDay(visible, clock.now(), zone),
            levels = lv,
            source = src,
            sources = all.map { it.source }.distinct().sorted().toImmutableList(),
            expanded = exp,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ErrorLogUiState())

    fun toggleLevel(level: ErrorLevel) = levels.update { if (level in it) it.removing(level) else it.adding(level) }

    fun setSource(value: String?) {
        source.value = value
    }

    fun toggleExpanded(id: Long) = expanded.update { if (id in it) it.removing(id) else it.adding(id) }

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }

    fun entryText(entry: ErrorEntry): String = buildString {
        append('[').append(TIME.format(Instant.ofEpochMilli(entry.timestamp).atZone(zone))).append("] ")
        append(entry.level.name).append(' ').append(entry.source).append(": ").append(entry.message)
        entry.stackTrace?.let { append('\n').append(it.trimEnd()) }
    }

    fun shareText(): String = buildString {
        append("Müsvedde ").append(BuildConfig.VERSION_NAME)
        append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(')')
        visible.sortedByDescending { it.timestamp }.forEach { append("\n\n").append(entryText(it)) }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
