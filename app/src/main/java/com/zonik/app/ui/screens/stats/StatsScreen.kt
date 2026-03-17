package com.zonik.app.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.db.StatCount
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.model.Album
import com.zonik.app.model.Artist
import com.zonik.app.ui.components.CoverArt
import com.zonik.app.ui.util.formatLargeDuration
import com.zonik.app.ui.util.formatLargeFileSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- ViewModel ---

data class StatsUiState(
    val isLoading: Boolean = true,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val genreCount: Int = 0,
    val totalDurationSeconds: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val starredTrackCount: Int = 0,
    val starredAlbumCount: Int = 0,
    val markedForDeletionCount: Int = 0,
    val formatDistribution: List<StatCount> = emptyList(),
    val bitrateDistribution: List<StatCount> = emptyList(),
    val topGenres: List<StatCount> = emptyList(),
    val yearDistribution: List<StatCount> = emptyList(),
    val topArtists: List<Artist> = emptyList(),
    val mostPlayedAlbums: List<Album> = emptyList(),
    val recentlyPlayedAlbums: List<Album> = emptyList()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val trackCount = libraryRepository.trackCount().first()
                val albumCount = libraryRepository.albumCount().first()
                val artistCount = libraryRepository.artistCount().first()
                val genreCount = libraryRepository.genreCount().first()
                val totalDuration = libraryRepository.totalDuration().first()
                val totalSize = libraryRepository.totalSize().first()
                val starredTracks = libraryRepository.getStarredTrackCount()
                val starredAlbums = libraryRepository.getStarredAlbumCount()
                val markedCount = libraryRepository.getMarkedForDeletionCount()
                val formats = libraryRepository.getFormatDistribution()
                val bitrates = libraryRepository.getBitrateDistribution()
                val genres = libraryRepository.getTopGenres()
                val years = libraryRepository.getYearDistribution()
                val topArtists = libraryRepository.getTopArtists()
                val mostPlayed = libraryRepository.getMostPlayedAlbums()
                val recentlyPlayed = libraryRepository.getRecentlyPlayedAlbums()

                _uiState.value = StatsUiState(
                    isLoading = false,
                    trackCount = trackCount,
                    albumCount = albumCount,
                    artistCount = artistCount,
                    genreCount = genreCount,
                    totalDurationSeconds = totalDuration,
                    totalSizeBytes = totalSize,
                    starredTrackCount = starredTracks,
                    starredAlbumCount = starredAlbums,
                    markedForDeletionCount = markedCount,
                    formatDistribution = formats,
                    bitrateDistribution = bitrates,
                    topGenres = genres,
                    yearDistribution = years,
                    topArtists = topArtists,
                    mostPlayedAlbums = mostPlayed,
                    recentlyPlayedAlbums = recentlyPlayed
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}

// --- Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadStats) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                windowInsets = WindowInsets(0)
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Library Overview
                SectionHeader("Library Overview")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("Tracks", "%,d".format(state.trackCount))
                            StatItem("Albums", "%,d".format(state.albumCount))
                            StatItem("Artists", "%,d".format(state.artistCount))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("Genres", "%,d".format(state.genreCount))
                            StatItem("Duration", formatLargeDuration(state.totalDurationSeconds))
                            StatItem("Size", formatLargeFileSize(state.totalSizeBytes))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("Starred", "%,d".format(state.starredTrackCount))
                            StatItem("Starred Albums", "%,d".format(state.starredAlbumCount))
                            StatItem("To Delete", "%,d".format(state.markedForDeletionCount))
                        }
                    }
                }

                // Format Distribution
                if (state.formatDistribution.isNotEmpty()) {
                    SectionHeader("Formats")
                    DistributionCard(state.formatDistribution, state.trackCount)
                }

                // Top Genres
                if (state.topGenres.isNotEmpty()) {
                    SectionHeader("Top Genres")
                    DistributionCard(state.topGenres.take(10), state.trackCount)
                }

                // Bitrate Distribution
                if (state.bitrateDistribution.isNotEmpty()) {
                    SectionHeader("Bitrate")
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val total = state.bitrateDistribution.sumOf { it.count }
                            state.bitrateDistribution.forEach { item ->
                                val label = if (item.label == "0") "Lossless" else "${item.label} kbps"
                                BarRow(label, item.count, total)
                            }
                        }
                    }
                }

                // Most Played Albums
                if (state.mostPlayedAlbums.isNotEmpty()) {
                    SectionHeader("Most Played Albums")
                    AlbumRow(state.mostPlayedAlbums)
                }

                // Recently Played Albums
                if (state.recentlyPlayedAlbums.isNotEmpty()) {
                    SectionHeader("Recently Played")
                    AlbumRow(state.recentlyPlayedAlbums)
                }

                // Top Artists
                if (state.topArtists.isNotEmpty()) {
                    SectionHeader("Top Artists")
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column {
                            state.topArtists.forEachIndexed { index, artist ->
                                ListItem(
                                    headlineContent = { Text(artist.name) },
                                    leadingContent = {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    trailingContent = {
                                        Text(
                                            "${artist.albumCount} albums",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                                if (index < state.topArtists.size - 1) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }

                // Year Distribution
                if (state.yearDistribution.isNotEmpty()) {
                    SectionHeader("By Decade")
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Group by decade
                            val decades = state.yearDistribution
                                .groupBy { (it.label.toIntOrNull() ?: 0) / 10 * 10 }
                                .map { (decade, items) -> "${decade}s" to items.sumOf { it.count } }
                                .sortedByDescending { it.second }
                            val total = decades.sumOf { it.second }
                            decades.forEach { (label, count) ->
                                BarRow(label, count, total)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// --- Components ---

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DistributionCard(items: List<StatCount>, totalTracks: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val total = items.sumOf { it.count }
            items.forEach { item ->
                BarRow(item.label.uppercase(), item.count, total)
            }
        }
    }
}

@Composable
private fun BarRow(label: String, count: Int, total: Int) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    val percent = (fraction * 100).toInt()
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "%,d ($percent%%)".format(count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun AlbumRow(albums: List<Album>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            Card(
                modifier = Modifier.width(130.dp)
            ) {
                Column {
                    CoverArt(
                        coverArtId = album.coverArt,
                        contentDescription = album.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        size = 300
                    )
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = album.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
