package com.zonik.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.media.PlaybackManager
import com.zonik.app.ui.components.MiniPlayer
import com.zonik.app.ui.navigation.MainTab
import com.zonik.app.ui.navigation.Screen
import com.zonik.app.ui.screens.downloads.DownloadsScreen
import com.zonik.app.ui.screens.home.HomeScreen
import com.zonik.app.ui.screens.library.AlbumDetailScreen
import com.zonik.app.ui.screens.library.ArtistDetailScreen
import com.zonik.app.ui.screens.library.LibraryScreen
import com.zonik.app.ui.screens.login.LoginScreen
import com.zonik.app.ui.screens.nowplaying.NowPlayingScreen
import com.zonik.app.ui.screens.playlists.PlaylistsScreen
import com.zonik.app.ui.screens.search.SearchScreen
import com.zonik.app.ui.screens.settings.SettingsScreen
import com.zonik.app.ui.theme.ZonikTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    val isLoggedIn = settingsRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            playbackManager.connect()
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZonikTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ZonikApp()
                }
            }
        }
    }
}

@Composable
fun ZonikApp(viewModel: MainViewModel = hiltViewModel()) {
    val rootNavController = rememberNavController()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    var showNowPlaying by remember { mutableStateOf(false) }

    val startDestination = if (isLoggedIn) Screen.Main.route else Screen.Login.route

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = rootNavController,
            startDestination = startDestination
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        rootNavController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Main.route) {
                MainScreen(
                    rootNavController = rootNavController,
                    onExpandNowPlaying = { showNowPlaying = true }
                )
            }
            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType })
            ) {
                AlbumDetailScreen(
                    onNavigateBack = { rootNavController.popBackStack() },
                    onNavigateToArtist = { artistId ->
                        rootNavController.navigate(Screen.ArtistDetail.createRoute(artistId))
                    }
                )
            }
            composable(
                route = Screen.ArtistDetail.route,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType })
            ) {
                ArtistDetailScreen(
                    onNavigateBack = { rootNavController.popBackStack() },
                    onNavigateToAlbum = { albumId ->
                        rootNavController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    }
                )
            }
        }

        if (showNowPlaying) {
            NowPlayingScreen(
                onBack = { showNowPlaying = false }
            )
        }
    }
}

private data class TabItem(
    val tab: MainTab,
    val icon: ImageVector,
    val label: String
)

private val tabs = listOf(
    TabItem(MainTab.Home, Icons.Filled.Home, MainTab.Home.label),
    TabItem(MainTab.Library, Icons.Filled.LibraryMusic, MainTab.Library.label),
    TabItem(MainTab.Search, Icons.Filled.Search, MainTab.Search.label),
    TabItem(MainTab.Downloads, Icons.Filled.Download, MainTab.Downloads.label),
    TabItem(MainTab.Settings, Icons.Filled.Settings, MainTab.Settings.label)
)

@Composable
fun MainScreen(
    rootNavController: NavHostController,
    onExpandNowPlaying: () -> Unit
) {
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(onClick = onExpandNowPlaying)
                NavigationBar {
                    tabs.forEach { tabItem ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = tabItem.icon,
                                    contentDescription = tabItem.label
                                )
                            },
                            label = {
                                Text(
                                    text = tabItem.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == tabItem.tab.route
                            } == true,
                            onClick = {
                                tabNavController.navigate(tabItem.tab.route) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = MainTab.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MainTab.Home.route) {
                HomeScreen()
            }
            composable(MainTab.Library.route) {
                LibraryScreen(
                    onNavigateToAlbum = { albumId ->
                        rootNavController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    },
                    onNavigateToArtist = { artistId ->
                        rootNavController.navigate(Screen.ArtistDetail.createRoute(artistId))
                    }
                )
            }
            composable(MainTab.Search.route) {
                SearchScreen(
                    onNavigateToAlbum = { albumId ->
                        rootNavController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    },
                    onNavigateToArtist = { artistId ->
                        rootNavController.navigate(Screen.ArtistDetail.createRoute(artistId))
                    }
                )
            }
            composable(MainTab.Downloads.route) {
                DownloadsScreen()
            }
            composable(MainTab.Settings.route) {
                SettingsScreen(
                    onDisconnected = {
                        rootNavController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
