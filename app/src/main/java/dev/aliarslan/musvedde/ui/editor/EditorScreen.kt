package dev.aliarslan.musvedde.ui.editor

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.aliarslan.musvedde.notification.rememberNotificationPermissionRequester
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.domain.EditorAlign
import dev.aliarslan.musvedde.domain.NoteState
import dev.aliarslan.musvedde.domain.Settings as AppSettings
import dev.aliarslan.musvedde.ui.theme.LocalAppearance
import dev.aliarslan.musvedde.ui.theme.fontFamily
import dev.aliarslan.musvedde.ui.theme.noteContainerColor

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deniedMessage = stringResource(R.string.notification_permission_denied)
    val settingsAction = stringResource(R.string.action_open_settings)
    val requestNotificationPermission = rememberNotificationPermissionRequester(onDenied = {
        scope.launch {
            if (snackbar.showSnackbar(deniedMessage, settingsAction, withDismissAction = true) == SnackbarResult.ActionPerformed) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
    })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.editorSettings.collectAsStateWithLifecycle()
    val labels by viewModel.allLabels.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { if (it == EditorEvent.Close) onBack() }
    }

    var focusMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.alwaysFocusMode) { if (settings.alwaysFocusMode) focusMode = true }
    var showColors by rememberSaveable { mutableStateOf(false) }
    var showLabels by rememberSaveable { mutableStateOf(false) }

    FocusModeEffect(focusMode)
    BackHandler(enabled = focusMode) { focusMode = false }

    val appearance = LocalAppearance.current
    val noteColor = noteContainerColor(state.color, appearance.dark, appearance.amoled)
    val background = if (noteColor == Color.Unspecified) MaterialTheme.colorScheme.background else noteColor

    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AnimatedVisibility(visible = !focusMode) {
                EditorTopBar(
                    state = state,
                    background = background,
                    onBack = onBack,
                    onTogglePin = viewModel::togglePinnedToTop,
                    onToggleNotification = {
                        if (state.pinnedToNotification) {
                            viewModel.setPinnedToNotification(false)
                        } else {
                            requestNotificationPermission { viewModel.setPinnedToNotification(true) }
                        }
                    },
                    onArchive = viewModel::archive,
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = !focusMode) {
                EditorBottomBar(
                    background = background,
                    title = viewModel.title,
                    body = viewModel.body,
                    onColors = { showColors = true },
                    onLabels = { showLabels = true },
                    onFocus = { focusMode = true },
                    onTrash = viewModel::trash,
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (state.loaded) {
                EditorFields(
                    title = viewModel.title,
                    body = viewModel.body,
                    settings = settings,
                    focusMode = focusMode,
                    onFontSizeCommitted = viewModel::setFontSize,
                )
            }
            if (focusMode) {
                IconButton(
                    onClick = { focusMode = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .safeDrawingPadding(),
                ) {
                    Icon(
                        Icons.Outlined.CloseFullscreen,
                        contentDescription = stringResource(R.string.editor_exit_focus),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }

    if (showColors) {
        ColorPickerSheet(
            selected = state.color,
            onSelect = viewModel::setColor,
            onDismiss = { showColors = false },
        )
    }
    if (showLabels) {
        LabelPickerSheet(
            labels = labels,
            selected = state.labelIds,
            onToggle = viewModel::toggleLabel,
            onCreate = viewModel::createLabel,
            onDismiss = { showLabels = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    state: EditorUiState,
    background: Color,
    onBack: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleNotification: () -> Unit,
    onArchive: () -> Unit,
) {
    TopAppBar(
        title = {},
        colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            IconButton(onClick = onTogglePin) {
                Icon(
                    if (state.pinnedToTop) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = stringResource(if (state.pinnedToTop) R.string.action_unpin else R.string.action_pin),
                )
            }
            if (state.state == NoteState.ACTIVE) {
                IconButton(onClick = onToggleNotification) {
                    Icon(
                        if (state.pinnedToNotification) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                        contentDescription = stringResource(
                            if (state.pinnedToNotification) R.string.action_unpin_notification else R.string.action_pin_notification,
                        ),
                    )
                }
            }
            if (state.state != NoteState.TRASHED) {
                IconButton(onClick = onArchive) {
                    val archived = state.state == NoteState.ARCHIVED
                    Icon(
                        if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        contentDescription = stringResource(if (archived) R.string.action_unarchive else R.string.action_archive),
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorBottomBar(
    background: Color,
    title: TextFieldState,
    body: TextFieldState,
    onColors: () -> Unit,
    onLabels: () -> Unit,
    onFocus: () -> Unit,
    onTrash: () -> Unit,
) {
    BottomAppBar(containerColor = background, tonalElevation = 0.dp) {
        IconButton(onClick = onColors) {
            Icon(Icons.Outlined.Palette, contentDescription = stringResource(R.string.action_color))
        }
        IconButton(onClick = onLabels) {
            Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = stringResource(R.string.action_labels))
        }
        Box(Modifier.weight(1f))
        IconButton(
            onClick = { if (body.undoState.canUndo) body.undoState.undo() else title.undoState.undo() },
            enabled = body.undoState.canUndo || title.undoState.canUndo,
        ) {
            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.action_undo))
        }
        IconButton(
            onClick = { if (body.undoState.canRedo) body.undoState.redo() else title.undoState.redo() },
            enabled = body.undoState.canRedo || title.undoState.canRedo,
        ) {
            Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = stringResource(R.string.action_redo))
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onFocus) {
            Icon(Icons.Outlined.CenterFocusStrong, contentDescription = stringResource(R.string.editor_focus_mode))
        }
        IconButton(onClick = onTrash) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_trash))
        }
    }
}

@Composable
private fun EditorFields(
    title: TextFieldState,
    body: TextFieldState,
    settings: AppSettings,
    focusMode: Boolean,
    onFontSizeCommitted: (Float) -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val hint = onSurface.copy(alpha = 0.45f)
    var fontSize by remember(settings.editorFontSizeSp) { mutableFloatStateOf(settings.editorFontSizeSp) }
    val bodyStyle = remember(settings, fontSize, onSurface) {
        TextStyle(
            color = onSurface,
            fontFamily = settings.editorFont.fontFamily(),
            fontSize = fontSize.sp,
            lineHeight = settings.editorLineHeight.em,
            textAlign = if (settings.editorAlign == EditorAlign.CENTER) TextAlign.Center else TextAlign.Start,
        )
    }
    val titleStyle = remember(bodyStyle) {
        bodyStyle.copy(fontSize = (fontSize * 1.25f).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
    val zoomModifier = if (settings.pinchZoom) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var zoomed = false
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        val zoom = event.calculateZoom()
                        if (zoom != 1f) {
                            zoomed = true
                            fontSize = (fontSize * zoom).coerceIn(AppSettings.EDITOR_FONT_SIZE_RANGE)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
                if (zoomed) onFontSizeCommitted(fontSize)
            }
        }
    } else {
        Modifier
    }

    Column(
        Modifier
            .fillMaxSize()
            .then(zoomModifier)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = if (focusMode) 48.dp else 8.dp),
    ) {
        if (!focusMode || title.text.isNotEmpty()) {
            HintedField(title, titleStyle, stringResource(R.string.editor_title_hint), hint, singleLine = true)
        }
        HintedField(
            body,
            bodyStyle,
            stringResource(R.string.editor_body_hint),
            hint,
            singleLine = false,
            modifier = Modifier.padding(top = 12.dp, bottom = 120.dp),
        )
    }
}

@Composable
private fun HintedField(
    state: TextFieldState,
    style: TextStyle,
    hint: String,
    hintColor: Color,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        state = state,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(),
        modifier = modifier.fillMaxWidth(),
        decorator = { inner ->
            Box {
                if (state.text.isEmpty()) Text(hint, style = style.copy(color = hintColor))
                inner()
            }
        },
    )
}
