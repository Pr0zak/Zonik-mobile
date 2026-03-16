package com.zonik.app.ui.screens.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.media.PlaybackManager
import com.zonik.app.model.Track
import com.zonik.app.ui.components.CoverArt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- ViewModel ---

enum class RepeatState { OFF, ALL, ONE }

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    val currentTrack: StateFlow<Track?> = playbackManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val queue: StateFlow<List<Track>> = playbackManager.queue

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatState = MutableStateFlow(RepeatState.OFF)
    val repeatState: StateFlow<RepeatState> = _repeatState.asStateFlow()

    private val _isStarred = MutableStateFlow(false)
    val isStarred: StateFlow<Boolean> = _isStarred.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    init {
        viewModelScope.launch {
            currentTrack.collect { track ->
                _isStarred.value = track?.starred ?: false
            }
        }
    }

    fun togglePlayPause() = playbackManager.togglePlayPause()
    fun skipNext() = playbackManager.skipNext()
    fun skipPrevious() = playbackManager.skipPrevious()
    fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)
    fun getCurrentPosition(): Long = playbackManager.getCurrentPosition()
    fun getDuration(): Long = playbackManager.getDuration()

    fun toggleShuffle() {
        val newValue = !_shuffleEnabled.value
        _shuffleEnabled.value = newValue
        playbackManager.setShuffleEnabled(newValue)
    }

    fun cycleRepeat() {
        val next = when (_repeatState.value) {
            RepeatState.OFF -> RepeatState.ALL
            RepeatState.ALL -> RepeatState.ONE
            RepeatState.ONE -> RepeatState.OFF
        }
        _repeatState.value = next
        val mode = when (next) {
            RepeatState.OFF -> androidx.media3.common.Player.REPEAT_MODE_OFF
            RepeatState.ALL -> androidx.media3.common.Player.REPEAT_MODE_ALL
            RepeatState.ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
        }
        playbackManager.setRepeatMode(mode)
    }

    fun toggleStar() {
        val track = currentTrack.value ?: return
        viewModelScope.launch {
            if (_isStarred.value) {
                libraryRepository.unstar(track.id)
                _isStarred.value = false
            } else {
                libraryRepository.star(track.id)
                _isStarred.value = true
            }
        }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIndex = speeds.indexOf(_playbackSpeed.value)
        val nextIndex = if (currentIndex == -1 || currentIndex == speeds.lastIndex) 0 else currentIndex + 1
        _playbackSpeed.value = speeds[nextIndex]
    }
}

// --- Helpers ---

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

// --- Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit = {},
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val repeatState by viewModel.repeatState.collectAsState()
    val isStarred by viewModel.isStarred.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var showDetails by remember { mutableStateOf(false) }

    // Position polling
    LaunchedEffect(isPlaying, currentTrack) {
        while (true) {
            if (!isSeeking) {
                positionMs = viewModel.getCurrentPosition()
                durationMs = viewModel.getDuration()
            }
            delay(200L)
        }
    }

    val track = currentTrack

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse")
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Album art
            CoverArt(
                coverArtId = track?.coverArt,
                contentDescription = track?.album,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                size = 600
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Track title
            Text(
                text = track?.title ?: "",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Artist
            Text(
                text = track?.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Album
            Text(
                text = track?.album ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Seek bar
            val sliderValue = if (isSeeking) {
                seekPosition
            } else if (durationMs > 0) {
                positionMs.toFloat() / durationMs.toFloat()
            } else {
                0f
            }

            Slider(
                value = sliderValue,
                onValueChange = { value ->
                    isSeeking = true
                    seekPosition = value
                },
                onValueChangeFinished = {
                    val seekMs = (seekPosition * durationMs).toLong()
                    viewModel.seekTo(seekMs)
                    positionMs = seekMs
                    isSeeking = false
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Position / Duration labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val displayPosition = if (isSeeking) {
                    (seekPosition * durationMs).toLong()
                } else {
                    positionMs
                }
                Text(
                    text = formatDuration(displayPosition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary controls: Shuffle, Previous, Play/Pause, Next, Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                IconButton(onClick = { viewModel.skipPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(32.dp)
                    )
                }

                FloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = { viewModel.skipNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = { viewModel.cycleRepeat() }) {
                    Icon(
                        imageVector = when (repeatState) {
                            RepeatState.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatState != RepeatState.OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Secondary controls: Star, Speed, Timer, Queue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleStar() }) {
                    Icon(
                        imageVector = if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isStarred) "Unstar" else "Star",
                        tint = if (isStarred) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                TextButton(onClick = { viewModel.cyclePlaybackSpeed() }) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Playback Speed",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "${playbackSpeed}x")
                }

                IconButton(onClick = { /* TODO: Sleep timer */ }) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Sleep Timer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onOpenQueue) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Song details (collapsible)
            if (track != null) {
                Surface(
                    onClick = { showDetails = !showDetails },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Song Details",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Icon(
                                imageVector = if (showDetails) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = if (showDetails) "Collapse" else "Expand"
                            )
                        }

                        AnimatedVisibility(visible = showDetails) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                track.bitRate?.let { bitRate ->
                                    DetailRow(label = "Bitrate", value = "$bitRate kbps")
                                }
                                track.suffix?.let { format ->
                                    DetailRow(label = "Format", value = format.uppercase())
                                }
                                track.size?.let { size ->
                                    val formatted = formatFileSize(size)
                                    if (formatted.isNotEmpty()) {
                                        DetailRow(label = "File Size", value = formatted)
                                    }
                                }
                                track.duration.let { duration ->
                                    if (duration > 0) {
                                        DetailRow(
                                            label = "Duration",
                                            value = formatDuration(duration.toLong() * 1000)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
