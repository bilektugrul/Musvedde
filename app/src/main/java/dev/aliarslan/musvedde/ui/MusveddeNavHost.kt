package dev.aliarslan.musvedde.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.aliarslan.musvedde.domain.Label
import dev.aliarslan.musvedde.ui.editor.EditorScreen
import dev.aliarslan.musvedde.ui.labels.EditLabelsDialog
import dev.aliarslan.musvedde.ui.notes.NotesScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun MusveddeNavHost(
    labels: ImmutableList<Label>,
    openNoteRequests: Flow<Long>,
    navController: NavHostController = rememberNavController(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showEditLabels by rememberSaveable { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentNotes = backStackEntry
        ?.takeIf { it.destination.hasRoute(NotesRoute::class) }
        ?.toRoute<NotesRoute>()

    LaunchedEffect(navController, openNoteRequests) {
        openNoteRequests.collect { id ->
            navController.navigate(EditorRoute(id)) { launchSingleTop = true }
        }
    }

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentNotes != null,
        drawerContent = {
            AppDrawer(
                current = currentNotes,
                labels = labels,
                onNotes = { route ->
                    closeDrawerThen {
                        navController.navigate(route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onEditLabels = { closeDrawerThen { showEditLabels = true } },
                onSettings = { closeDrawerThen { navController.navigate(SettingsRoute) } },
                onErrorLog = { closeDrawerThen { navController.navigate(ErrorLogRoute) } },
            )
        },
    ) {
        NavHost(navController, startDestination = NotesRoute()) {
            composable<NotesRoute> {
                NotesScreen(
                    onMenu = { scope.launch { drawerState.open() } },
                    onOpenNote = { id -> navController.navigate(EditorRoute(id)) },
                    onNewNote = { navController.navigate(EditorRoute()) },
                )
            }
            composable<EditorRoute> {
                EditorScreen(onBack = { navController.popBackStack() })
            }
            composable<SettingsRoute> { Placeholder("Settings") }
            composable<ErrorLogRoute> { Placeholder("Error log") }
        }
    }
    if (showEditLabels) EditLabelsDialog(onDismiss = { showEditLabels = false })
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }
}
