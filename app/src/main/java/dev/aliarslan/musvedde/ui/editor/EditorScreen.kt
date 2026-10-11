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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.testTag
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
    val barEnter = fadeIn(tween(appearance.motionMillis(220))) + expandVertically(tween(appearance.motionMillis(260)))
    val barExit = fadeOut(tween(appearance.motionMillis(180))) + shrinkVertically(tween(appearance.motionMillis(240)))
    val noteColor = noteContainerColor(state.color, appearance.dark, appearance.amoled)
    val background = if (noteColor == Color.Unspecified) MaterialTheme.colorScheme.background else noteColor

    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AnimatedVisibility(visible = !focusMode, enter = barEnter, exit = barExit) {
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
            AnimatedVisibility(visible = !focusMode, enter = barEnter, exit = barExit) {
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
                    autoFocusBody = viewModel.isNewNote,
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
    autoFocusBody: Boolean,
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
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    val zoomModifier = if (settings.pinchZoom) {
        Modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoomed = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                if (!zoomed) {
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    zoomOrigin = TransformOrigin(centroid.x / size.width, centroid.y / size.height)
                                }
                                zoomed = true
                                zoomScale = nextZoomScale(zoomScale, zoom, fontSize, AppSettings.EDITOR_FONT_SIZE_RANGE)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (zoomed) {
                        // While pinching only the layer scales; the text is laid out again once, at the new size.
                        fontSize = zoomedFontSize(fontSize, zoomScale, AppSettings.EDITOR_FONT_SIZE_RANGE)
                        zoomScale = 1f
                        onFontSizeCommitted(fontSize)
                    }
                }
            }
            .graphicsLayer {
                scaleX = zoomScale
                scaleY = zoomScale
                transformOrigin = zoomOrigin
            }
    } else {
        Modifier
    }

    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var contentCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var titleBounds by remember { mutableStateOf<Rect?>(null) }
    var bodyBounds by remember { mutableStateOf<Rect?>(null) }
    val bodyLayout = remember { arrayOfNulls<() -> TextLayoutResult?>(1) }
    val showTitle = !focusMode || title.text.isNotEmpty()

    LaunchedEffect(autoFocusBody) {
        if (autoFocusBody) {
            bodyFocus.requestFocus()
            keyboard?.show()
        }
    }

    fun focus(requester: FocusRequester, state: TextFieldState, offset: Int?) {
        state.edit { selection = TextRange(offset?.coerceIn(0, length) ?: length) }
        requester.requestFocus()
        keyboard?.show()
    }

    fun onTap(offset: Offset) {
        val coords = contentCoords ?: return
        val bodyRect = bodyBounds ?: return
        val root = coords.localToRoot(offset)
        when (val action = resolveTap(root.x, root.y, titleBounds.takeIf { showTitle }, bodyRect)) {
            TapAction.Ignore -> Unit
            TapAction.TitleEnd -> focus(titleFocus, title, null)
            TapAction.BodyEnd -> focus(bodyFocus, body, null)
            is TapAction.BodyLineEnd -> {
                val layout = bodyLayout[0]?.invoke()
                val lineEnd = layout?.let { it.getLineEnd(it.getLineForVerticalPosition(action.yInBody), visibleEnd = true) }
                focus(bodyFocus, body, lineEnd)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().then(zoomModifier)) {
        val viewportHeight = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .onGloballyPositioned { contentCoords = it }
                .pointerInput(Unit) { detectTapGestures(onTap = ::onTap) }
                .padding(horizontal = 20.dp, vertical = if (focusMode) 48.dp else 8.dp),
        ) {
            if (showTitle) {
                HintedField(
                    title,
                    titleStyle,
                    stringResource(R.string.editor_title_hint),
                    hint,
                    singleLine = true,
                    modifier = Modifier
                        .focusRequester(titleFocus)
                        .onGloballyPositioned { titleBounds = it.boundsInRoot() },
                )
            }
            HintedField(
                body,
                bodyStyle,
                stringResource(R.string.editor_body_hint),
                hint,
                singleLine = false,
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 120.dp)
                    .testTag("editor_body")
                    .focusRequester(bodyFocus)
                    .onGloballyPositioned { bodyBounds = it.boundsInRoot() },
                onTextLayout = { bodyLayout[0] = it },
            )
        }
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
    onTextLayout: ((() -> TextLayoutResult?) -> Unit)? = null,
) {
    BasicTextField(
        state = state,
        textStyle = style,
        onTextLayout = onTextLayout?.let { callback -> { getResult -> callback(getResult) } },
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
