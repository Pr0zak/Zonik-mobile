package com.zonik.app.ui.screens.nowplaying

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.media.PlaybackManager
import com.zonik.app.model.Track
import com.zonik.app.ui.components.CoverArt
import com.zonik.app.ui.util.formatDurationMs
import com.zonik.app.ui.util.formatFileSize
import com.zonik.app.ui.util.formatDuration
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
    val isCasting: StateFlow<Boolean> = playbackManager.castManager.isCasting
    val castDeviceName: StateFlow<String?> = playbackManager.castManager.castDeviceName

    fun getCastContext() = playbackManager.castManager.getCastContext()

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
    fun skipToIndex(index: Int) = playbackManager.skipToIndex(index)
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
    val queue by viewModel.queue.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val repeatState by viewModel.repeatState.collectAsState()
    val isStarred by viewModel.isStarred.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val isCasting by viewModel.isCasting.collectAsState()
    val castDeviceName by viewModel.castDeviceName.collectAsState()

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var showDetails by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // Palette colors extracted from cover art
    var dominantColor by remember { mutableStateOf(Color(0xFF0B0E11)) }
    var accentColor by remember { mutableStateOf(Color(0xFF00BFA5)) }

    val animatedDominant by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(800),
        label = "dominantColor"
    )
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(800),
        label = "accentColor"
    )

    // Extract palette from cover art
    val context = LocalContext.current
    LaunchedEffect(currentTrack?.coverArt) {
        val coverArtId = currentTrack?.coverArt ?: return@LaunchedEffect
        val imageUrl = "http://localhost/rest/getCoverArt.view?id=$coverArtId&size=300"
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
                val palette = Palette.from(bitmap).generate()
                dominantColor = Color(palette.getDarkMutedColor(0xFF1A1A2E.toInt()))
                accentColor = Color(palette.getVibrantColor(0xFF7C4DFF.toInt()))
            }
        } catch (_: Exception) {}
    }

    // Position polling — 100ms for smooth seek bar
    LaunchedEffect(isPlaying, currentTrack) {
        while (true) {
            if (!isSeeking) {
                positionMs = viewModel.getCurrentPosition()
                durationMs = viewModel.getDuration()
            }
            delay(100L)
        }
    }

    val track = currentTrack

    // Full screen with blurred background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Blurred album art background
        if (track?.coverArt != null) {
            CoverArt(
                coverArtId = track.coverArt,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(80.dp),
                size = 150
            )
            // Dark overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                animatedDominant.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                animatedDominant.copy(alpha = 0.5f),
                                Color.Black
                            )
                        )
                    )
            )
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                IconButton(onClick = { showDetails = !showDetails }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Album art — large, centered, rounded
            CoverArt(
                coverArtId = track?.coverArt,
                contentDescription = track?.album,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                size = 600
            )

            Spacer(modifier = Modifier.weight(0.5f))

            // Track info
            Text(
                text = track?.title ?: "",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = track?.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = animatedAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = track?.album ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Seek bar — thin, accent colored
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
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = animatedAccent,
                    activeTrackColor = animatedAccent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )

            // Time labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val displayPos = if (isSeeking) (seekPosition * durationMs).toLong() else positionMs
                Text(
                    text = formatDurationMs(displayPos),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = formatDurationMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) animatedAccent else Color.White.copy(alpha = 0.5f)
                    )
                }

                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause — large circle
                FloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    shape = CircleShape,
                    containerColor = animatedAccent,
                    contentColor = Color.Black,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { viewModel.cycleRepeat() }) {
                    Icon(
                        imageVector = when (repeatState) {
                            RepeatState.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatState != RepeatState.OFF) animatedAccent else Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Secondary controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleStar() }) {
                    Icon(
                        imageVector = if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isStarred) animatedAccent else Color.White.copy(alpha = 0.5f)
                    )
                }

                // Format badge
                if (track?.suffix != null || track?.bitRate != null) {
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = listOfNotNull(
                                track.suffix?.uppercase(),
                                track.bitRate?.let { "${it}k" }
                            ).joinToString(" "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Cast button — uses AndroidView to wrap MediaRouteButton from Cast SDK
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        // MediaRouteButton requires a non-translucent theme background.
                        // Wrap in a ContextThemeWrapper with a solid dark background.
                        val themedCtx = android.view.ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat)
                        androidx.mediarouter.app.MediaRouteButton(themedCtx).apply {
                            try {
                                com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(themedCtx, this)
                            } catch (e: Exception) {
                                com.zonik.app.data.DebugLog.w("NowPlaying", "Cast button setup failed: ${e.message}")
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                )

                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (showQueue) animatedAccent else Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Casting indicator
            if (isCasting && castDeviceName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Cast,
                        contentDescription = null,
                        tint = animatedAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Casting to $castDeviceName",
                        style = MaterialTheme.typography.labelMedium,
                        color = animatedAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Collapsible song details
            AnimatedVisibility(visible = showDetails) {
                if (track != null) {
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            track.bitRate?.let { DetailRow("Bitrate", "$it kbps") }
                            track.suffix?.let { DetailRow("Format", it.uppercase()) }
                            track.size?.let {
                                val s = formatFileSize(it)
                                if (s.isNotEmpty()) DetailRow("Size", s)
                            }
                            if (track.duration > 0) DetailRow("Duration", formatDuration(track.duration))
                            track.genre?.let { DetailRow("Genre", it) }
                            track.year?.let { DetailRow("Year", it.toString()) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Queue bottom sheet
        if (showQueue) {
            ModalBottomSheet(
                onDismissRequest = { showQueue = false },
                containerColor = Color(0xFF1A1F24)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Queue (${queue.size} tracks)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        itemsIndexed(queue, key = { index, t -> "$index-${t.id}" }) { index, queueTrack ->
                            val isCurrent = queueTrack.id == track?.id
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = queueTrack.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isCurrent) animatedAccent else Color.White,
                                        style = if (isCurrent) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                                else MaterialTheme.typography.bodyLarge
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "${queueTrack.artist} · ${queueTrack.album}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                },
                                leadingContent = {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isCurrent) animatedAccent else Color.White.copy(alpha = 0.4f)
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    viewModel.skipToIndex(index)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
    }
}

