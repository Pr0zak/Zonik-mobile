package com.zonik.app.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.DebugLog
import com.zonik.app.data.api.*
import com.zonik.app.data.db.ZonikDatabase
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.media.PlaybackManager
import com.zonik.app.model.Album
import com.zonik.app.model.Artist
import com.zonik.app.model.Track
import com.zonik.app.ui.components.CoverArt
import com.zonik.app.ui.theme.WithNeutralScheme
import com.zonik.app.ui.util.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// region State

enum class GetState { Idle, Searching, Downloading, Done, Failed }

data class GetButtonState(
    val state: GetState = GetState.Idle,
    val jobId: String? = null,
    val pct: Float = 0f,
    val received: Long = 0L,
    val total: Long = 0L,
    val error: String? = null
)

data class SearchUiState(
    val query: String = "",
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val isLibrarySearching: Boolean = false,
    val hasSearched: Boolean = false,
    val libraryError: String? = null,
    val downloadResults: List<DownloadResult> = emptyList(),
    val isDownloadSearching: Boolean = false,
    val hasDownloadSearched: Boolean = false,
    val downloadError: String? = null,
    val parsedArtist: String = "",
    val parsedTrack: String = "",
    val activeTransfers: List<TransferInfo> = emptyList(),
    val activeJobs: List<JobInfo> = emptyList(),
    val recentJobs: List<JobInfo> = emptyList(),
    val getStates: Map<String, GetButtonState> = emptyMap(),
    val progress: Map<String, JobProgress> = emptyMap(),
    val toast: String? = null
)

// endregion

