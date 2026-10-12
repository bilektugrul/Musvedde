package dev.aliarslan.musvedde.ui.settings

import android.content.Intent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aliarslan.musvedde.BuildConfig
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.data.update.UpdateChecker
import dev.aliarslan.musvedde.data.update.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Done(val result: UpdateResult) : UpdateState
}

@HiltViewModel
class AboutViewModel @Inject constructor(private val checker: UpdateChecker) : ViewModel() {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()

    fun check() {
        if (_state.value == UpdateState.Checking) return
        _state.value = UpdateState.Checking
        viewModelScope.launch { _state.value = UpdateState.Done(checker.check(BuildConfig.VERSION_NAME)) }
    }
}

@Composable
fun AboutSection(viewModel: AboutViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val repoUrl = "https://github.com/${BuildConfig.REPOSITORY}"
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    ActionRow(stringResource(R.string.app_name), stringResource(R.string.about_version, BuildConfig.VERSION_NAME)) {}
    ActionRow(stringResource(R.string.about_source), repoUrl.removePrefix("https://")) { open(repoUrl) }

    val updateSummary = when (val s = state) {
        UpdateState.Idle -> stringResource(R.string.about_update_tap)
        UpdateState.Checking -> stringResource(R.string.about_update_checking)
        is UpdateState.Done -> when (val r = s.result) {
            is UpdateResult.Available -> stringResource(R.string.about_update_available, r.version)
            UpdateResult.UpToDate -> stringResource(R.string.about_update_latest)
            UpdateResult.NoReleases -> stringResource(R.string.about_update_no_releases)
            is UpdateResult.Failed -> stringResource(R.string.about_update_failed, r.message)
        }
    }
    val available = (state as? UpdateState.Done)?.result as? UpdateResult.Available
    ActionRow(
        stringResource(if (available != null) R.string.about_update_download else R.string.about_update_check),
        updateSummary,
        enabled = state != UpdateState.Checking,
    ) {
        if (available != null) open(available.apkUrl ?: available.pageUrl) else viewModel.check()
    }
    if (state == UpdateState.Checking) CircularProgressIndicator()
    ActionRow(stringResource(R.string.about_license), stringResource(R.string.about_license_summary)) {}
}
