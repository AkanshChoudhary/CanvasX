package com.my_app.art_collab.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.my_app.art_collab.ui.screens.auth.AuthScreen
import com.my_app.art_collab.ui.screens.home.HomeScreen
import com.my_app.art_collab.ui.screens.canvas_editor.CanvasEditorScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: Screen
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(300)) }
    ) {
        // ── Auth ─────────────────────────────────────────────────────────────
        composable<Screen.Auth> {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Screen.Home) {
                        popUpTo(Screen.Auth) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Home ─────────────────────────────────────────────────────────────
        composable<Screen.Home> {
            HomeScreen(
                onOpenNewCanvas = { canvasId, name, widthPx, heightPx ->
                    navController.navigate(
                        Screen.NewCanvas(
                            canvasId = canvasId,
                            name = name,
                            widthPx = widthPx,
                            heightPx = heightPx
                        )
                    )
                },
                onLoggedOut = {
                    navController.navigate(Screen.Auth) {
                        popUpTo(Screen.Home) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<Screen.NewCanvas> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.NewCanvas>()
            CanvasEditorScreen(
                canvasId = route.canvasId,
                name = route.name,
                widthPx = route.widthPx,
                heightPx = route.heightPx,
                onNavigateBack = { navController.popBackStack() }
            )
        }

    }
}