// region ViewModel

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackManager: PlaybackManager,
    private val zonikApi: ZonikApi,
    private val database: ZonikDatabase,
    private val progressClient: DownloadProgressClient
) : ViewModel() {

    companion object {
        private const val TAG = "SearchVM"
    }

    private val _query = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var librarySearchJob: Job? = null
    private var downloadSearchJob: Job? = null
    private var statusPollJob: Job? = null

    init {
        // Library search reacts to debounced query changes
        _query
            .debounce(300)
            .distinctUntilChanged()
            .onEach { q ->
                if (q.isBlank()) {
                    _uiState.update {
                        it.copy(
                            artists = emptyList(),
                            albums = emptyList(),
                            tracks = emptyList(),
                            isLibrarySearching = false,
                            hasSearched = false,
                            libraryError = null,
                            downloadResults = emptyList(),
                            isDownloadSearching = false,
                            hasDownloadSearched = false,
                            downloadError = null,
                            parsedArtist = "",
                            parsedTrack = ""
                        )
                    }
                } else {
                    runLibrarySearch(q)
                }
            }
            .launchIn(viewModelScope)

        // Mirror progress map into UI state and resolve get-button states
        progressClient.progress
            .onEach { progressMap ->
                _uiState.update { state ->
                    val updatedGetStates = state.getStates.mapValues { (_, gs) ->
                        val jp = gs.jobId?.let { progressMap[it] } ?: return@mapValues gs
                        val newState = when (jp.status.lowercase()) {
                            "completed", "complete" -> GetState.Done
                            "failed", "error", "cancelled" -> GetState.Failed
                            "running", "in_progress" -> if (jp.total > 0) GetState.Downloading else GetState.Searching
                            else -> if (jp.total > 0) GetState.Downloading else GetState.Searching
                        }
                        gs.copy(
                            state = newState,
                            pct = jp.pct,
                            received = jp.progress,
                            total = jp.total,
                            error = jp.error
                        )
                    }
                    state.copy(progress = progressMap, getStates = updatedGetStates)
                }
            }
            .launchIn(viewModelScope)

        // Initial load of active downloads + recent history
        viewModelScope.launch {
            progressClient.acquire()
            refreshStatus()
            loadRecentHistory()
        }
        startStatusPolling()
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { progressClient.release() }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        _query.value = newQuery
    }

    fun playTrack(track: Track) = playbackManager.playTracks(listOf(track), 0)
    fun playNext(track: Track) = playbackManager.playNext(track)
    fun addToQueue(track: Track) = playbackManager.addToQueue(track)

    fun toggleMarkForDeletion(track: Track) {
        viewModelScope.launch {
            if (track.markedForDeletion) {
                libraryRepository.unmarkForDeletion(track.id)
            } else {
                libraryRepository.markForDeletion(track.id)
            }
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
                if (radioTracks.isNotEmpty()) playbackManager.playTracks(radioTracks)
            } catch (_: Exception) {}
        }
    }

    fun searchOnline(force: Boolean = false) {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return
        if (!force && _uiState.value.isDownloadSearching) return
        runDownloadSearch(query)
    }

    fun triggerDownload(result: DownloadResult) {
        val parsed = parseQuery(_uiState.value.query)
        val key = result.key()
        val artist = parsed.first.ifBlank { _uiState.value.parsedArtist }
        val track = parsed.second.ifBlank { _uiState.value.parsedTrack }
            .ifBlank { result.displayName }
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(getStates = state.getStates + (key to GetButtonState(state = GetState.Searching)))
            }
            try {
                DebugLog.d(TAG, "Trigger artist='$artist' track='$track' user=${result.username}")
                val response = zonikApi.triggerDownload(
                    DownloadTriggerRequest(
                        artist = artist,
                        track = track,
                        username = result.username,
                        filename = result.filename
                    )
                )
                val jobId = response.jobId
                _uiState.update { state ->
                    val gs = state.getStates[key] ?: GetButtonState()
                    val newGs = gs.copy(jobId = jobId, state = GetState.Searching)
                    state.copy(
                        getStates = state.getStates + (key to newGs),
                        toast = "Download queued"
                    )
                }
                refreshStatus()
            } catch (e: Exception) {
                DebugLog.e(TAG, "Trigger failed", e)
                _uiState.update { state ->
                    val gs = state.getStates[key] ?: GetButtonState()
                    state.copy(
                        getStates = state.getStates + (key to gs.copy(state = GetState.Failed, error = e.message)),
                        toast = "Download failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelTransfer(transfer: TransferInfo) {
        viewModelScope.launch {
            try {
                zonikApi.cancelTransfer(
                    CancelTransferRequest(username = transfer.username, filename = transfer.filename)
                )
                refreshStatus()
            } catch (e: Exception) {
                DebugLog.e(TAG, "Cancel failed", e)
                _uiState.update { it.copy(toast = "Cancel failed: ${e.message}") }
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            try {
                val statusResponse = zonikApi.getDownloadStatus()
                val activeJobs = zonikApi.getActiveJobs()
                _uiState.update {
                    it.copy(activeTransfers = statusResponse.transfers, activeJobs = activeJobs)
                }
            } catch (_: Exception) {}
        }
    }

    fun loadRecentHistory() {
        viewModelScope.launch {
            try {
                val response = zonikApi.getJobHistory(limit = 20)
                _uiState.update { it.copy(recentJobs = response.items) }
            } catch (_: Exception) {}
        }
    }

    fun dismissToast() {
        _uiState.update { it.copy(toast = null) }
    }

    private fun runLibrarySearch(query: String) {
        librarySearchJob?.cancel()
        // Clear download results when query changes
        _uiState.update {
            it.copy(
                downloadResults = emptyList(),
                hasDownloadSearched = false,
                downloadError = null
            )
        }
        librarySearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLibrarySearching = true, libraryError = null) }
            try {
                val (artists, albums, tracks) = libraryRepository.search(query)
                _uiState.update {
                    it.copy(
                        artists = artists,
                        albums = albums,
                        tracks = tracks,
                        isLibrarySearching = false,
                        hasSearched = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLibrarySearching = false,
                        hasSearched = true,
                        libraryError = e.message ?: "Search failed"
                    )
                }
            }
        }
    }

    private fun runDownloadSearch(query: String) {
        downloadSearchJob?.cancel()
        val (artist, track) = parseQuery(query)
        downloadSearchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadSearching = true,
                    downloadError = null,
                    parsedArtist = artist,
                    parsedTrack = track
                )
            }
            try {
                val request = if (artist.isNotBlank()) {
                    DownloadSearchRequest(artist = artist, track = track)
                } else {
                    DownloadSearchRequest(query = track)
                }
                val response = zonikApi.searchDownloads(request)
                val sorted = response.results.sortedWith(downloadResultComparator())
                _uiState.update {
                    it.copy(
                        downloadResults = sorted,
                        isDownloadSearching = false,
                        hasDownloadSearched = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloadSearching = false,
                        hasDownloadSearched = true,
                        downloadError = e.message ?: "Search failed"
                    )
                }
            }
        }
    }

    private fun startStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob = viewModelScope.launch {
            while (true) {
                delay(8_000)
                val state = _uiState.value
                val hasActive = state.activeTransfers.isNotEmpty() || state.activeJobs.isNotEmpty()
                if (hasActive) refreshStatus()
            }
        }
    }
}

