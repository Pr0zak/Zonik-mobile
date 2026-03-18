package com.zonik.app.ui.screens.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.media.PlaybackManager
import com.zonik.app.model.Playlist
import com.zonik.app.model.Track
import com.zonik.app.ui.util.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedPlaylist: Playlist? = null,
    val playlistTracks: List<Track> = emptyList(),
    val isLoadingTracks: Boolean = false
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val playlists = libraryRepository.getPlaylists()
                _uiState.update { it.copy(playlists = playlists, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load playlists")
                }
            }
        }
    }

    fun selectPlaylist(playlist: Playlist) {
        _uiState.update { it.copy(selectedPlaylist = playlist, isLoadingTracks = true) }
        viewModelScope.launch {
            try {
                val tracks = libraryRepository.getPlaylistTracks(playlist.id)
                _uiState.update { it.copy(playlistTracks = tracks, isLoadingTracks = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingTracks = false,
                        error = e.message ?: "Failed to load playlist tracks"
                    )
                }
            }
        }
    }

    fun clearSelectedPlaylist() {
        _uiState.update { it.copy(selectedPlaylist = null, playlistTracks = emptyList()) }
    }

    fun playAll() {
        val tracks = _uiState.value.playlistTracks
        if (tracks.isNotEmpty()) {
            playbackManager.playTracks(tracks, 0)
        }
    }

    fun shufflePlay() {
        val tracks = _uiState.value.playlistTracks
        if (tracks.isNotEmpty()) {
            playbackManager.playTracks(tracks.shuffled(), 0)
        }
    }

    fun playTrack(index: Int) {
        val tracks = _uiState.value.playlistTracks
        if (tracks.isNotEmpty()) {
            playbackManager.playTracks(tracks, index)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.selectedPlaylist != null) {
        PlaylistDetailScreen(
            playlist = uiState.selectedPlaylist!!,
            tracks = uiState.playlistTracks,
            isLoading = uiState.isLoadingTracks,
            onBack = viewModel::clearSelectedPlaylist,
            onPlayAll = viewModel::playAll,
            onShuffle = viewModel::shufflePlay,
            onTrackClick = { index -> viewModel.playTrack(index) }
        )
    } else {
        PlaylistListScreen(
            playlists = uiState.playlists,
            isLoading = uiState.isLoading,
            error = uiState.error,
            onPlaylistClick = viewModel::selectPlaylist
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistListScreen(
    playlists: List<Playlist>,
    isLoading: Boolean,
    error: String?,
    onPlaylistClick: (Playlist) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playlists") },
                windowInsets = WindowInsets(0)
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            playlists.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No playlists found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(text = playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val trackLabel = "${playlist.songCount} track${if (playlist.songCount != 1) "s" else ""}"
            val durationLabel = formatDuration(playlist.duration)
            Text(text = "$trackLabel - $durationLabel")
        },
        leadingContent = {
            Icon(
                Icons.Default.QueueMusic,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailScreen(
    playlist: Playlist,
    tracks: List<Track>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                windowInsets = WindowInsets(0)
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Play all / Shuffle buttons
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onPlayAll,
                                modifier = Modifier.weight(1f),
                                enabled = tracks.isNotEmpty()
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play All")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play All")
                            }
                            FilledTonalButton(
                                onClick = onShuffle,
                                modifier = Modifier.weight(1f),
                                enabled = tracks.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Shuffle")
                            }
                        }
                    }

                    // Track count summary
                    item {
                        val trackLabel =
                            "${playlist.songCount} track${if (playlist.songCount != 1) "s" else ""}"
                        val durationLabel = formatDuration(playlist.duration)
                        Text(
                            text = "$trackLabel - $durationLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // Track list
                    itemsIndexed(tracks, key = { index, track -> "track-$index-${track.id}" }) { index, track ->
                        PlaylistTrackRow(
                            track = track,
                            trackNumber = index + 1,
                            onClick = { onTrackClick(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTrackRow(track: Track, trackNumber: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(text = track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(text = track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Text(
                text = formatDuration(track.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
