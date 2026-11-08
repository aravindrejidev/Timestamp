package com.timestamp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.timestamp.app.ui.Routes
import com.timestamp.app.ui.components.FloatingDock
import com.timestamp.app.ui.screens.ExportScreen
import com.timestamp.app.ui.screens.HomeScreen
import com.timestamp.app.ui.screens.ImportScreen
import com.timestamp.app.ui.screens.SettingsScreen
import com.timestamp.app.ui.screens.StudioScreen
import com.timestamp.app.ui.theme.LocalBackgroundBrush
import com.timestamp.app.ui.theme.TimestampTheme
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

        val meta by editorVm.meta.collectAsStateWithLifecycle()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val showDock = route != null && route != Routes.STUDIO && !imeVisible

        fun go(target: String) {
            if (target == route) return
            nav.navigate(target) {
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
        }

        Box(Modifier.fillMaxSize().background(LocalBackgroundBrush.current)) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    if (showDock) {
                        FloatingDock(
                            current = route,
                            studioEnabled = meta != null,
                            onNavigate = { go(it) }
                        )
                    }
                }
            ) { inner ->
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    modifier = Modifier.padding(bottom = inner.calculateBottomPadding())
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            vm = homeVm,
                            onNew = { go(Routes.IMPORT) },
                            onOpen = { id ->
                                editorVm.openProject(id)
                                nav.navigate(Routes.STUDIO)
                            },
                            onDelete = { id ->
                                homeVm.delete(id)
                                editorVm.onDeleted(id)
                            },
                            onSettings = { go(Routes.SETTINGS) }
                        )
                    }
                    composable(Routes.IMPORT) {
                        ImportScreen(importVm) { id ->
                            editorVm.openProject(id)
                            nav.navigate(Routes.STUDIO) { popUpTo(Routes.HOME) }
                        }
                    }
                    composable(Routes.STUDIO) {
                        StudioScreen(
                            vm = editorVm,
                            onBack = { nav.popBackStack() },
                            onExport = { nav.navigate(Routes.EXPORT) }
                        )
                    }
                    composable(Routes.EXPORT) {
                        ExportScreen(editorVm, settings, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(settings)
                    }
                }
            }
        }
    }
}
