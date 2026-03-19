package com.zonik.wear.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.zonik.wear.media.WearMediaManager
import com.zonik.wear.ui.screens.BrowseChildrenScreen
import com.zonik.wear.ui.screens.BrowseScreen
import com.zonik.wear.ui.screens.NowPlayingScreen
import com.zonik.wear.ui.screens.QueueScreen

object WearRoutes {
    const val NOW_PLAYING = "now_playing"
    const val BROWSE = "browse"
    const val BROWSE_CHILDREN = "browse_children/{parentId}"
    const val QUEUE = "queue"

    fun browseChildren(parentId: String) = "browse_children/$parentId"
}

@Composable
fun WearNavHost(mediaManager: WearMediaManager) {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearRoutes.NOW_PLAYING
    ) {
        composable(WearRoutes.NOW_PLAYING) {
            NowPlayingScreen(
                mediaManager = mediaManager,
                onBrowseClick = { navController.navigate(WearRoutes.BROWSE) },
                onQueueClick = { navController.navigate(WearRoutes.QUEUE) }
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
    }
}
