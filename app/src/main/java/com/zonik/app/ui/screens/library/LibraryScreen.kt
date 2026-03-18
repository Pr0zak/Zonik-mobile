package com.zonik.app.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
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
import com.zonik.app.ui.components.TrackDetailsSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TrackSort(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
    RECENT("Recently Added")
}

enum class AlbumSort(val label: String) {
    NAME("Name"),
    ARTIST("Artist"),
    YEAR("Year"),
    RECENT("Recently Added")
}

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

    private val _trackSort = MutableStateFlow(TrackSort.RECENT)
    val trackSort: StateFlow<TrackSort> = _trackSort.asStateFlow()

    private val _trackSortAsc = MutableStateFlow(false)
    val trackSortAsc: StateFlow<Boolean> = _trackSortAsc.asStateFlow()

    private val _trackFormatFilter = MutableStateFlow<String?>(null)
    val trackFormatFilter: StateFlow<String?> = _trackFormatFilter.asStateFlow()

    private val _albumSort = MutableStateFlow(AlbumSort.RECENT)
    val albumSort: StateFlow<AlbumSort> = _albumSort.asStateFlow()

    private val _albumSortAsc = MutableStateFlow(false)
    val albumSortAsc: StateFlow<Boolean> = _albumSortAsc.asStateFlow()

    fun setTrackSort(sort: TrackSort) {
        if (_trackSort.value == sort) {
            _trackSortAsc.value = !_trackSortAsc.value
        } else {
            _trackSort.value = sort
            _trackSortAsc.value = sort != TrackSort.RECENT
        }
    }

    fun setTrackFormatFilter(format: String?) {
        _trackFormatFilter.value = format
    }

    fun setAlbumSort(sort: AlbumSort) {
        if (_albumSort.value == sort) {
            _albumSortAsc.value = !_albumSortAsc.value
        } else {
            _albumSort.value = sort
            _albumSortAsc.value = sort != AlbumSort.RECENT
        }
    }

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

    fun toggleMarkForDeletion(track: Track) {
        viewModelScope.launch {
            if (track.markedForDeletion) {
                libraryRepository.unmarkForDeletion(track.id)
            } else {
                libraryRepository.markForDeletion(track.id)
            }
        }
    }

    fun playGenre(genre: String) {
        viewModelScope.launch {
            try {
                val tracks = libraryRepository.getRandomSongs(count = 100, genre = genre)
                if (tracks.isNotEmpty()) {
                    playbackManager.setShuffleEnabled(true)
                    playbackManager.playTracks(tracks)
                }
            } catch (_: Exception) {}
        }
    }

    fun playPlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                val tracks = libraryRepository.getPlaylistTracks(playlistId)
                if (tracks.isNotEmpty()) {
                    playbackManager.playTracks(tracks)
                }
            } catch (_: Exception) {}
        }
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
            TopAppBar(
                title = { Text("Library") },
                windowInsets = WindowInsets(0)
            )
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
                    onAlbumClick = onNavigateToAlbum,
                    viewModel = viewModel
                )
                LibraryTab.TRACKS -> TracksTab(
                    tracks = tracks,
                    viewModel = viewModel
                )
                LibraryTab.GENRES -> GenresTab(
                    genres = genres,
                    isLoading = isLoadingGenres,
                    onPlayGenre = viewModel::playGenre
                )
                LibraryTab.PLAYLISTS -> PlaylistsTab(
                    playlists = playlists,
                    isLoading = isLoadingPlaylists,
                    onPlayPlaylist = viewModel::playPlaylist
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumsTab(
    albums: List<Album>,
    onAlbumClick: (String) -> Unit,
    viewModel: LibraryViewModel
) {
    val albumSort by viewModel.albumSort.collectAsState()
    val albumSortAsc by viewModel.albumSortAsc.collectAsState()

    if (albums.isEmpty()) {
        EmptyState(message = "No albums found")
        return
    }

    val sortedAlbums = remember(albums, albumSort, albumSortAsc) {
        val sorted = when (albumSort) {
            AlbumSort.NAME -> albums.sortedBy { it.name.lowercase() }
            AlbumSort.ARTIST -> albums.sortedBy { it.artist.lowercase() }
            AlbumSort.YEAR -> albums.sortedBy { it.year ?: 0 }
            AlbumSort.RECENT -> albums
        }
        if (albumSortAsc || albumSort == AlbumSort.RECENT && albumSortAsc) sorted else sorted.reversed()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AlbumSort.entries.forEach { sort ->
                val selected = albumSort == sort
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setAlbumSort(sort) },
                    label = { Text(sort.label, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = if (selected) {
                        {
                            Icon(
                                if (albumSortAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sortedAlbums, key = { it.id }) { album ->
                AlbumGridCard(
                    album = album,
                    onClick = { onAlbumClick(album.id) }
                )
            }
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
    isLoading: Boolean,
    onPlayGenre: (String) -> Unit
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
                        text = "${genre.songCount} song${if (genre.songCount != 1) "s" else ""} · " +
                            "${genre.albumCount} album${if (genre.albumCount != 1) "s" else ""}"
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable { onPlayGenre(genre.name) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TracksTab(
    tracks: List<Track>,
    viewModel: LibraryViewModel
) {
    val trackSort by viewModel.trackSort.collectAsState()
    val trackSortAsc by viewModel.trackSortAsc.collectAsState()
    if (tracks.isEmpty()) {
        EmptyState(message = "No tracks found")
        return
    }

    // Apply sort
    val sortedTracks = remember(tracks, trackSort, trackSortAsc) {
        val sorted = when (trackSort) {
            TrackSort.TITLE -> tracks.sortedBy { it.title.lowercase() }
            TrackSort.ARTIST -> tracks.sortedBy { it.artist.lowercase() }
            TrackSort.ALBUM -> tracks.sortedWith(compareBy({ it.album.lowercase() }, { it.track ?: 0 }))
            TrackSort.DURATION -> tracks.sortedBy { it.duration }
            TrackSort.RECENT -> tracks // Already in recent order from DB
        }
        if (trackSortAsc || trackSort == TrackSort.RECENT && trackSortAsc) sorted else sorted.reversed()
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Build letter index for alpha scroll (only when sorted by title or artist)
    val letterIndex = remember(sortedTracks, trackSort) {
        if (trackSort != TrackSort.RECENT && trackSort != TrackSort.DURATION) {
            val map = mutableMapOf<Char, Int>()
            val headerOffset = 3 // Play All row + sort chips + track count
            sortedTracks.forEachIndexed { index, track ->
                val letter = when (trackSort) {
                    TrackSort.TITLE -> track.title.firstOrNull()?.uppercaseChar() ?: '#'
                    TrackSort.ARTIST -> track.artist.firstOrNull()?.uppercaseChar() ?: '#'
                    TrackSort.ALBUM -> track.album.firstOrNull()?.uppercaseChar() ?: '#'
                    else -> '#'
                }
                val ch = if (letter.isLetter()) letter else '#'
                if (ch !in map) map[ch] = index + headerOffset
            }
            map
        } else emptyMap()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        // Play All / Shuffle row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { viewModel.playAllTracks() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All")
                }
                FilledTonalButton(
                    onClick = { viewModel.shuffleAllTracks() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle")
                }
            }
        }

        // Sort chips
        item {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TrackSort.entries.forEach { sort ->
                    val selected = trackSort == sort
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setTrackSort(sort) },
                        label = { Text(sort.label, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = if (selected) {
                            {
                                Icon(
                                    if (trackSortAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        }

        // Track count
        item {
            Text(
                text = "${sortedTracks.size} tracks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        items(sortedTracks, key = { it.id }) { track ->
            var showMenu by remember { mutableStateOf(false) }
            var showDetails by remember { mutableStateOf(false) }

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
                        onClick = { viewModel.playTrack(track) },
                        onLongClick = { showMenu = true }
                    )
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        onClick = { showMenu = false; viewModel.playTrack(track) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Play Next") },
                        onClick = { showMenu = false; viewModel.playNext(track) },
                        leadingIcon = { Icon(Icons.Default.QueuePlayNext, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Queue") },
                        onClick = { showMenu = false; viewModel.addToQueue(track) },
                        leadingIcon = { Icon(Icons.Default.AddToQueue, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Track Details") },
                        onClick = { showMenu = false; showDetails = true },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (track.markedForDeletion) "Unmark for Deletion" else "Mark for Deletion",
                                color = if (!track.markedForDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = { showMenu = false; viewModel.toggleMarkForDeletion(track) },
                        leadingIcon = {
                            Icon(
                                if (track.markedForDeletion) Icons.Default.RestoreFromTrash else Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = if (!track.markedForDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }

                if (showDetails) {
                    TrackDetailsSheet(track = track, onDismiss = { showDetails = false })
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Alpha scroll sidebar — draggable
    if (letterIndex.isNotEmpty()) {
        val letters = letterIndex.keys.sorted()
        var isDragging by remember { mutableStateOf(false) }
        var activeLetter by remember { mutableStateOf<Char?>(null) }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val letterHeight = size.height.toFloat() / letters.size
                            val index = (offset.y / letterHeight).toInt().coerceIn(0, letters.lastIndex)
                            val letter = letters[index]
                            activeLetter = letter
                            letterIndex[letter]?.let { idx ->
                                coroutineScope.launch { listState.scrollToItem(idx) }
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            activeLetter = null
                        },
                        onDragCancel = {
                            isDragging = false
                            activeLetter = null
                        },
                        onVerticalDrag = { _, _ ->
                            // handled via pointer position in onDragStart-like logic below
                        }
                    )
                }
                .pointerInput(letters) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (isDragging && event.changes.isNotEmpty()) {
                                val y = event.changes.first().position.y
                                val letterHeight = size.height.toFloat() / letters.size
                                val index = (y / letterHeight).toInt().coerceIn(0, letters.lastIndex)
                                val letter = letters[index]
                                if (letter != activeLetter) {
                                    activeLetter = letter
                                    letterIndex[letter]?.let { idx ->
                                        coroutineScope.launch { listState.scrollToItem(idx) }
                                    }
                                }
                            }
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (letter == activeLetter) androidx.compose.ui.text.font.FontWeight.Bold else null,
                    color = if (letter == activeLetter) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable {
                            letterIndex[letter]?.let { index ->
                                coroutineScope.launch { listState.scrollToItem(index) }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }

        // Show active letter indicator while dragging
        if (isDragging && activeLetter != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeLetter.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
    } // close Box
}

@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    isLoading: Boolean,
    onPlayPlaylist: (String) -> Unit
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
                },
                leadingContent = {
                    CoverArt(
                        coverArtId = playlist.coverArt,
                        contentDescription = playlist.name,
                        modifier = Modifier.size(48.dp)
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable { onPlayPlaylist(playlist.id) }
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
