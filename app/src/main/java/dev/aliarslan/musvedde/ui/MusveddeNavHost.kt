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
import dev.aliarslan.musvedde.notification.rememberNotificationPermissionRequester
import dev.aliarslan.musvedde.ui.editor.EditorScreen
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
    val requestNotificationPermission = rememberNotificationPermissionRequester(onDenied = {})
    val scope = rememberCoroutineScope()
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
                onEditLabels = { closeDrawerThen { } },
                onSettings = { closeDrawerThen { navController.navigate(SettingsRoute) } },
                onErrorLog = { closeDrawerThen { navController.navigate(ErrorLogRoute) } },
            )
        },
    ) {
        NavHost(navController, startDestination = NotesRoute()) {
            composable<NotesRoute> { Placeholder("Notes") }
            composable<EditorRoute> {
                EditorScreen(
                    onBack = { navController.popBackStack() },
                    requestNotificationPermission = requestNotificationPermission,
                )
            }
            composable<SettingsRoute> { Placeholder("Settings") }
            composable<ErrorLogRoute> { Placeholder("Error log") }
        }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }
}
