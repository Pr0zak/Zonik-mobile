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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.zonik.app.R
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
import com.zonik.app.model.Album
import com.zonik.app.model.Track
import com.zonik.app.ui.components.CoverArt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackManager: PlaybackManager,
    private val syncManager: SyncManager
) : ViewModel() {

    val recentAlbums = libraryRepository.getRecentAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                    playbackManager.setShuffleEnabled(true)
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

    fun trueRandom() {
        viewModelScope.launch {
            try {
                DebugLog.d("HomeViewModel", "Starting true random")
                val tracks = libraryRepository.getRandomSongs(count = 50)
                if (tracks.isNotEmpty()) {
                    playbackManager.playTracks(tracks)
                    DebugLog.d("HomeViewModel", "True random started with ${tracks.size} tracks")
                } else {
                    DebugLog.w("HomeViewModel", "True random: no tracks returned")
                }
            } catch (e: Exception) {
                DebugLog.e("HomeViewModel", "True random failed", e)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLibraryTracks: (() -> Unit)? = null,
    onNavigateToAlbum: ((String) -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recentAlbums by viewModel.recentAlbums.collectAsState()
    val recentTracks by viewModel.recentTracks.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1A1A2E))
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Zonik")
                    }
                },
                actions = {
                    if (syncState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(
                        onClick = viewModel::syncNow,
                        enabled = !syncState.isSyncing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Sync status banner
            SyncBanner(
                syncState = syncState,
                onDismiss = {}
            )

            // Quick actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = viewModel::shuffleMix,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Mix")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shuffle Mix")
                }
                FilledTonalButton(
                    onClick = viewModel::trueRandom,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Casino, contentDescription = "True Random")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("True Random")
                }
            }

            // All Tracks button
            if (onNavigateToLibraryTracks != null) {
                OutlinedButton(
                    onClick = onNavigateToLibraryTracks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All Tracks")
                }
            }

            // Recently Added
            Text(
                text = "Recently Added",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (recentAlbums.isEmpty() && !syncState.isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No albums yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = viewModel::syncNow) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Library")
                        }
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentAlbums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onNavigateToAlbum?.invoke(album.id) }
                        )
                    }
                }
            }

            // Recent Tracks
            if (recentTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Recent Tracks",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                recentTracks.forEach { track ->
                    TrackListItemWithMenu(
                        track = track,
                        onPlay = { viewModel.playTrack(track) },
                        onPlayNext = { viewModel.playNext(track) },
                        onAddToQueue = { viewModel.addToQueue(track) },
                        onToggleMarkForDeletion = { viewModel.toggleMarkForDeletion(track) }
                    )
                }
            }

            // Recently Played
            if (recentlyPlayed.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Recently Played",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyPlayed, key = { it.id }) { track ->
                        RecentlyPlayedCard(
                            track = track,
                            onClick = { viewModel.playTrack(track) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
    Card(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            CoverArt(
                coverArtId = track.coverArt,
                contentDescription = track.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackListItemWithMenu(
    track: Track,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleMarkForDeletion: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        ListItem(
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
                    modifier = Modifier.size(48.dp)
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
            modifier = Modifier.combinedClickable(
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

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.width(150.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            CoverArt(
                coverArtId = album.coverArt,
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
