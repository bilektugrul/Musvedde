package dev.aliarslan.musvedde.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.data.note.LabelRepository
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import dev.aliarslan.musvedde.domain.Label
import dev.aliarslan.musvedde.domain.Settings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import dev.aliarslan.musvedde.web.WebEditorController
import dev.aliarslan.musvedde.web.WebEditorStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    labelRepository: LabelRepository,
    webEditor: WebEditorController,
) : ViewModel() {

    val settings: StateFlow<Settings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val labels: StateFlow<ImmutableList<Label>> = labelRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

    val webEditorRunning: StateFlow<Boolean> = webEditor.status
        .map { it != WebEditorStatus.Stopped }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
