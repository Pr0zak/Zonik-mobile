package com.zonik.wear.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.zonik.wear.WearApp
import com.zonik.wear.media.WearMediaManager
import com.zonik.wear.ui.screens.NowPlayingScreen
import com.zonik.wear.ui.screens.QueueScreen
import com.zonik.wear.ui.screens.pairing.PairingScreen
import com.zonik.wear.ui.screens.pairing.PairingViewModel
import com.zonik.wear.ui.screens.settings.WearSettingsScreen

object WearRoutes {
    const val PAIRING = "pairing"
    const val NOW_PLAYING = "now_playing"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
}

@Composable
fun WearNavHost(mediaManager: WearMediaManager) {
    val navController = rememberSwipeDismissableNavController()
    val ctx = LocalContext.current
    val app = ctx.applicationContext as WearApp
    val serverConfig by app.settings.serverConfig.collectAsStateWithLifecycle(initialValue = null)
    val startDestination = if (serverConfig == null) WearRoutes.PAIRING else WearRoutes.NOW_PLAYING

    LaunchedEffect(serverConfig) {
        val current = navController.currentDestination?.route
        when {
            serverConfig == null && current != WearRoutes.PAIRING -> {
                navController.navigate(WearRoutes.PAIRING) {
                    popUpTo(0) { inclusive = true }
                }
            }
            serverConfig != null && current == WearRoutes.PAIRING -> {
                navController.navigate(WearRoutes.NOW_PLAYING) {
                    popUpTo(WearRoutes.PAIRING) { inclusive = true }
                }
            }
        }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(WearRoutes.PAIRING) {
            val vm = viewModel { PairingViewModel(app.settings) }
            PairingScreen(
                viewModel = vm,
                onPaired = {
                    navController.navigate(WearRoutes.NOW_PLAYING) {
                        popUpTo(WearRoutes.PAIRING) { inclusive = true }
                    }
                }
            )
        }

        composable(WearRoutes.NOW_PLAYING) {
            NowPlayingScreen(
                mediaManager = mediaManager,
                onQueueClick = { navController.navigate(WearRoutes.QUEUE) },
                onSettingsClick = { navController.navigate(WearRoutes.SETTINGS) },
            )
        }

        composable(WearRoutes.QUEUE) {
            QueueScreen(mediaManager = mediaManager)
        }

        composable(WearRoutes.SETTINGS) {
            WearSettingsScreen(
                onRePair = { navController.popBackStack() }
            )
        }
    }
}
