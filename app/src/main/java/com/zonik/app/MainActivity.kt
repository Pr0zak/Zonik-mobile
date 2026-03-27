package com.zonik.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.data.repository.SyncManager
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.zonik.app.ui.theme.ZonikColors
import com.zonik.app.ui.theme.ZonikShapes
import com.zonik.app.ui.util.isTv
import kotlinx.coroutines.launch
import com.zonik.app.ui.theme.ZonikTheme
import androidx.compose.foundation.focusGroup
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val playbackManager: PlaybackManager,
    private val syncManager: SyncManager
) : ViewModel() {

    val isLoggedIn = settingsRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val syncState = syncManager.syncState

    // Emits when playTracks is called (for auto-showing Now Playing immediately)
    val playbackStarted: Flow<Unit> = playbackManager.playbackRequested

    init {
        viewModelScope.launch {
            playbackManager.connect()
        }
        // Sync is manual or scheduled — no auto-sync on startup
    }

    fun syncNow() {
        viewModelScope.launch { syncManager.fullSync() }
    }
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHOW_NOW_PLAYING = "SHOW_NOW_PLAYING"
    }

    // Mutable state to signal that Now Playing should be shown
    internal val showNowPlayingFromIntent = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNowPlayingIntent(intent)
        setContent {
            ZonikTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ZonikApp(showNowPlayingFromIntent = showNowPlayingFromIntent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNowPlayingIntent(intent)
    }

    private fun handleNowPlayingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_NOW_PLAYING, false) == true) {
            showNowPlayingFromIntent.value = true
        }
    }
}

@Composable
fun ZonikApp(
    viewModel: MainViewModel = hiltViewModel(),
    showNowPlayingFromIntent: MutableState<Boolean> = mutableStateOf(false)
) {
    val rootNavController = rememberNavController()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    var showNowPlaying by remember { mutableStateOf(false) }

    // (isLoggedIn loads from DataStore via SharingStarted.Eagerly)

    // Auto-show Now Playing when a track starts playing (not on TV — TV has playback bar)
    val syncState by viewModel.syncState.collectAsState()
    val isTvDevice = isTv()
    LaunchedEffect(Unit) {
        viewModel.playbackStarted.collect {
            if (!isTvDevice) showNowPlaying = true
        }
    }

    // Show Now Playing when opened from notification
    val intentShowNowPlaying by showNowPlayingFromIntent
    LaunchedEffect(intentShowNowPlaying) {
        if (intentShowNowPlaying) {
            showNowPlaying = true
            showNowPlayingFromIntent.value = false
        }
    }

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
                if (isTv()) {
                    com.zonik.app.ui.tv.TvMainScreen(
                        onNavigateToAlbum = { albumId ->
                            rootNavController.navigate(Screen.AlbumDetail.createRoute(albumId))
                        },
                        onDisconnected = {
                            rootNavController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                } else {
                    MainScreen(
                        rootNavController = rootNavController,
                        onExpandNowPlaying = { showNowPlaying = true }
                    )
                }
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
            composable(Screen.Stats.route) {
                com.zonik.app.ui.screens.stats.StatsScreen(
                    onBack = { rootNavController.popBackStack() }
                )
            }
        }

        if (!isTvDevice) {
            AnimatedVisibility(
                visible = showNowPlaying,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(250)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200)
                )
            ) {
                NowPlayingScreen(onBack = { showNowPlaying = false })
            }
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
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    // Measure nav bar + mini player height for bottom padding
    val navBarHeight = 80.dp
    val miniPlayerHeight = 72.dp
    val miniPlayerBottomPadding = 8.dp

    // Sync pager swipes → nav bar selection
    val currentPage = pagerState.currentPage

    Box(modifier = Modifier.fillMaxSize()) {
        // Phone: swipeable HorizontalPager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = navBarHeight + miniPlayerHeight + miniPlayerBottomPadding),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onNavigateToLibraryTracks = {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onNavigateToAlbum = { albumId ->
                        rootNavController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    }
                )
                1 -> LibraryScreen(
                    onNavigateToAlbum = { albumId ->
                        rootNavController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    },
                    onNavigateToArtist = { artistId ->
                        rootNavController.navigate(Screen.ArtistDetail.createRoute(artistId))
                    }
                )
                2 -> SearchScreen(
                    onNavigateToAlbum = { albumId ->
                        rootNavController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    },
                    onNavigateToArtist = { artistId ->
                        rootNavController.navigate(Screen.ArtistDetail.createRoute(artistId))
                    }
                )
                3 -> DownloadsScreen()
                4 -> SettingsScreen(
                    onDisconnected = {
                        rootNavController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToStats = {
                        rootNavController.navigate(Screen.Stats.route)
                    }
                )
            }
        }

        // Floating MiniPlayer + Glass Nav Bar at bottom
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // Floating MiniPlayer
            MiniPlayer(
                onClick = onExpandNowPlaying,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = miniPlayerBottomPadding)
            )

            // Glass Navigation Bar
            Surface(
                color = ZonikColors.navBarBg,
                shape = ZonikShapes.navBarShape,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.focusGroup()
                ) {
                    tabs.forEachIndexed { index, tabItem ->
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
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            selected = currentPage == index,
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = Color.White.copy(alpha = 0.4f),
                                unselectedTextColor = Color.White.copy(alpha = 0.4f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        }
    }
}
