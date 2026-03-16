package com.zonik.app.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.zonik.app.model.Album
import com.zonik.app.model.Artist
import com.zonik.app.model.Genre
import com.zonik.app.model.Playlist
import com.zonik.app.model.Track
import com.zonik.app.ui.components.CoverArt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    val artists = libraryRepository.getArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums = libraryRepository.getAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tracks = libraryRepository.getAllTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres.asStateFlow()

    private val _isLoadingGenres = MutableStateFlow(false)
    val isLoadingGenres: StateFlow<Boolean> = _isLoadingGenres.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists: StateFlow<Boolean> = _isLoadingPlaylists.asStateFlow()

    init {
        loadGenres()
        loadPlaylists()
    }

    fun playTrack(track: Track) {
        playbackManager.playTracks(listOf(track), 0)
    }

    fun playAllTracks() {
        val allTracks = tracks.value
        if (allTracks.isNotEmpty()) {
            playbackManager.playTracks(allTracks, startIndex = 0)
        }
    }

    fun shuffleAllTracks() {
        val allTracks = tracks.value
        if (allTracks.isNotEmpty()) {
            playbackManager.playTracks(allTracks.shuffled(), startIndex = 0)
        }
    }

    fun playNext(track: Track) {
        playbackManager.playNext(track)
    }

    fun addToQueue(track: Track) {
        playbackManager.addToQueue(track)
    }

    private fun loadGenres() {
        viewModelScope.launch {
            _isLoadingGenres.value = true
            try {
                _genres.value = libraryRepository.getGenres()
            } catch (_: Exception) {
                _genres.value = emptyList()
            } finally {
                _isLoadingGenres.value = false
            }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            _isLoadingPlaylists.value = true
            try {
                _playlists.value = libraryRepository.getPlaylists()
            } catch (_: Exception) {
                _playlists.value = emptyList()
            } finally {
                _isLoadingPlaylists.value = false
            }
        }
    }
}

private enum class LibraryTab(val label: String) {
    TRACKS("Tracks"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres"),
    PLAYLISTS("Playlists")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val artists by viewModel.artists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val isLoadingGenres by viewModel.isLoadingGenres.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isLoadingPlaylists by viewModel.isLoadingPlaylists.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = LibraryTab.entries

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Library") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) }
                    )
                }
            }

            when (tabs[selectedTab]) {
                LibraryTab.ARTISTS -> ArtistsTab(
                    artists = artists,
                    onArtistClick = onNavigateToArtist
                )
                LibraryTab.ALBUMS -> AlbumsTab(
                    albums = albums,
                    onAlbumClick = onNavigateToAlbum
                )
                LibraryTab.TRACKS -> TracksTab(
                    tracks = tracks,
                    onPlayTrack = viewModel::playTrack,
                    onPlayAll = viewModel::playAllTracks,
                    onShuffleAll = viewModel::shuffleAllTracks,
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue
                )
                LibraryTab.GENRES -> GenresTab(
                    genres = genres,
                    isLoading = isLoadingGenres
                )
                LibraryTab.PLAYLISTS -> PlaylistsTab(
                    playlists = playlists,
                    isLoading = isLoadingPlaylists
                )
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: List<Artist>,
    onArtistClick: (String) -> Unit
) {
    if (artists.isEmpty()) {
        EmptyState(message = "No artists found")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(artists, key = { it.id }) { artist ->
            ListItem(
                headlineContent = {
                    Text(
                        text = artist.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        text = "${artist.albumCount} album${if (artist.albumCount != 1) "s" else ""}"
                    )
                },
                leadingContent = {
                    CoverArt(
                        coverArtId = artist.coverArt,
                        contentDescription = artist.name,
                        modifier = Modifier.size(48.dp)
                    )
                },
                modifier = Modifier.clickable { onArtistClick(artist.id) }
            )
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: List<Album>,
    onAlbumClick: (String) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyState(message = "No albums found")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumGridCard(
                album = album,
                onClick = { onAlbumClick(album.id) }
            )
        }
    }
}

@Composable
private fun AlbumGridCard(
    album: Album,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            album.year?.let { year ->
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GenresTab(
    genres: List<Genre>,
    isLoading: Boolean
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (genres.isEmpty()) {
        EmptyState(message = "No genres found")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(genres, key = { it.name }) { genre ->
            ListItem(
                headlineContent = {
                    Text(
                        text = genre.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        text = "${genre.songCount} song${if (genre.songCount != 1) "s" else ""} \u00b7 " +
                            "${genre.albumCount} album${if (genre.albumCount != 1) "s" else ""}"
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TracksTab(
    tracks: List<Track>,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyState(message = "No tracks found")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Play All / Shuffle button row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onPlayAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All")
                }
                FilledTonalButton(
                    onClick = onShuffleAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle")
                }
            }
        }

        items(tracks, key = { it.id }) { track ->
            var showMenu by remember { mutableStateOf(false) }

            Box {
                ListItem(
                    headlineContent = {
                        Text(
                            text = track.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "${track.artist} · ${track.album}",
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
                        Column(horizontalAlignment = Alignment.End) {
                            val min = track.duration / 60
                            val sec = track.duration % 60
                            Text(
                                text = "%d:%02d".format(min, sec),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            track.suffix?.uppercase()?.let { fmt ->
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = fmt,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = { onPlayTrack(track) },
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
                            onPlayTrack(track)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Play Next") },
                        onClick = {
                            showMenu = false
                            onPlayNext(track)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.QueuePlayNext, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Queue") },
                        onClick = {
                            showMenu = false
                            onAddToQueue(track)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.AddToQueue, contentDescription = null)
                        }
                    )
                }
            }
        }

        // Bottom spacing for mini player
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    isLoading: Boolean
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (playlists.isEmpty()) {
        EmptyState(message = "No playlists found")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(playlists, key = { it.id }) { playlist ->
            ListItem(
                headlineContent = {
                    Text(
                        text = playlist.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        text = "${playlist.songCount} track${if (playlist.songCount != 1) "s" else ""}"
                    )
                }
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
