package com.timestamp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import com.timestamp.app.ui.Routes
import com.timestamp.app.ui.screens.ExportScreen
import com.timestamp.app.ui.screens.HomeScreen
import com.timestamp.app.ui.screens.ImportScreen
import com.timestamp.app.ui.screens.PreviewScreen
import com.timestamp.app.ui.screens.SettingsScreen
import com.timestamp.app.ui.screens.StudioScreen
import com.timestamp.app.ui.screens.UpdateHost
import com.timestamp.app.ui.theme.LocalBackgroundBrush
import com.timestamp.app.ui.theme.TimestampTheme
import com.timestamp.app.update.UpdateViewModel
import com.timestamp.app.viewmodel.EditorViewModel
import com.timestamp.app.viewmodel.HomeViewModel
import com.timestamp.app.viewmodel.ImportViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { TimestampRoot() }
    }
}

@Composable
private fun TimestampRoot() {
    val app = LocalContext.current.applicationContext as TimestampApp
    val settings = app.settings
    val amoled by settings.amoled.collectAsStateWithLifecycle()
    val glow by settings.glow.collectAsStateWithLifecycle()

    TimestampTheme(amoled = amoled, glow = glow) {
        val nav = rememberNavController()
        val homeVm: HomeViewModel = viewModel()
        val importVm: ImportViewModel = viewModel()
        val editorVm: EditorViewModel = viewModel()
        val updateVm: UpdateViewModel = viewModel()

        // Look for a new GitHub release every time the app opens; the popup appears by itself.
        LaunchedEffect(Unit) { updateVm.checkOnStart() }

        Box(Modifier.fillMaxSize().background(LocalBackgroundBrush.current)) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    fadeIn(tween(240)) + slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 14 }
                },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(240)) },
                popExitTransition = {
                    fadeOut(tween(180)) + slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 14 }
                }
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        vm = homeVm,
                        onNew = { nav.navigate(Routes.IMPORT) },
                        onOpen = { id ->
                            editorVm.openProject(id)
                            nav.navigate(Routes.STUDIO)
                        },
                        onDelete = { id ->
                            homeVm.delete(id)
                            editorVm.onDeleted(id)
                        },
                        onSettings = { nav.navigate(Routes.SETTINGS) }
                    )
                }
                composable(Routes.IMPORT) {
                    ImportScreen(
                        vm = importVm,
                        onBack = { nav.popBackStack() },
                        onCreated = { id ->
                            editorVm.openProject(id)
                            nav.navigate(Routes.STUDIO) { popUpTo(Routes.HOME) }
                        }
                    )
                }
                composable(Routes.STUDIO) {
                    StudioScreen(
                        vm = editorVm,
                        onBack = { nav.popBackStack() },
                        onExport = { nav.navigate(Routes.EXPORT) },
                        onPreview = { nav.navigate(Routes.PREVIEW) }
                    )
                }
                composable(Routes.EXPORT) {
                    ExportScreen(
                        vm = editorVm,
                        settings = settings,
                        onBack = { nav.popBackStack() },
                        onPreview = { nav.navigate(Routes.PREVIEW) }
                    )
                }
                composable(
                    Routes.PREVIEW,
                    // slides up like a sheet, slides down when closed
                    enterTransition = {
                        slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(250))
                    },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(240)) },
                    popExitTransition = {
                        slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(250))
                    }
                ) {
                    PreviewScreen(vm = editorVm, onBack = { nav.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(settings = settings, update = updateVm, onBack = { nav.popBackStack() })
                }
            }
            UpdateHost(updateVm)
        }
    }
}
