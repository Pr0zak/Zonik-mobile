package com.zonik.app.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zonik.app.ui.theme.WithNeutralScheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.media.PlaybackManager
import com.zonik.app.model.Album
import com.zonik.app.model.Artist
import com.zonik.app.model.Track
import com.zonik.app.ui.components.CoverArt
import com.zonik.app.ui.util.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _query = MutableStateFlow("")

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        _query
            .debounce(300)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isBlank()) {
                    _uiState.update {
                        it.copy(
                            artists = emptyList(),
                            albums = emptyList(),
                            tracks = emptyList(),
                            isLoading = false,
                            hasSearched = false,
                            error = null
                        )
                    }
                } else {
                    search(query)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        _query.value = newQuery
    }

    private fun search(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val (artists, albums, tracks) = libraryRepository.search(query)
                _uiState.update {
                    it.copy(
                        artists = artists,
                        albums = albums,
                        tracks = tracks,
                        isLoading = false,
                        hasSearched = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasSearched = true,
                        error = e.message ?: "Search failed"
                    )
                }
            }
        }
    }

    fun playTrack(track: Track) {
        playbackManager.playTracks(listOf(track), 0)
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
            // Update UI state to reflect the change
            _uiState.update { state ->
                state.copy(
                    tracks = state.tracks.map {
                        if (it.id == track.id) it.copy(markedForDeletion = !track.markedForDeletion) else it
                    }
                )
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
}

private enum class SearchFilter { ALL, ALBUMS, ARTISTS, TRACKS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf(SearchFilter.ALL) }

    WithNeutralScheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                M3SearchBar(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChanged
                )
                Spacer(modifier = Modifier.height(4.dp))
                FilterChipRow(
                    selected = filter,
                    onSelect = { filter = it }
                )
                Spacer(modifier = Modifier.height(8.dp))

                when {
                    uiState.isLoading -> CenterContent {
                        CircularProgressIndicator()
                    }

                    uiState.error != null -> CenterContent {
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    !uiState.hasSearched -> CenterContent {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Search your music library",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    uiState.artists.isEmpty() && uiState.albums.isEmpty() && uiState.tracks.isEmpty() -> CenterContent {
                        Text(
                            text = "No results found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        SearchResults(
                            query = uiState.query,
                            filter = filter,
                            artists = uiState.artists,
                            albums = uiState.albums,
                            tracks = uiState.tracks,
                            onArtistClick = { onNavigateToArtist(it.id) },
                            onAlbumClick = { onNavigateToAlbum(it.id) },
                            onTrackClick = { viewModel.playTrack(it) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onToggleMarkForDeletion = { viewModel.toggleMarkForDeletion(it) },
                            onStartRadio = { viewModel.startRadio(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun M3SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 8.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search your library",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    selected: SearchFilter,
    onSelect: (SearchFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchFilter.entries.forEach { f ->
            val isSelected = f == selected
            val label = when (f) {
                SearchFilter.ALL -> "All"
                SearchFilter.ALBUMS -> "Albums"
                SearchFilter.ARTISTS -> "Artists"
                SearchFilter.TRACKS -> "Tracks"
            }
            Surface(
                onClick = { onSelect(f) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun highlight(text: String, query: String, color: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val idx = text.indexOf(query, ignoreCase = true)
    if (idx < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, idx))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
            append(text.substring(idx, idx + query.length))
        }
        append(text.substring(idx + query.length))
    }
}

@Composable
private fun SearchResults(
    query: String,
    filter: SearchFilter,
    artists: List<Artist>,
    albums: List<Album>,
    tracks: List<Track>,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onToggleMarkForDeletion: (Track) -> Unit,
    onStartRadio: (Track) -> Unit
) {
    val showArtists = filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS
    val showAlbums = filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS
    val showTracks = filter == SearchFilter.ALL || filter == SearchFilter.TRACKS

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 200.dp)
    ) {
        // Top result hero — first matching album, falls back to first artist or track
        if (filter == SearchFilter.ALL) {
            val topAlbum = albums.firstOrNull()
            if (topAlbum != null) {
                item("top") {
                    TopResultHero(
                        query = query,
                        album = topAlbum,
                        onClick = { onAlbumClick(topAlbum) }
                    )
                }
            }
        }

        if (showArtists && artists.isNotEmpty()) {
            item("artists-h") { SectionHeader(title = "Artists") }
            items(artists, key = { "artist-${it.id}" }) { artist ->
                ResultRow(
                    kind = "Artist",
                    headline = artist.name,
                    sub = "${artist.albumCount} album${if (artist.albumCount != 1) "s" else ""}",
                    coverArtId = artist.coverArt,
                    query = query,
                    onClick = { onArtistClick(artist) }
                )
            }
        }

        if (showAlbums && albums.isNotEmpty()) {
            item("albums-h") { SectionHeader(title = "Albums") }
            items(albums, key = { "album-${it.id}" }) { album ->
                ResultRow(
                    kind = "Album",
                    headline = album.name,
                    sub = album.artist,
                    coverArtId = album.coverArt,
                    query = query,
                    onClick = { onAlbumClick(album) }
                )
            }
        }

        if (showTracks && tracks.isNotEmpty()) {
            item("tracks-h") { SectionHeader(title = "Tracks") }
            itemsIndexed(tracks, key = { _, track -> "track-${track.id}" }) { _, track ->
                TrackRow(
                    track = track,
                    query = query,
                    onClick = { onTrackClick(track) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onToggleMarkForDeletion = { onToggleMarkForDeletion(track) },
                    onStartRadio = { onStartRadio(track) }
                )
            }
        }
    }
}

@Composable
private fun TopResultHero(
    query: String,
    album: Album,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            CoverArt(
                coverArtId = album.coverArt,
                contentDescription = album.name,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOP RESULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = highlight(album.name, query, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Album · ${album.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    kind: String,
    headline: String,
    sub: String,
    coverArtId: String?,
    query: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        CoverArt(
            coverArtId = coverArtId,
            contentDescription = headline,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kind.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = highlight(headline, query, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    query: String,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleMarkForDeletion: () -> Unit,
    onStartRadio: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            CoverArt(
                coverArtId = track.coverArt,
                contentDescription = track.title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TRACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = highlight(track.title, query, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (track.markedForDeletion) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} · ${formatDuration(track.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Play") },
                onClick = { showMenu = false; onClick() },
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
