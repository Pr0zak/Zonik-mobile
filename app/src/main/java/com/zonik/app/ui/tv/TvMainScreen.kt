package com.zonik.app.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.media.PlaybackManager
import com.zonik.app.model.Album
import com.zonik.app.model.Track
import com.zonik.app.ui.components.CoverArt
import com.zonik.app.ui.theme.ZonikColors
import com.zonik.app.ui.theme.ZonikShapes
import com.zonik.app.ui.util.formatDuration
import com.zonik.app.ui.util.formatDurationMs
import com.zonik.app.ui.util.tvFocusHighlight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class TvViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val libraryRepository: LibraryRepository,
    private val syncManager: com.zonik.app.data.repository.SyncManager,
    private val logUploader: com.zonik.app.data.api.LogUploader
) : ViewModel() {

    // Playback state (delegated from PlaybackManager)
    val currentTrack: StateFlow<Track?> = playbackManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val queue: StateFlow<List<Track>> = playbackManager.queue

    // Library data
    val albums: StateFlow<List<Album>> = libraryRepository.getAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tracks: StateFlow<List<Track>> = libraryRepository.getAllTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTracks: StateFlow<List<Track>> = libraryRepository.getRecentTracks(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recentAlbums = MutableStateFlow<List<Album>>(emptyList())
    val recentAlbums: StateFlow<List<Album>> = _recentAlbums.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.getRecentAlbums(20).collect { _recentAlbums.value = it }
        }
    }

    fun shuffleMix() {
        viewModelScope.launch {
            try {
                val songs = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    libraryRepository.getRandomSongs(100)
                }
                if (songs.isNotEmpty()) {
                    playbackManager.playTracks(songs)
                }
            } catch (e: Exception) {
                com.zonik.app.data.DebugLog.e("TvVM", "Shuffle mix failed", e)
            }
        }
    }

    fun playTrack(track: Track) {
        val allTracks = tracks.value
        val index = allTracks.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            playbackManager.playTracks(allTracks, index)
        } else {
            playbackManager.playTracks(listOf(track))
        }
    }

    fun playAlbum(albumId: String) {
        viewModelScope.launch {
            try {
                val (_, albumTracks) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    libraryRepository.getAlbumDetail(albumId)
                }
                if (albumTracks.isNotEmpty()) {
                    playbackManager.playTracks(albumTracks)
                }
            } catch (_: Exception) {}
        }
    }

    fun togglePlayPause() = playbackManager.togglePlayPause()
    fun skipNext() = playbackManager.skipNext()
    fun skipPrevious() = playbackManager.skipPrevious()
    fun getCurrentPosition(): Long = playbackManager.getCurrentPosition()
    fun getDuration(): Long = playbackManager.getDuration()

    val syncState = syncManager.syncState

    fun syncNow() {
        viewModelScope.launch { syncManager.fullSync() }
    }

    fun uploadLogs() {
        viewModelScope.launch {
            logUploader.uploadLogsToServer()
        }
    }

    fun checkForUpdate(context: android.content.Context) {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://github.com/Pr0zak/Zonik-mobile/releases/latest")
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("TvVM", "Check update failed: ${e.message}")
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Navigation definitions
// ──────────────────────────────────────────────────────────────────────────────

private enum class TvTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LIBRARY("Library", Icons.Default.VideoLibrary),
    SETTINGS("Settings", Icons.Default.Settings)
}

private enum class LibrarySubTab(val label: String) {
    ALBUMS("Albums"),
    TRACKS("Tracks"),
    FAVORITES("Favorites")
}

// ──────────────────────────────────────────────────────────────────────────────
// Colors
// ──────────────────────────────────────────────────────────────────────────────

private val TvBackground = Color(0xFF151320)
private val TvCardBackground = Color(0xFF1E1C2A)

