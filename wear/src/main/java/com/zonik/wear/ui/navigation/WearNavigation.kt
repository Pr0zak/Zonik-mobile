package com.zonik.wear.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.zonik.wear.WearApp
import com.zonik.wear.media.WearMediaManager
import com.zonik.wear.ui.screens.BrowseChildrenScreen
import com.zonik.wear.ui.screens.BrowseScreen
import com.zonik.wear.ui.screens.NowPlayingScreen
import com.zonik.wear.ui.screens.QueueScreen
import com.zonik.wear.ui.screens.pairing.PairingScreen
import com.zonik.wear.ui.screens.pairing.PairingViewModel
import com.zonik.wear.ui.screens.settings.WearSettingsScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

object WearRoutes {
    const val PAIRING = "pairing"
    const val NOW_PLAYING = "now_playing"
    const val BROWSE = "browse"
    const val BROWSE_CHILDREN = "browse_children/{parentId}"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"

    fun browseChildren(parentId: String) = "browse_children/$parentId"
}

@Composable
fun WearNavHost(mediaManager: WearMediaManager) {
    val navController = rememberSwipeDismissableNavController()
    val ctx = LocalContext.current
    val app = ctx.applicationContext as WearApp
    val serverConfig by app.settings.serverConfig.collectAsStateWithLifecycle(initialValue = null)
    // Once we have a ServerConfig the pairing screen is no longer relevant.
    val startDestination = if (serverConfig == null) WearRoutes.PAIRING else WearRoutes.NOW_PLAYING

    // React to runtime config changes — re-pair from Settings sends us back
    // to the pairing screen; a successful pair takes us out of it.
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
                onBrowseClick = { navController.navigate(WearRoutes.BROWSE) },
                onQueueClick = { navController.navigate(WearRoutes.QUEUE) },
                onSettingsClick = { navController.navigate(WearRoutes.SETTINGS) },
            )
        }

        composable(WearRoutes.BROWSE) {
            BrowseScreen(
                mediaManager = mediaManager,
                onNodeClick = { nodeId ->
                    navController.navigate(WearRoutes.browseChildren(nodeId))
                }
            )
        }

        composable(
            route = WearRoutes.BROWSE_CHILDREN,
            arguments = listOf(navArgument("parentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val parentId = backStackEntry.arguments?.getString("parentId") ?: return@composable
            BrowseChildrenScreen(
                mediaManager = mediaManager,
                parentId = parentId,
                onNodeClick = { nodeId ->
                    navController.navigate(WearRoutes.browseChildren(nodeId))
                }
            )
        }

        composable(WearRoutes.QUEUE) {
            QueueScreen(mediaManager = mediaManager)
        }

        composable(WearRoutes.SETTINGS) {
            WearSettingsScreen(
                onRePair = {
                    // Config is already cleared; the LaunchedEffect above
                    // will route us back to PAIRING. We also pop the
                    // settings entry off the back stack so swipe-dismiss
                    // doesn't land back here.
                    navController.popBackStack()
                }
            )
        }
    }
}
