package dev.aliarslan.musvedde.ui

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import dev.aliarslan.musvedde.ui.theme.LocalAppearance
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.aliarslan.musvedde.domain.Label
import dev.aliarslan.musvedde.ui.editor.EditorScreen
import dev.aliarslan.musvedde.ui.labels.EditLabelsDialog
import dev.aliarslan.musvedde.ui.errorlog.ErrorLogScreen
import dev.aliarslan.musvedde.ui.history.HistoryScreen
import dev.aliarslan.musvedde.ui.storage.StorageScreen
import dev.aliarslan.musvedde.ui.notes.NotesScreen
import dev.aliarslan.musvedde.ui.settings.SettingsCategory
import dev.aliarslan.musvedde.ui.settings.SettingsCategoryScreen
import dev.aliarslan.musvedde.ui.settings.SettingsHomeScreen
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
    openWebEditorRequests: Flow<Unit>,
    webEditorRunning: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showEditLabels by rememberSaveable { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val canGoBack = backStackEntry != null && navController.previousBackStackEntry != null
    val currentNotes = backStackEntry
        ?.takeIf { it.destination.hasRoute(NotesRoute::class) }
        ?.toRoute<NotesRoute>()

    LaunchedEffect(navController, openNoteRequests) {
        openNoteRequests.collect { id ->
            navController.navigate(EditorRoute(id)) { launchSingleTop = true }
        }
    }

    LaunchedEffect(navController, openWebEditorRequests) {
        openWebEditorRequests.collect {
            navController.navigate(SettingsCategoryRoute(SettingsCategory.WEB_EDITOR)) { launchSingleTop = true }
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
                        if (route != currentNotes) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onEditLabels = { closeDrawerThen { showEditLabels = true } },
                webEditorRunning = webEditorRunning,
                onWebEditor = { closeDrawerThen { navController.navigate(SettingsCategoryRoute(SettingsCategory.WEB_EDITOR)) } },
                onSettings = { closeDrawerThen { navController.navigate(SettingsRoute) } },
            )
        },
    ) {
        val appearance = LocalAppearance.current
        val duration = appearance.motionMillis(320)
        NavHost(
            navController,
            startDestination = NotesRoute(),
            modifier = Modifier.swipeToGoBack(enabled = canGoBack && !drawerState.isOpen),
            enterTransition = { slideInHorizontally(tween(duration)) { it } },
            exitTransition = { slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration), targetAlpha = 0.6f) },
            popEnterTransition = { slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration), initialAlpha = 0.6f) },
            popExitTransition = { slideOutHorizontally(tween(duration)) { it } },
            predictivePopEnterTransition = {
                slideInHorizontally(tween(duration, easing = LinearEasing)) { -it / 4 } +
                    fadeIn(tween(duration, easing = LinearEasing), initialAlpha = 0.6f)
            },
            predictivePopExitTransition = { slideOutHorizontally(tween(duration, easing = LinearEasing)) { it } },
        ) {
            composable<NotesRoute> {
                NotesScreen(
                    onMenu = { scope.launch { drawerState.open() } },
                    onOpenNote = { id -> navController.navigate(EditorRoute(id)) },
                    onNewNote = { secret -> navController.navigate(EditorRoute(secret = secret)) },
                )
            }
            composable<EditorRoute> {
                EditorScreen(
                    onBack = { navController.popBackStack() },
                    onOpenHistory = { id -> navController.navigate(HistoryRoute(id)) },
                )
            }
            composable<SettingsRoute> {
                SettingsHomeScreen(
                    onBack = { navController.popBackStack() },
                    onCategory = { category -> navController.navigate(SettingsCategoryRoute(category)) },
                    onStorage = { navController.navigate(StorageRoute) },
                )
            }
            composable<SettingsCategoryRoute> { entry ->
                SettingsCategoryScreen(
                    category = entry.toRoute<SettingsCategoryRoute>().category,
                    onBack = { navController.popBackStack() },
                    onErrorLog = { navController.navigate(ErrorLogRoute) },
                )
            }
            composable<StorageRoute> {
                StorageScreen(
                    onBack = { navController.popBackStack() },
                    onOpenHistory = { id -> navController.navigate(HistoryRoute(id)) },
                )
            }
            composable<HistoryRoute> { HistoryScreen(onBack = { navController.popBackStack() }) }
            composable<ErrorLogRoute> { ErrorLogScreen(onBack = { navController.popBackStack() }) }
        }
    }
    if (showEditLabels) EditLabelsDialog(onDismiss = { showEditLabels = false })
}
