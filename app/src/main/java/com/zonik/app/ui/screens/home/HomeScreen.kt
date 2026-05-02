package com.zonik.app.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.zonik.app.ui.theme.ZonikShapes
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.DebugLog
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.data.repository.SyncManager
import com.zonik.app.data.repository.SyncState
import com.zonik.app.media.PlaybackManager
import com.zonik.app.model.Track
import com.zonik.app.ui.components.CoverArt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.zonik.app.ui.util.tvFocusHighlight
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackManager: PlaybackManager,
    private val syncManager: SyncManager
) : ViewModel() {

    val recentTracks = libraryRepository.getRecentTracks(limit = 10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed = playbackManager.recentlyPlayed

    val syncState = syncManager.syncState

    fun syncNow() {
        viewModelScope.launch { syncManager.fullSync() }
    }

    fun shuffleMix() {
        viewModelScope.launch {
            try {
                DebugLog.d("HomeViewModel", "Starting shuffle mix")
                val tracks = libraryRepository.getRandomSongs(count = 100)
                if (tracks.isNotEmpty()) {
                    playbackManager.setShuffleEnabled(false)
                    playbackManager.playTracks(tracks)
                    DebugLog.d("HomeViewModel", "Shuffle mix started with ${tracks.size} tracks")
                } else {
                    DebugLog.w("HomeViewModel", "Shuffle mix: no tracks returned")
                }
            } catch (e: Exception) {
                DebugLog.e("HomeViewModel", "Shuffle mix failed", e)
            }
        }
    }

    fun shuffleRecentlyAdded() {
        viewModelScope.launch {
            try {
                val tracks = libraryRepository.getRecentlyAddedTracks(limit = 100).shuffled()
                if (tracks.isNotEmpty()) {
                    playbackManager.setShuffleEnabled(false)
                    playbackManager.playTracks(tracks)
                }
            } catch (e: Exception) {
                DebugLog.e("HomeViewModel", "Shuffle recently-added failed", e)
            }
        }
    }

    fun shuffleNewestByYear() {
        viewModelScope.launch {
            try {
                val tracks = libraryRepository.getNewestByYearTracks(limit = 100).shuffled()
                if (tracks.isNotEmpty()) {
                    playbackManager.setShuffleEnabled(false)
                    playbackManager.playTracks(tracks)
                }
            } catch (e: Exception) {
                DebugLog.e("HomeViewModel", "Shuffle newest-by-year failed", e)
            }
        }
    }


    fun playTrack(track: Track) {
        playbackManager.playTracks(listOf(track))
    }

    fun playNext(track: Track) {
        playbackManager.playNext(track)
    }

    fun addToQueue(track: Track) {
        playbackManager.addToQueue(track)
    }

    fun toggleMarkForDeletion(track: Track) {
        viewModelScope.launch {
            if (track.markedForDeletion) {
                libraryRepository.unmarkForDeletion(track.id)
            } else {
                libraryRepository.markForDeletion(track.id)
            }
        }
    }

    fun startRadio(track: Track) {
        viewModelScope.launch {
            try {
                val radioTracks = libraryRepository.startRadio(track.id, track.genre, track.artistId)
                if (radioTracks.isNotEmpty()) {
                    playbackManager.playTracks(radioTracks)
                }
            } catch (e: Exception) {
                DebugLog.e("HomeViewModel", "Start radio failed", e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLibraryTracks: (() -> Unit)? = null,
    onNavigateToLibraryFavorites: (() -> Unit)? = null,
    onNavigateToAlbum: ((String) -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recentTracks by viewModel.recentTracks.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    val featuredCoverArt = recentlyPlayed.firstOrNull()?.coverArt
        ?: recentTracks.firstOrNull()?.coverArt

    com.zonik.app.ui.theme.WithAlbumScheme(coverArtId = featuredCoverArt) {
        PullToRefreshBox(
            isRefreshing = syncState.isSyncing,
            onRefresh = viewModel::syncNow,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HomeContent(
                    syncState = syncState,
                    recentTracks = recentTracks,
                    recentlyPlayed = recentlyPlayed,
                    onShuffleMix = viewModel::shuffleMix,
                    onShuffleRecentlyAdded = viewModel::shuffleRecentlyAdded,
                    onShuffleNewestByYear = viewModel::shuffleNewestByYear,
                    onSyncNow = viewModel::syncNow,
                    onNavigateToLibraryTracks = onNavigateToLibraryTracks,
                    onNavigateToLibraryFavorites = onNavigateToLibraryFavorites,
                    onPlayTrack = viewModel::playTrack,
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                    onToggleMarkForDeletion = viewModel::toggleMarkForDeletion,
                    onStartRadio = viewModel::startRadio
                )

                ExtendedFloatingActionButton(
                    onClick = viewModel::shuffleMix,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text("Play", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 158.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    syncState: SyncState,
    recentTracks: List<Track>,
    recentlyPlayed: List<Track>,
    onShuffleMix: () -> Unit,
    onShuffleRecentlyAdded: () -> Unit,
    onShuffleNewestByYear: () -> Unit,
    onSyncNow: () -> Unit,
    onNavigateToLibraryTracks: (() -> Unit)?,
    onNavigateToLibraryFavorites: (() -> Unit)?,
    onPlayTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onToggleMarkForDeletion: (Track) -> Unit,
    onStartRadio: (Track) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Top app bar — titleLg + sync action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Zonik",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            if (syncState.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(
                onClick = onSyncNow,
                enabled = !syncState.isSyncing
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync")
            }
        }

        // Greeting
        Text(
            text = "Good evening",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
        )

        // Sync status banner
        SyncBanner(syncState = syncState, onDismiss = {})

        // Shuffle row — 2-up tile grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShuffleTile(
                title = "Shuffle Mix",
                sub = "100 random tracks",
                icon = Icons.Default.Shuffle,
                tonal = false,
                onClick = onShuffleMix,
                modifier = Modifier.weight(1f)
            )
            ShuffleTile(
                title = "Favorites",
                sub = "Starred tracks",
                icon = Icons.Default.Favorite,
                tonal = true,
                onClick = onNavigateToLibraryFavorites ?: onNavigateToLibraryTracks ?: {},
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShuffleTile(
                title = "Recently Added",
                sub = "Shuffle 100 newest",
                icon = Icons.Default.NewReleases,
                tonal = true,
                onClick = onShuffleRecentlyAdded,
                modifier = Modifier.weight(1f)
            )
            ShuffleTile(
                title = "By Release Date",
                sub = "Shuffle 100 newest",
                icon = Icons.Default.CalendarMonth,
                tonal = true,
                onClick = onShuffleNewestByYear,
                modifier = Modifier.weight(1f)
            )
        }

        // Recently played
        if (recentlyPlayed.isNotEmpty()) {
            SectionTitle(text = "Recently played")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recentlyPlayed, key = { it.id }) { track ->
                    RecentlyPlayedCard(
                        track = track,
                        onClick = { onPlayTrack(track) }
                    )
                }
            }
        }

        // All tracks shortcut
        if (onNavigateToLibraryTracks != null) {
            TextButton(
                onClick = onNavigateToLibraryTracks,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("All Tracks")
            }
        }

        // Recent Tracks (full list — preserves prior behavior)
        if (recentTracks.isNotEmpty()) {
            SectionTitle(text = "Recent tracks")
            recentTracks.forEachIndexed { index, track ->
                val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent
                TrackListItemWithMenu(
                    track = track,
                    onPlay = { onPlayTrack(track) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onToggleMarkForDeletion = { onToggleMarkForDeletion(track) },
                    onStartRadio = { onStartRadio(track) },
                    backgroundColor = rowBg
                )
            }
        } else if (!syncState.isSyncing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No tracks yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onSyncNow) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Library")
                    }
                }
            }
        }

        // Bottom padding to clear the floating mini-player + nav
        Spacer(modifier = Modifier.height(220.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)
    )
}

@Composable
private fun ShuffleTile(
    title: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tonal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (tonal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
        contentColor = if (tonal) Color.White else MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (tonal) Color.White.copy(alpha = 0.12f)
                        else Color(0xFF0D0A18).copy(alpha = 0.18f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SyncBanner(syncState: SyncState, onDismiss: () -> Unit) {
    if (syncState.isSyncing) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = syncState.phase,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (syncState.detail.isNotEmpty()) {
                        Text(
                            text = syncState.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    if (syncState.error != null) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = syncState.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }

    if (!syncState.isSyncing && syncState.error == null && syncState.lastSyncResult != null) {
        var dismissed by remember(syncState.lastSyncResult) { mutableStateOf(false) }

        LaunchedEffect(syncState.lastSyncResult) {
            delay(8000)
            dismissed = true
        }

        if (!dismissed) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = syncState.lastSyncResult,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    IconButton(onClick = { dismissed = true }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyPlayedCard(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(144.dp)
            .tvFocusHighlight(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box {
            CoverArt(
                coverArtId = track.coverArt,
                contentDescription = track.title,
                modifier = Modifier
                    .size(144.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            track.suffix?.let { suffix ->
                if (suffix.lowercase() in setOf("flac", "alac", "wav", "aiff")) {
                    Box(modifier = Modifier.padding(6.dp)) {
                        com.zonik.app.ui.components.FormatBadge(suffix)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackListItemWithMenu(
    track: Track,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleMarkForDeletion: () -> Unit,
    onStartRadio: () -> Unit,
    backgroundColor: Color = Color.Transparent
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = backgroundColor),
            headlineContent = {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (track.markedForDeletion) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                )
            },
            supportingContent = {
                Text(
                    text = "${track.artist} \u00b7 ${track.album}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                CoverArt(
                    coverArtId = track.coverArt,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            },
            trailingContent = {
                val min = track.duration / 60
                val sec = track.duration % 60
                Text(
                    text = "%d:%02d".format(min, sec),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .tvFocusHighlight()
                .combinedClickable(
                    onClick = onPlay,
                    onLongClick = { showMenu = true }
                )
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Play") },
                onClick = { showMenu = false; onPlay() },
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Play Next") },
                onClick = { showMenu = false; onPlayNext() },
                leadingIcon = { Icon(Icons.Default.QueuePlayNext, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Add to Queue") },
                onClick = { showMenu = false; onAddToQueue() },
                leadingIcon = { Icon(Icons.Default.AddToQueue, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Start Radio") },
                onClick = { showMenu = false; onStartRadio() },
                leadingIcon = { Icon(Icons.Default.Sensors, contentDescription = null) }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (track.markedForDeletion) "Unmark for Deletion" else "Mark for Deletion",
                        color = if (!track.markedForDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = { showMenu = false; onToggleMarkForDeletion() },
                leadingIcon = {
                    Icon(
                        if (track.markedForDeletion) Icons.Default.RestoreFromTrash else Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = if (!track.markedForDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            )
        }
    }
}