// endregion

// region Helpers

internal fun DownloadResult.key(): String = "${username}|${filename}"

internal fun parseQuery(raw: String): Pair<String, String> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "" to ""
    // Split on " - " (with surrounding whitespace) — common artist/track separator
    val sep = Regex("\\s+[-—–]\\s+")
    val parts = trimmed.split(sep, limit = 2)
    return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        parts[0].trim() to parts[1].trim()
    } else {
        "" to trimmed
    }
}

private fun formatPriority(format: String): Int = when (format.uppercase()) {
    "FLAC" -> 0
    "MP3", "M4A", "AAC", "OGG" -> 1
    "WAV" -> 2
    else -> 3
}

private fun downloadResultComparator(): Comparator<DownloadResult> {
    return Comparator { a, b ->
        // Format priority
        val fp = formatPriority(a.format).compareTo(formatPriority(b.format))
        if (fp != 0) return@Comparator fp
        // For MP3-class, prefer >= 320kbps
        val aHigh = (a.bitRate ?: 0) >= 320
        val bHigh = (b.bitRate ?: 0) >= 320
        if (aHigh != bHigh) return@Comparator if (aHigh) -1 else 1
        // Slots free
        val aSlot = a.slotsFree == true || (a.freeUploadSlots ?: 0) > 0
        val bSlot = b.slotsFree == true || (b.freeUploadSlots ?: 0) > 0
        if (aSlot != bSlot) return@Comparator if (aSlot) -1 else 1
        // Bitrate descending
        val br = (b.bitRate ?: 0).compareTo(a.bitRate ?: 0)
        if (br != 0) return@Comparator br
        // File size descending
        b.size.compareTo(a.size)
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024.0
        i++
    }
    return "%.1f %s".format(v, units[i])
}

// endregion

// region Screen

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
    var recentExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.toast) {
        if (uiState.toast != null) {
            delay(3_000)
            viewModel.dismissToast()
        }
    }

    WithNeutralScheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                    if (uiState.query.isNotBlank()) {
                        FilterChipRow(selected = filter, onSelect = { filter = it })
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 200.dp)
                    ) {
                        if (uiState.query.isBlank()) {
                            // Empty state — show active downloads summary + recents
                            item("active") {
                                ActiveDownloadsSection(
                                    transfers = uiState.activeTransfers,
                                    activeJobs = uiState.activeJobs,
                                    progress = uiState.progress,
                                    onCancel = viewModel::cancelTransfer
                                )
                            }
                            item("recents") {
                                RecentDownloadsSection(
                                    expanded = recentExpanded,
                                    onToggle = { recentExpanded = !recentExpanded },
                                    recents = uiState.recentJobs,
                                    onRefresh = viewModel::loadRecentHistory
                                )
                            }
                            item("emptyHint") {
                                EmptyHint()
                            }
                        } else {
                            // Library results section
                            librarySection(
                                query = uiState.query,
                                filter = filter,
                                state = uiState,
                                onArtistClick = { onNavigateToArtist(it.id) },
                                onAlbumClick = { onNavigateToAlbum(it.id) },
                                onTrackClick = { viewModel.playTrack(it) },
                                onPlayNext = { viewModel.playNext(it) },
                                onAddToQueue = { viewModel.addToQueue(it) },
                                onToggleMarkForDeletion = { viewModel.toggleMarkForDeletion(it) },
                                onStartRadio = { viewModel.startRadio(it) }
                            )

                            // Get more section
                            getMoreSection(
                                state = uiState,
                                onSearchOnline = { viewModel.searchOnline() },
                                onTrigger = { viewModel.triggerDownload(it) }
                            )

                            item("activeFooter") {
                                if (uiState.activeTransfers.isNotEmpty() || uiState.activeJobs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SectionHeader("In progress")
                                    ActiveTransfersList(
                                        transfers = uiState.activeTransfers,
                                        progress = uiState.progress,
                                        onCancel = viewModel::cancelTransfer
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.toast != null) {
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        action = {
                            TextButton(onClick = viewModel::dismissToast) { Text("Dismiss") }
                        }
                    ) {
                        Text(uiState.toast!!)
                    }
                }
            }
        }
    }
}

// endregion

// region Library section