// ──────────────────────────────────────────────────────────────────────────────
// Main Screen — NavigationDrawer layout
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMainScreen(
    onNavigateToAlbum: (String) -> Unit = {},
    onDisconnected: () -> Unit = {},
    viewModel: TvViewModel = hiltViewModel()
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var selectedTab by remember { mutableStateOf(TvTab.HOME) }

    BackHandler(enabled = selectedTab != TvTab.HOME) {
        selectedTab = TvTab.HOME
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { viewModel.togglePlayPause(); true }
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!isPlaying) viewModel.togglePlayPause(); true }
                        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (isPlaying) viewModel.togglePlayPause(); true }
                        android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> { viewModel.skipNext(); true }
                        android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { viewModel.skipPrevious(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        // TV safe area padding: 48dp horizontal, 27dp vertical
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, top = 27.dp, bottom = 27.dp)
        ) {
            NavigationDrawer(
                drawerContent = { drawerValue ->
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Logo at top of drawer
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(
                                        id = com.zonik.app.R.drawable.ic_logo_z
                                    ),
                                    contentDescription = "Zonik",
                                    tint = ZonikColors.gold,
                                    modifier = Modifier.size(28.dp)
                                )
                                if (drawerValue == DrawerValue.Open) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Zonik",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Navigation items
                        TvTab.entries.forEach { tab ->
                            val isSelected = tab == selectedTab
                            NavigationDrawerItem(
                                selected = isSelected,
                                onClick = { selectedTab = tab },
                                leadingContent = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = if (isSelected) ZonikColors.gold
                                            else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                content = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) ZonikColors.gold
                                            else Color.White.copy(alpha = 0.7f),
                                        fontWeight = if (isSelected) FontWeight.Bold
                                            else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            ) {
                // Content area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp)
                ) {
                    when (selectedTab) {
                        TvTab.HOME -> TvHomeContent(
                            viewModel = viewModel,
                            onAlbumClick = onNavigateToAlbum
                        )
                        TvTab.LIBRARY -> TvLibraryContent(
                            viewModel = viewModel,
                            onAlbumClick = onNavigateToAlbum
                        )
                        TvTab.SETTINGS -> TvSettingsContent(
                            viewModel = viewModel,
                            onDisconnected = onDisconnected
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Home Tab — Shuffle + Now Playing card with integrated controls
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvHomeContent(
    viewModel: TvViewModel,
    onAlbumClick: (String) -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val recentAlbums by viewModel.recentAlbums.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ── Shuffle Mix button ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(ZonikShapes.buttonShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(ZonikColors.gradientStart, ZonikColors.gradientEnd)
                    ),
                    ZonikShapes.buttonShape
                )
                .tvFocusHighlight(ZonikShapes.buttonShape)
                .clickable { viewModel.shuffleMix() }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Shuffle Mix",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Now Playing card ────────────────────────────────────────────
        if (currentTrack != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.titleMedium,
                color = ZonikColors.gold,
                fontWeight = FontWeight.Bold,
                letterSpacing = MaterialTheme.typography.titleMedium.letterSpacing * 1.5f
            )
            Spacer(modifier = Modifier.height(12.dp))

            TvNowPlayingCard(
                track = currentTrack!!,
                isPlaying = isPlaying,
                viewModel = viewModel
            )
        }

        // ── Recent Albums row ───────────────────────────────────────────
        if (recentAlbums.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "RECENT ALBUMS",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                letterSpacing = MaterialTheme.typography.titleMedium.letterSpacing * 1.5f
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                items(recentAlbums, key = { it.id }) { album ->
                    TvAlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album.id) },
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        }

        // Bottom spacing
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Now Playing card — album art + info + controls + progress, all in one card
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvNowPlayingCard(
    track: Track,
    isPlaying: Boolean,
    viewModel: TvViewModel
) {
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // Poll playback position
    LaunchedEffect(isPlaying, track.id) {
        while (true) {
            positionMs = viewModel.getCurrentPosition()
            durationMs = viewModel.getDuration()
            delay(200L)
        }
    }

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvCardBackground, ZonikShapes.cardShape)
            .clip(ZonikShapes.cardShape)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Album art
            CoverArt(
                coverArtId = track.coverArt,
                contentDescription = track.title,
                modifier = Modifier
                    .size(150.dp)
                    .clip(ZonikShapes.coverArtLargeShape),
                size = 600
            )

            Spacer(modifier = Modifier.width(24.dp))

            // Track info + controls
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Artist
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Album
                Text(
                    text = track.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Transport controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Skip Previous
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                            .clip(CircleShape)
                            .tvFocusHighlight(CircleShape)
                            .clickable { viewModel.skipPrevious() }
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Play/Pause — gradient circle
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ZonikColors.gradientStart, ZonikColors.gradientEnd)
                                ),
                                CircleShape
                            )
                            .clip(CircleShape)
                            .tvFocusHighlight(CircleShape)
                            .clickable { viewModel.togglePlayPause() }
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Skip Next
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                            .clip(CircleShape)
                            .tvFocusHighlight(CircleShape)
                            .clickable { viewModel.skipNext() }
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Progress bar ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(ZonikColors.gold)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationMs(positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = formatDurationMs(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Library Tab
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvLibraryContent(
    viewModel: TvViewModel,
    onAlbumClick: (String) -> Unit
) {
    val albums by viewModel.albums.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    var subTab by remember { mutableStateOf(LibrarySubTab.ALBUMS) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Sub-tab row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LibrarySubTab.entries.forEach { tab ->
                val isSelected = tab == subTab
                Box(
                    modifier = Modifier
                        .clip(ZonikShapes.buttonShape)
                        .background(
                            if (isSelected) Brush.horizontalGradient(
                                listOf(ZonikColors.gradientStart, ZonikColors.gradientEnd)
                            ) else Brush.horizontalGradient(
                                listOf(TvCardBackground, TvCardBackground)
                            ),
                            ZonikShapes.buttonShape
                        )
                        .tvFocusHighlight(ZonikShapes.buttonShape)
                        .clickable { subTab = tab }
                        .focusable()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid content
        when (subTab) {
            LibrarySubTab.ALBUMS -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albums, key = { it.id }) { album ->
                        TvAlbumGridCard(
                            album = album,
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }
            }
            LibrarySubTab.TRACKS -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tracks, key = { it.id }) { track ->
                        TvTrackGridCard(
                            track = track,
                            onClick = { viewModel.playTrack(track) }
                        )
                    }
                }
            }
            LibrarySubTab.FAVORITES -> {
                val favorites = tracks.filter { it.starred }
                if (favorites.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No favorites yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(favorites, key = { it.id }) { track ->
                            TvTrackGridCard(
                                track = track,
                                onClick = { viewModel.playTrack(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Settings Tab
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvSettingsContent(
    viewModel: TvViewModel,
    onDisconnected: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val syncState by viewModel.syncState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Sync
        TvSettingsButton(
            icon = Icons.Default.Sync,
            title = if (syncState.isSyncing) "Syncing..." else "Sync Library",
            subtitle = "Sync tracks, albums, and artists from server",
            onClick = { viewModel.syncNow() },
            enabled = !syncState.isSyncing,
            isLoading = syncState.isSyncing
        )

        // Upload Logs
        TvSettingsButton(
            icon = Icons.Default.Upload,
            title = "Upload Logs",
            subtitle = "Send debug logs to server for troubleshooting",
            onClick = { viewModel.uploadLogs() }
        )

        // Check Update
        TvSettingsButton(
            icon = Icons.Default.SystemUpdate,
            title = "Check for Update",
            subtitle = "Open GitHub releases page",
            onClick = { viewModel.checkForUpdate(context) }
        )

        // Disconnect
        TvSettingsButton(
            icon = Icons.Default.Logout,
            title = "Disconnect",
            subtitle = "Log out from server",
            onClick = onDisconnected,
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun TvSettingsButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    tint: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvCardBackground, ZonikShapes.cardShape)
            .clip(ZonikShapes.cardShape)
            .tvFocusHighlight(ZonikShapes.cardShape)
            .clickable(enabled = enabled, onClick = onClick)
            .focusable()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = ZonikColors.gold
            )
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Card Components
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvAlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(ZonikShapes.cardShape)
            .background(TvCardBackground, ZonikShapes.cardShape)
            .tvFocusHighlight(ZonikShapes.cardShape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(8.dp)
    ) {
        CoverArt(
            coverArtId = album.coverArt,
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ZonikShapes.coverArtShape),
            size = 300
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvTrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(ZonikShapes.cardShape)
            .background(TvCardBackground, ZonikShapes.cardShape)
            .tvFocusHighlight(ZonikShapes.cardShape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(8.dp)
    ) {
        CoverArt(
            coverArtId = track.coverArt,
            contentDescription = track.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ZonikShapes.coverArtShape),
            size = 300
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvAlbumGridCard(
    album: Album,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(ZonikShapes.cardShape)
            .background(TvCardBackground, ZonikShapes.cardShape)
            .tvFocusHighlight(ZonikShapes.cardShape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(8.dp)
    ) {
        CoverArt(
            coverArtId = album.coverArt,
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ZonikShapes.coverArtShape),
            size = 300
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (album.year != null) {
            Text(
                text = album.year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun TvTrackGridCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(ZonikShapes.cardShape)
            .background(TvCardBackground, ZonikShapes.cardShape)
            .tvFocusHighlight(ZonikShapes.cardShape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(8.dp)
    ) {
        CoverArt(
            coverArtId = track.coverArt,
            contentDescription = track.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ZonikShapes.coverArtShape),
            size = 300
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (track.duration > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatDuration(track.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}
