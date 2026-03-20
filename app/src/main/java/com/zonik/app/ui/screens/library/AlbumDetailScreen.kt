package com.zonik.app.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.media.PlaybackManager
import com.zonik.app.model.Album
import com.zonik.app.model.Track
import com.zonik.app.ui.components.CoverArt
import com.zonik.app.ui.util.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val albumId: String = checkNotNull(savedStateHandle["albumId"])

    private val _album = MutableStateFlow<Album?>(null)
    val album: StateFlow<Album?> = _album.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadAlbumDetail()
    }

    private fun loadAlbumDetail() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val (album, tracks) = libraryRepository.getAlbumDetail(albumId)
                _album.value = album
                _tracks.value = tracks
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load album"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playAll() {
        val tracks = _tracks.value
        if (tracks.isNotEmpty()) {
            playbackManager.playTracks(tracks, startIndex = 0)
        }
    }

    fun shuffle() {
        val tracks = _tracks.value
        if (tracks.isNotEmpty()) {
            playbackManager.playTracks(tracks.shuffled(), startIndex = 0)
        }
    }

    fun playFromTrack(index: Int) {
        val tracks = _tracks.value
        if (index in tracks.indices) {
            playbackManager.playTracks(tracks, startIndex = index)
        }
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
            // Refresh tracks to reflect the change
            _tracks.value = _tracks.value.map {
                if (it.id == track.id) it.copy(markedForDeletion = !track.markedForDeletion) else it
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
            } catch (_: Exception) {}
        }
    }

    fun star() {
        viewModelScope.launch {
            val currentAlbum = _album.value ?: return@launch
            try {
                if (currentAlbum.starred) {
                    libraryRepository.unstar(currentAlbum.id)
                } else {
                    libraryRepository.star(currentAlbum.id)
                }
                _album.value = currentAlbum.copy(starred = !currentAlbum.starred)
            } catch (_: Exception) {
                // Silently fail
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val album by viewModel.album.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                AlbumDetailContent(
                    album = album,
                    tracks = tracks,
                    onPlayAll = viewModel::playAll,
                    onShuffle = viewModel::shuffle,
                    onStar = viewModel::star,
                    onTrackClick = viewModel::playFromTrack,
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                    onGoToArtist = onNavigateToArtist,
                    onToggleMarkForDeletion = viewModel::toggleMarkForDeletion,
                    onStartRadio = viewModel::startRadio,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumDetailContent(
    album: Album?,
    tracks: List<Track>,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onStar: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onGoToArtist: (String) -> Unit,
    onToggleMarkForDeletion: (Track) -> Unit,
    onStartRadio: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        // Album header
        item {
            AlbumHeader(album = album, onStar = onStar)
        }

        // Action buttons
        item {
            ActionButtons(
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
                onStar = onStar,
                isStarred = album?.starred == true
            )
        }

        // Track list
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackItem(
                track = track,
                onClick = { onTrackClick(index) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onGoToArtist = {
                    track.artistId?.let { artistId -> onGoToArtist(artistId) }
                },
                onToggleMarkForDeletion = { onToggleMarkForDeletion(track) },
                onStartRadio = { onStartRadio(track) }
            )
        }

        // Bottom spacing for mini player
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun AlbumHeader(album: Album?, onStar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Album art
        CoverArt(
            coverArtId = album?.coverArt,
            contentDescription = album?.name,
            modifier = Modifier.size(240.dp),
            size = 600
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = album?.name ?: "",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = album?.artist ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val details = buildList {
            album?.year?.let { add(it.toString()) }
            album?.genre?.let { add(it) }
            album?.let {
                add("${it.songCount} track${if (it.songCount != 1) "s" else ""}")
                add(formatDuration(it.duration))
            }
        }

        if (details.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = details.joinToString(" \u00b7 "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionButtons(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onStar: () -> Unit,
    isStarred: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        FilledTonalButton(onClick = onPlayAll) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Play All")
        }
        FilledTonalButton(onClick = onShuffle) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Shuffle")
        }
        IconButton(onClick = onStar) {
            Icon(
                imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (isStarred) "Unstar" else "Star",
                tint = if (isStarred) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackItem(
    track: Track,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onGoToArtist: () -> Unit,
    onToggleMarkForDeletion: () -> Unit,
    onStartRadio: () -> Unit
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
                    text = track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Text(
                    text = track.track?.toString() ?: "-",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp)
                )
            },
            trailingContent = {
                Text(
                    text = formatDuration(track.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Play") },
                onClick = {
                    showMenu = false
                    onClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Play Next") },
                onClick = {
                    showMenu = false
                    onPlayNext()
                },
                leadingIcon = {
                    Icon(Icons.Default.QueuePlayNext, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Add to Queue") },
                onClick = {
                    showMenu = false
                    onAddToQueue()
                },
                leadingIcon = {
                    Icon(Icons.Default.AddToQueue, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Go to Artist") },
                onClick = {
                    showMenu = false
                    onGoToArtist()
                },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Start Radio") },
                onClick = {
                    showMenu = false
                    onStartRadio()
                },
                leadingIcon = {
                    Icon(Icons.Default.Sensors, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (track.markedForDeletion) "Unmark for Deletion" else "Mark for Deletion",
                        color = if (!track.markedForDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    showMenu = false
                    onToggleMarkForDeletion()
                },
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