private fun androidx.compose.foundation.lazy.LazyListScope.librarySection(
    query: String,
    filter: SearchFilter,
    state: SearchUiState,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onToggleMarkForDeletion: (Track) -> Unit,
    onStartRadio: (Track) -> Unit
) {
    if (state.libraryError != null) {
        item("libraryError") {
            ErrorBanner(text = state.libraryError)
        }
        return
    }

    if (state.isLibrarySearching && !state.hasSearched) {
        item("libraryLoading") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        return
    }

    val showArtists = filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS
    val showAlbums = filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS
    val showTracks = filter == SearchFilter.ALL || filter == SearchFilter.TRACKS

    val anyResults = state.artists.isNotEmpty() || state.albums.isNotEmpty() || state.tracks.isNotEmpty()

    if (anyResults) {
        item("library-h") {
            SectionHeader("In your library")
        }

        if (filter == SearchFilter.ALL) {
            val topAlbum = state.albums.firstOrNull()
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

        if (showArtists && state.artists.isNotEmpty()) {
            item("artists-h") { SubHeader("Artists") }
            items(state.artists, key = { "artist-${it.id}" }) { artist ->
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

        if (showAlbums && state.albums.isNotEmpty()) {
            item("albums-h") { SubHeader("Albums") }
            items(state.albums, key = { "album-${it.id}" }) { album ->
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

        if (showTracks && state.tracks.isNotEmpty()) {
            item("tracks-h") { SubHeader("Tracks") }
            itemsIndexed(state.tracks, key = { _, track -> "track-${track.id}" }) { _, track ->
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
    } else if (state.hasSearched) {
        item("library-empty") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Nothing in your library matches \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// endregion

// region Get more section

private fun androidx.compose.foundation.lazy.LazyListScope.getMoreSection(
    state: SearchUiState,
    onSearchOnline: () -> Unit,
    onTrigger: (DownloadResult) -> Unit
) {
    if (state.query.isBlank()) return

    item("getmore-h") {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        ) {
            Text(
                text = "Get more",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (state.isDownloadSearching) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Searching network",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (state.hasDownloadSearched) {
                TextButton(onClick = onSearchOnline) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Search again")
                }
            } else {
                FilledTonalButton(onClick = onSearchOnline) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Search network")
                }
            }
        }
        if (state.parsedArtist.isNotBlank() && state.hasDownloadSearched) {
            Text(
                text = "Parsed: artist=\"${state.parsedArtist}\" track=\"${state.parsedTrack}\"",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    if (state.downloadError != null) {
        item("dl-err") { ErrorBanner(text = state.downloadError) }
    }

    if (state.hasDownloadSearched && state.downloadResults.isEmpty() && !state.isDownloadSearching) {
        item("dl-empty") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "No matches found on the network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (state.downloadResults.isNotEmpty()) {
        items(state.downloadResults, key = { it.key() }) { result ->
            DownloadCandidateRow(
                result = result,
                getState = state.getStates[result.key()] ?: GetButtonState(),
                onTrigger = { onTrigger(result) }
            )
        }
    }
}

// endregion

// region Active / Recent

@Composable
private fun ActiveDownloadsSection(
    transfers: List<TransferInfo>,
    activeJobs: List<JobInfo>,
    progress: Map<String, JobProgress>,
    onCancel: (TransferInfo) -> Unit
) {
    if (transfers.isEmpty() && activeJobs.isEmpty()) return
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionHeader("Active downloads")
        ActiveTransfersList(transfers = transfers, progress = progress, onCancel = onCancel)
        if (activeJobs.isNotEmpty()) {
            SubHeader("Queued (${activeJobs.size})")
            activeJobs.forEach { job ->
                ListItem(
                    headlineContent = {
                        Text(job.description?.takeIf { it.isNotBlank() } ?: job.type)
                    },
                    supportingContent = {
                        Text(
                            text = job.status.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Work, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun ActiveTransfersList(
    transfers: List<TransferInfo>,
    progress: Map<String, JobProgress>,
    onCancel: (TransferInfo) -> Unit
) {
    Column {
        transfers.forEach { transfer ->
            TransferItem(transfer = transfer, onCancel = { onCancel(transfer) })
        }
    }
}

@Composable
private fun TransferItem(transfer: TransferInfo, onCancel: () -> Unit) {
    Column {
        ListItem(
            headlineContent = {
                Text(
                    text = transfer.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TransferStateBadge(state = transfer.state)
                    if (transfer.state.equals("Transferring", ignoreCase = true)) {
                        Text(
                            text = buildString {
                                if (transfer.speedMbps.isNotEmpty()) append(transfer.speedMbps)
                                append(" · ${transfer.progress.toInt()}%")
                                transfer.etaSeconds?.let { append(" · ETA ${it}s") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    transfer.error?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            trailingContent = {
                val isActive = transfer.state.equals("Queued", ignoreCase = true) ||
                        transfer.state.equals("Transferring", ignoreCase = true)
                if (isActive) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )

        AnimatedVisibility(visible = transfer.state.equals("Transferring", ignoreCase = true)) {
            LinearProgressIndicator(
                progress = { transfer.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun TransferStateBadge(state: String) {
    val (containerColor, contentColor) = when (state.lowercase()) {
        "queued" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "transferring" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "completed" -> Color(0xFF2E7D32) to Color.White
        "failed", "denied" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    SuggestionChip(
        onClick = {},
        label = { Text(state, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor = contentColor
        ),
        modifier = Modifier.height(24.dp)
    )
}

@Composable
private fun RecentDownloadsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    recents: List<JobInfo>,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Surface(
            onClick = onToggle,
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recent downloads",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (expanded) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                if (recents.isEmpty()) {
                    Text(
                        text = "No recent downloads",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    recents.take(20).forEach { job ->
                        val statusColor = when (job.status.lowercase()) {
                            "completed", "complete" -> Color(0xFF2E7D32)
                            "failed", "error" -> MaterialTheme.colorScheme.error
                            "running", "in_progress" -> Color(0xFFED6C02)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val headline = job.description?.takeIf { it.isNotBlank() }
                            ?: job.type.replace("_", " ").replaceFirstChar { it.uppercase() }
                        ListItem(
                            headlineContent = {
                                Text(headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    text = job.status.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = when (job.status.lowercase()) {
                                        "completed", "complete" -> Icons.Default.Check
                                        "failed", "error" -> Icons.Default.ErrorOutline
                                        "running", "in_progress" -> Icons.Default.Sync
                                        else -> Icons.Default.Schedule
                                    },
                                    contentDescription = null,
                                    tint = statusColor
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 32.dp, end = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Search your library or the network",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tip: type \"Artist - Track\" for sharper results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// endregion

// region Search bar / chips / common rows

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun M3SearchBar(query: String, onQueryChange: (String) -> Unit) {
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
                            text = "Search music",
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
private fun FilterChipRow(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
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
private fun TopResultHero(query: String, album: Album, onClick: () -> Unit) {
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

@Composable
private fun SubHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun ErrorBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
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
                .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
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

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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

// endregion

// region Download candidate row

@Composable
private fun DownloadCandidateRow(
    result: DownloadResult,
    getState: GetButtonState,
    onTrigger: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        QualityChip(format = result.format, bitRate = result.bitRate)
                        Text(
                            text = result.sizeMb,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = result.username,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (result.slotsFree == true || (result.freeUploadSlots ?: 0) > 0) {
                            Text(
                                text = "· free slot",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                GetButton(getState = getState, onClick = onTrigger)
            }
            if (getState.state == GetState.Downloading && getState.total > 0) {
                LinearProgressIndicator(
                    progress = { getState.pct / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
                Text(
                    text = "${getState.pct.toInt()}% · ${formatBytes(getState.received)} / ${formatBytes(getState.total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (getState.state == GetState.Failed && getState.error != null) {
                Text(
                    text = getState.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun QualityChip(format: String, bitRate: Int?) {
    val isFlac = format.equals("FLAC", ignoreCase = true)
    val br = bitRate ?: 0
    val (container, content) = when {
        isFlac -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        br >= 320 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = if (isFlac) "FLAC" else if (br > 0) "$format ${br}k" else format
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun GetButton(getState: GetButtonState, onClick: () -> Unit) {
    when (getState.state) {
        GetState.Idle -> {
            FilledTonalButton(onClick = onClick) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Get", style = MaterialTheme.typography.labelLarge)
            }
        }
        GetState.Searching -> {
            FilledTonalButton(onClick = {}, enabled = false) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Queued", style = MaterialTheme.typography.labelLarge)
            }
        }
        GetState.Downloading -> {
            FilledTonalButton(onClick = {}, enabled = false) {
                Text(
                    text = "${getState.pct.toInt()}%",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        GetState.Done -> {
            FilledTonalButton(
                onClick = {},
                enabled = false,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF2E7D32).copy(alpha = 0.18f),
                    contentColor = Color(0xFF2E7D32)
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Done", style = MaterialTheme.typography.labelLarge)
            }
        }
        GetState.Failed -> {
            FilledTonalButton(
                onClick = onClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// endregion
