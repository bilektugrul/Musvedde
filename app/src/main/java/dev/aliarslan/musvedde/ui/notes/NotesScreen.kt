package dev.aliarslan.musvedde.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.domain.ListLayout
import dev.aliarslan.musvedde.domain.NoteSummary
import dev.aliarslan.musvedde.ui.NotesSection
import dev.aliarslan.musvedde.ui.editor.ColorPickerSheet
import dev.aliarslan.musvedde.ui.editor.LabelPickerSheet
import dev.aliarslan.musvedde.ui.theme.LocalAppearance
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

@Composable
fun NotesScreen(
    onMenu: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val labels by viewModel.allLabels.collectAsStateWithLifecycle()
    val labelNames = remember(labels) { labels.associate { it.id to it.name }.toImmutableMap() }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    var showColors by rememberSaveable { mutableStateOf(false) }
    var showLabels by rememberSaveable { mutableStateOf(false) }
    var confirmEmptyTrash by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteForever by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is NotesEvent.Trashed -> resources.getQuantityString(R.plurals.event_trashed, event.count, event.count)
                is NotesEvent.Archived -> resources.getQuantityString(R.plurals.event_archived, event.count, event.count)
                is NotesEvent.Unarchived -> resources.getQuantityString(R.plurals.event_unarchived, event.count, event.count)
                is NotesEvent.Restored -> resources.getQuantityString(R.plurals.event_restored, event.count, event.count)
            }
            val result = snackbar.showSnackbar(message, actionLabel = resources.getString(R.string.action_undo), withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLast()
        }
    }

    BackHandler(enabled = state.selection.isNotEmpty()) { viewModel.clearSelection() }

    val sectionTitle = when (state.section) {
        NotesSection.ACTIVE -> null
        NotesSection.ARCHIVED -> stringResource(R.string.nav_archive)
        NotesSection.TRASHED -> stringResource(R.string.nav_trash)
        NotesSection.LABEL -> labelNames[state.labelId]
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (state.selection.isNotEmpty()) {
                SelectionTopBar(
                    count = state.selection.size,
                    section = state.section,
                    onClose = viewModel::clearSelection,
                    onPin = viewModel::pinSelected,
                    onColor = { showColors = true },
                    onLabel = { showLabels = true },
                    onArchive = viewModel::archiveSelected,
                    onTrash = viewModel::trashSelected,
                    onRestore = viewModel::restoreSelected,
                    onDeleteForever = { confirmDeleteForever = true },
                )
            } else {
                SearchTopBar(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    layout = state.layout,
                    onToggleLayout = { viewModel.toggleLayout() },
                    onMenu = onMenu,
                    title = sectionTitle?.let { stringResource(R.string.search_in, it) },
                )
            }
        },
        floatingActionButton = {
            if (state.section == NotesSection.ACTIVE || state.section == NotesSection.LABEL) {
                FloatingActionButton(onClick = onNewNote) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_new_note))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loaded && state.pinned.isEmpty() && state.others.isEmpty()) {
                EmptyState(state.section, searching = state.query.isNotBlank())
            } else {
                NotesGrid(
                    state = state,
                    labelNames = labelNames,
                    onOpen = { id -> if (state.selection.isEmpty()) onOpenNote(id) else viewModel.toggleSelect(id) },
                    onSelect = viewModel::toggleSelect,
                    onDragStart = viewModel::select,
                    onMove = viewModel::move,
                    onEmptyTrash = { confirmEmptyTrash = true },
                )
            }
        }
    }

    if (showColors) {
        ColorPickerSheet(
            selected = dev.aliarslan.musvedde.domain.NoteColor.DEFAULT,
            onSelect = {
                viewModel.colorSelected(it)
                showColors = false
            },
            onDismiss = { showColors = false },
        )
    }
    if (showLabels) {
        val selectedNotes = (state.pinned + state.others).filter { it.id in state.selection }
        val common = labels.map { it.id }
            .filter { id -> selectedNotes.isNotEmpty() && selectedNotes.all { id in it.labelIds } }
            .toImmutableSet()
        LabelPickerSheet(
            labels = labels,
            selected = common,
            onToggle = { id -> viewModel.labelSelected(id, add = id !in common) },
            onCreate = {},
            onDismiss = { showLabels = false },
        )
    }
    if (confirmEmptyTrash) {
        ConfirmDialog(
            text = stringResource(R.string.confirm_empty_trash),
            confirm = stringResource(R.string.action_empty_trash),
            onConfirm = { viewModel.emptyTrash() },
            onDismiss = { confirmEmptyTrash = false },
        )
    }
    if (confirmDeleteForever) {
        ConfirmDialog(
            text = stringResource(R.string.confirm_delete_forever),
            confirm = stringResource(R.string.action_delete_forever),
            onConfirm = { viewModel.deleteForeverSelected() },
            onDismiss = { confirmDeleteForever = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesGrid(
    state: NotesUiState,
    labelNames: ImmutableMap<Long, String>,
    onOpen: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onDragStart: (Long) -> Unit,
    onMove: (id: Long, aboveId: Long?, belowId: Long?) -> Unit,
    onEmptyTrash: () -> Unit,
) {
    val gridState = rememberLazyStaggeredGridState()
    var pinned by remember(state.pinned) { mutableStateOf(state.pinned) }
    var others by remember(state.others) { mutableStateOf(state.others) }
    val reorderState = rememberReorderableLazyStaggeredGridState(gridState) { from, to ->
        val fromId = from.key as? Long ?: return@rememberReorderableLazyStaggeredGridState
        val toId = to.key as? Long ?: return@rememberReorderableLazyStaggeredGridState
        pinned.moved(fromId, toId)?.let { pinned = it }
        others.moved(fromId, toId)?.let { others = it }
    }
    val persistMove = { id: Long ->
        val list = if (pinned.any { it.id == id }) pinned else others
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) onMove(id, list.getOrNull(index - 1)?.id, list.getOrNull(index + 1)?.id)
    }
    val reduceMotion = LocalAppearance.current.reduceMotion

    LazyVerticalStaggeredGrid(
        columns = if (state.layout == ListLayout.GRID) StaggeredGridCells.Adaptive(160.dp) else StaggeredGridCells.Fixed(1),
        state = gridState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        modifier = Modifier.fillMaxSize().testTag("notes_grid"),
    ) {
        if (state.section == NotesSection.TRASHED) {
            item(key = "trash_header", span = StaggeredGridItemSpan.FullLine, contentType = "header") {
                TrashHeader(onEmptyTrash)
            }
        }
        if (pinned.isNotEmpty()) {
            header("pinned_header", R.string.header_pinned)
        }
        noteItems(pinned, state, labelNames, reorderState, reduceMotion, onOpen, onSelect, onDragStart, persistMove)
        if (pinned.isNotEmpty() && others.isNotEmpty()) {
            header("others_header", R.string.header_others)
        }
        noteItems(others, state, labelNames, reorderState, reduceMotion, onOpen, onSelect, onDragStart, persistMove)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyStaggeredGridScope.noteItems(
    notes: ImmutableList<NoteSummary>,
    state: NotesUiState,
    labelNames: ImmutableMap<Long, String>,
    reorderState: sh.calvin.reorderable.ReorderableLazyStaggeredGridState,
    reduceMotion: Boolean,
    onOpen: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onDragStart: (Long) -> Unit,
    onDragStop: (Long) -> Unit,
) {
    items(notes, key = { it.id }, contentType = { "note" }) { note ->
        ReorderableItem(
            reorderState,
            key = note.id,
            enabled = state.canReorder,
            animateItemModifier = if (reduceMotion) Modifier else Modifier.animateItem(),
        ) { _ ->
            val dragModifier = if (state.canReorder) {
                Modifier.longPressDraggableHandle(
                    onDragStarted = { onDragStart(note.id) },
                    onDragStopped = { onDragStop(note.id) },
                )
            } else {
                Modifier
            }
            NoteCard(
                note = note,
                labelNames = labelNames,
                selected = note.id in state.selection,
                previewSize = state.previewSize,
                modifier = Modifier
                    .testTag("note_card")
                    .then(dragModifier)
                    .combinedClickable(
                        onClick = { onOpen(note.id) },
                        onLongClick = if (state.canReorder) null else ({ onSelect(note.id) }),
                    ),
            )
        }
    }
}

private fun LazyStaggeredGridScope.header(key: String, text: Int) {
    item(key = key, span = StaggeredGridItemSpan.FullLine, contentType = "header") {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
        )
    }
}

private fun ImmutableList<NoteSummary>.moved(fromId: Long, toId: Long): ImmutableList<NoteSummary>? {
    val from = indexOfFirst { it.id == fromId }
    val to = indexOfFirst { it.id == toId }
    if (from < 0 || to < 0) return null
    return toMutableList().apply { add(to, removeAt(from)) }.toImmutableList()
}

@Composable
private fun TrashHeader(onEmptyTrash: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        Text(
            stringResource(R.string.trash_auto_delete),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onEmptyTrash) { Text(stringResource(R.string.action_empty_trash)) }
    }
}

@Composable
private fun EmptyState(section: NotesSection, searching: Boolean) {
    val (icon: ImageVector, text: Int) = when {
        searching -> Icons.Outlined.SearchOff to R.string.empty_search
        section == NotesSection.ARCHIVED -> Icons.Outlined.Archive to R.string.empty_archive
        section == NotesSection.TRASHED -> Icons.Outlined.Delete to R.string.empty_trash
        else -> Icons.Outlined.Lightbulb to R.string.empty_notes
    }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, start = 32.dp, end = 32.dp),
        )
    }
}

@Composable
fun ConfirmDialog(text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
