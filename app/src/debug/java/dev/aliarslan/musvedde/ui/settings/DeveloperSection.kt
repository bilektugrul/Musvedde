package dev.aliarslan.musvedde.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.di.ApplicationScope
import dev.aliarslan.musvedde.dev.SampleNotesSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val seeder: SampleNotesSeeder,
    private val db: MusveddeDatabase,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun seed(count: Int) = run { seeder.seed(count) }

    fun deleteAll() = run {
        db.noteDao().deleteAll()
        db.labelDao().deleteAll()
    }

    private fun run(block: suspend () -> Unit) {
        _busy.value = true
        appScope.launch {
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }
}

@Composable
fun DeveloperSection(viewModel: DeveloperViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    SectionHeader("Developer")
    ActionRow("Generate 1,000 sample notes", enabled = !busy) { viewModel.seed(1_000) }
    ActionRow("Generate 10,000 sample notes", enabled = !busy) { viewModel.seed(10_000) }
    ActionRow("Delete all notes and labels", enabled = !busy) { viewModel.deleteAll() }
}
