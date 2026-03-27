package com.zonik.app.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
// Tab definitions
// ──────────────────────────────────────────────────────────────────────────────

private enum class TvTab(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    SEARCH("Search"),
    SETTINGS("Settings")
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
// Main Screen
// ──────────────────────────────────────────────────────────────────────────────

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
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top navigation bar
            TvTopNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            // Content area — fills available space above playback bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
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
                    TvTab.SEARCH -> TvSearchPlaceholder()
                    TvTab.SETTINGS -> com.zonik.app.ui.screens.settings.SettingsScreen(
                        onDisconnected = onDisconnected,
                        onNavigateToStats = {}
                    )
                }
            }

            // Playback bar
            if (currentTrack != null) {
                TvPlaybackBar(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    viewModel = viewModel
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Top Navigation
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvTopNav(
    selectedTab: TvTab,
    onTabSelected: (TvTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 27.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.zonik.app.R.drawable.ic_logo_z),
            contentDescription = "Zonik",
            tint = ZonikColors.gold,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Zonik",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(48.dp))

        // Tab items
        TvTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Text(
                text = tab.label,
                style = MaterialTheme.typography.titleLarge,
                color = if (isSelected) ZonikColors.gold else Color.White.copy(alpha = 0.6f),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(ZonikShapes.buttonShape)
                    .tvFocusHighlight(ZonikShapes.buttonShape)
                    .clickable { onTabSelected(tab) }
                    .focusable()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Home Tab
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvHomeContent(
    viewModel: TvViewModel,
    onAlbumClick: (String) -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val recentTracks by viewModel.recentTracks.collectAsState()
    val recentAlbums by viewModel.recentAlbums.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp)
    ) {
        // Shuffle Mix button — prominent, first focusable item
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
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
                    modifier = Modifier.size(24.dp)
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

        // Now Playing section
        if (currentTrack != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TvCardBackground, ZonikShapes.cardShape)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(
                    coverArtId = currentTrack!!.coverArt,
                    contentDescription = currentTrack!!.title,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(ZonikShapes.coverArtLargeShape),
                    size = 600
                )
                Spacer(modifier = Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack!!.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentTrack!!.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentTrack!!.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Recently Played Albums
        if (recentAlbums.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Recently Played",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 48.dp)
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

        // Recent Tracks
        if (recentTracks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Recent Tracks",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 48.dp)
            ) {
                items(recentTracks, key = { it.id }) { track ->
                    TvTrackCard(
                        track = track,
                        onClick = { viewModel.playTrack(track) },
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        }

        // Bottom spacing for playback bar clearance
        Spacer(modifier = Modifier.height(80.dp))
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
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
    ) {
        // Sub-tab filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LibrarySubTab.entries.forEach { tab ->
                val isSelected = tab == subTab
                FilterChip(
                    selected = isSelected,
                    onClick = { subTab = tab },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZonikColors.gradientStart,
                        selectedLabelColor = Color.White,
                        containerColor = TvCardBackground,
                        labelColor = Color.White.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .tvFocusHighlight(ZonikShapes.buttonShape)
                        .focusable()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Grid content based on sub-tab
        when (subTab) {
            LibrarySubTab.ALBUMS -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
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
                    contentPadding = PaddingValues(bottom = 80.dp),
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
                        contentPadding = PaddingValues(bottom = 80.dp),
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
// Placeholder tabs
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvSearchPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Search coming soon",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun TvSettingsPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Settings coming soon",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Playback Bar
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvPlaybackBar(
    track: Track,
    isPlaying: Boolean,
    viewModel: TvViewModel
) {
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // Poll playback position
    LaunchedEffect(isPlaying) {
        while (true) {
            positionMs = viewModel.getCurrentPosition()
            durationMs = viewModel.getDuration()
            delay(200L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZonikColors.glassBg)
    ) {
        // Thin progress bar at top of playback bar
        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(ZonikColors.gold)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover art
            CoverArt(
                coverArtId = track.coverArt,
                contentDescription = track.title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(ZonikShapes.coverArtShape),
                size = 100
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Transport controls
            IconButton(
                onClick = { viewModel.skipPrevious() },
                modifier = Modifier
                    .size(40.dp)
                    .tvFocusHighlight(CircleShape)
                    .focusable()
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(ZonikColors.gradientStart, ZonikColors.gradientEnd)
                        ),
                        CircleShape
                    )
                    .tvFocusHighlight(CircleShape)
                    .focusable()
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { viewModel.skipNext() },
                modifier = Modifier
                    .size(40.dp)
                    .tvFocusHighlight(CircleShape)
                    .focusable()
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Position / Duration
            Text(
                text = "${formatDurationMs(positionMs)} / ${formatDurationMs(durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
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
                .height(144.dp)
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
                .height(144.dp)
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
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
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
                .height(150.dp)
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
                .height(150.dp)
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
