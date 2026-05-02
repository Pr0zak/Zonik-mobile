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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import com.zonik.app.ui.theme.ZonikShapes
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
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: com.zonik.app.data.repository.SettingsRepository,
    private val database: com.zonik.app.data.db.ZonikDatabase,
    private val waveformManager: com.zonik.app.media.WaveformManager,
    private val offlineCacheManager: com.zonik.app.media.OfflineCacheManager
) : ViewModel() {

    val currentTrack: StateFlow<Track?> = playbackManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val queue: StateFlow<List<Track>> = playbackManager.queue
    val isCasting: StateFlow<Boolean> = playbackManager.castManager.isCasting
    val castDeviceName: StateFlow<String?> = playbackManager.castManager.castDeviceName
    val isBuffering: StateFlow<Boolean> = playbackManager.isBuffering
    val playbackError: StateFlow<String?> = playbackManager.playbackError
    val waveform: StateFlow<FloatArray?> = waveformManager.currentWaveform
    val waveformEnabled: StateFlow<Boolean> = settingsRepository.visualizerEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun getCastContext() = playbackManager.castManager.getCastContext()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatState = MutableStateFlow(RepeatState.OFF)
    val repeatState: StateFlow<RepeatState> = _repeatState.asStateFlow()

    private val _isStarred = MutableStateFlow(false)
    val isStarred: StateFlow<Boolean> = _isStarred.asStateFlow()

    private val _isMarkedForDeletion = MutableStateFlow(false)
    val isMarkedForDeletion: StateFlow<Boolean> = _isMarkedForDeletion.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    val keepScreenOn: StateFlow<Boolean> = settingsRepository.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            currentTrack.collect { track ->
                if (track != null) {
                    val starred = database.trackDao().isStarred(track.id)
                    _isStarred.value = starred ?: false
                    val dbTrack = database.trackDao().getById(track.id)
                    _isMarkedForDeletion.value = dbTrack?.markedForDeletion ?: false
                    // Load waveform for current track
                    if (waveformEnabled.value) {
                        waveformManager.loadWaveform(track.id)
                    }
                } else {
                    _isStarred.value = false
                    _isMarkedForDeletion.value = false
                    waveformManager.clear()
                }
            }
        }
        // Also reload waveform when the setting is toggled on
        viewModelScope.launch {
            waveformEnabled.collect { enabled ->
                val track = currentTrack.value
                if (enabled && track != null) {
                    waveformManager.loadWaveform(track.id)
                } else if (!enabled) {
                    waveformManager.clear()
                }
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

    val offlineTrackIds: StateFlow<Set<String>> = offlineCacheManager.offlineTrackIds
    val downloadStates: StateFlow<Map<String, com.zonik.app.media.DownloadState>> = offlineCacheManager.downloadStates

    fun cacheQueueOffline() {
        val trackIds = queue.value.map { it.id }
        if (trackIds.isNotEmpty()) {
            offlineCacheManager.downloadTracks(trackIds)
        }
    }

    fun cancelQueueDownloads() {
        offlineCacheManager.cancelDownloads()
    }

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
        val newValue = !_isStarred.value
        _isStarred.value = newValue
        viewModelScope.launch {
            if (newValue) {
                libraryRepository.star(track.id)
            } else {
                libraryRepository.unstar(track.id)
            }
        }
    }

    fun toggleMarkForDeletion() {
        val track = currentTrack.value ?: return
        val newValue = !_isMarkedForDeletion.value
        _isMarkedForDeletion.value = newValue
        viewModelScope.launch {
            if (newValue) {
                libraryRepository.markForDeletion(track.id)
            } else {
                libraryRepository.unmarkForDeletion(track.id)
            }
        }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIndex = speeds.indexOf(_playbackSpeed.value)
        val nextIndex = if (currentIndex == -1 || currentIndex == speeds.lastIndex) 0 else currentIndex + 1
        _playbackSpeed.value = speeds[nextIndex]
        playbackManager.setPlaybackSpeed(speeds[nextIndex])
    }

    private val _isLoadingRadio = MutableStateFlow(false)
    val isLoadingRadio: StateFlow<Boolean> = _isLoadingRadio.asStateFlow()

    fun startRadio() {
        val track = currentTrack.value ?: return
        if (_isLoadingRadio.value) return
        _isLoadingRadio.value = true
        viewModelScope.launch {
            try {
                val radioTracks = libraryRepository.startRadio(track.id, track.genre, track.artistId)
                if (radioTracks.isNotEmpty()) {
                    playbackManager.playTracks(radioTracks)
                }
            } catch (e: Exception) {
                com.zonik.app.data.DebugLog.e("NowPlaying", "Start radio failed", e)
            } finally {
                _isLoadingRadio.value = false
            }
        }
    }
}

// --- Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val repeatState by viewModel.repeatState.collectAsState()
    val isStarred by viewModel.isStarred.collectAsState()
    val isMarkedForDeletion by viewModel.isMarkedForDeletion.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val isCasting by viewModel.isCasting.collectAsState()
    val castDeviceName by viewModel.castDeviceName.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val isLoadingRadio by viewModel.isLoadingRadio.collectAsState()
    val waveform by viewModel.waveform.collectAsState()
    val waveformEnabled by viewModel.waveformEnabled.collectAsState()

    // Keep screen on while Now Playing is visible (if enabled in settings)
    val activity = LocalContext.current as? android.app.Activity
    DisposableEffect(keepScreenOn, isPlaying) {
        if (keepScreenOn && isPlaying) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
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

    // Swipe down to dismiss — smooth animated offset
    val dragOffset = remember { Animatable(0f) }
    val dismissThreshold = 300f
    val coroutineScope = rememberCoroutineScope()

    val isTvDevice = com.zonik.app.ui.util.isTv()

    // Full screen with blurred background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!isTvDevice) Modifier.graphicsLayer {
                    val offset = dragOffset.value.coerceAtLeast(0f)
                    translationY = offset
                    alpha = 1f - (offset / (dismissThreshold * 3f))
                } else Modifier
            )
            .then(
                if (!isTvDevice) Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (dragOffset.value > dismissThreshold) {
                                    onBack()
                                } else {
                                    dragOffset.animateTo(0f, androidx.compose.animation.core.spring(
                                        dampingRatio = 0.7f,
                                        stiffness = 400f
                                    ))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffset.animateTo(0f, androidx.compose.animation.core.spring())
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                            }
                        }
                    )
                } else Modifier
            )
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
            // Top bar — minimal, glass pill
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
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                // Spacer to balance the top bar layout
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.weight(0.4f))

            // Album art — large, centered, rounded with glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                // Glow layer behind album art
                CoverArt(
                    coverArtId = track?.coverArt,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(50.dp)
                        .alpha(0.5f),
                    size = 300
                )
                // Main album art with shadow-like border
                CoverArt(
                    coverArtId = track?.coverArt,
                    contentDescription = track?.album,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(28.dp)),
                    size = 600
                )
            }

            Spacer(modifier = Modifier.weight(0.4f))

            // Track info in a glass card
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Format badge inline with title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = track?.title ?: "",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (track?.suffix != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val isLossless = track.suffix?.lowercase() in listOf("flac", "alac")
                            val tertiary = MaterialTheme.colorScheme.tertiary
                            Surface(
                                color = if (isLossless) tertiary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = listOfNotNull(
                                        track.suffix?.uppercase(),
                                        track.bitRate?.let { "${it}k" }
                                    ).joinToString(" "),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isLossless) tertiary else Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = track?.artist ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = animatedAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = (track?.album ?: "").uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.5.sp
                        ),
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Seek bar — waveform or standard slider
            val sliderValue = if (isSeeking) {
                seekPosition
            } else if (durationMs > 0) {
                positionMs.toFloat() / durationMs.toFloat()
            } else {
                0f
            }

            val showWaveform = waveformEnabled && waveform != null && !isCasting

            Box(modifier = Modifier.fillMaxWidth()) {
                // Waveform visualization behind the slider
                if (showWaveform) {
                    com.zonik.app.ui.components.WaveformBars(
                        waveform = waveform!!,
                        progress = sliderValue,
                        activeColor = animatedAccent,
                        inactiveColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .align(Alignment.Center)
                    )
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
                    thumb = {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    },
                    track = { sliderState ->
                        if (showWaveform) {
                            // Invisible track — waveform replaces it visually
                            Spacer(modifier = Modifier.fillMaxWidth().height(4.dp))
                        } else {
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = animatedAccent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                                ),
                                thumbTrackGapSize = 0.dp,
                                drawStopIndicator = null
                            )
                        }
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = animatedAccent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                    )
                )
            }

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
                    color = Color.White.copy(alpha = 0.5f)
                )
                Text(
                    text = formatDurationMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary controls — larger, more breathing room
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) animatedAccent else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Play/Pause — gradient circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(animatedAccent, animatedAccent.copy(alpha = 0.7f))
                            )
                        )
                        .clickable { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = Color.Black
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(onClick = { viewModel.cycleRepeat() }) {
                    Icon(
                        imageVector = when (repeatState) {
                            RepeatState.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatState != RepeatState.OFF) animatedAccent else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Secondary controls — glass pill bar
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
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

                    IconButton(onClick = { viewModel.toggleMarkForDeletion() }) {
                        Icon(
                            imageVector = if (isMarkedForDeletion) Icons.Default.Delete else Icons.Default.DeleteOutline,
                            contentDescription = "Mark for Deletion",
                            tint = if (isMarkedForDeletion) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.5f)
                        )
                    }

                    // Start Radio button
                    IconButton(
                        onClick = { viewModel.startRadio() },
                        enabled = !isLoadingRadio && track != null
                    ) {
                        if (isLoadingRadio) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "Start Radio",
                                tint = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Cast button
                    val activityContext = LocalContext.current
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { _ ->
                            androidx.mediarouter.app.MediaRouteButton(activityContext).apply {
                                try {
                                    com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(activityContext, this)
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
            }

            // Casting indicator
            if (isCasting && castDeviceName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = animatedAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
            }

            // Connection error banner
            if (playbackError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (playbackError?.contains("retrying") == true || playbackError?.contains("Slow") == true) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = playbackError ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Queue bottom sheet
        if (showQueue) {
            ModalBottomSheet(
                onDismissRequest = { showQueue = false },
                containerColor = Color(0xFF151320),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val offlineIds by viewModel.offlineTrackIds.collectAsState()
                    val downloadStates by viewModel.downloadStates.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "QUEUE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = animatedAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${queue.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = animatedAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        // Cache queue for offline button (toggles between download/cancel)
                        val allCached = queue.isNotEmpty() && queue.all { it.id in offlineIds }
                        val anyDownloading = downloadStates.values.any { it == com.zonik.app.media.DownloadState.DOWNLOADING || it == com.zonik.app.media.DownloadState.QUEUED }
                        IconButton(
                            onClick = {
                                if (anyDownloading) viewModel.cancelQueueDownloads()
                                else viewModel.cacheQueueOffline()
                            },
                            enabled = !allCached
                        ) {
                            Icon(
                                when {
                                    allCached -> Icons.Default.CloudDone
                                    anyDownloading -> Icons.Default.Close
                                    else -> Icons.Default.CloudDownload
                                },
                                contentDescription = when {
                                    allCached -> "Queue cached"
                                    anyDownloading -> "Cancel downloads"
                                    else -> "Cache queue offline"
                                },
                                tint = when {
                                    allCached -> Color(0xFF4CAF50)
                                    anyDownloading -> Color(0xFFE57373)
                                    else -> Color.White.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (anyDownloading || (offlineIds.isNotEmpty() && queue.any { it.id in offlineIds })) {
                            val cachedCount = queue.count { it.id in offlineIds }
                            Text(
                                text = "$cachedCount/${queue.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (allCached) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                    // Auto-scroll to currently playing track when queue opens
                    val currentIndex = queue.indexOfFirst { it.id == track?.id }
                    LaunchedEffect(currentIndex) {
                        if (currentIndex >= 0) {
                            lazyListState.animateScrollToItem(
                                index = maxOf(0, currentIndex - 2) // show 2 items above for context
                            )
                        }
                    }
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        itemsIndexed(queue, key = { index, t -> "$index-${t.id}" }) { index, queueTrack ->
                            val isCurrent = queueTrack.id == track?.id
                            val rowBg = if (index % 2 == 0) Color.White.copy(alpha = 0.03f) else Color.Transparent
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .clickable { viewModel.skipToIndex(index) }
                            ) {
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
                                        CoverArt(
                                            coverArtId = queueTrack.coverArt,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            size = 100
                                        )
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Offline status indicator
                                            val trackDownloadState = downloadStates[queueTrack.id]
                                            val isTrackOffline = queueTrack.id in offlineIds
                                            when {
                                                isTrackOffline -> Icon(
                                                    Icons.Default.CloudDone,
                                                    contentDescription = "Cached",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = Color(0xFF4CAF50)
                                                )
                                                trackDownloadState == com.zonik.app.media.DownloadState.DOWNLOADING -> CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = animatedAccent
                                                )
                                                trackDownloadState == com.zonik.app.media.DownloadState.QUEUED -> Icon(
                                                    Icons.Default.Schedule,
                                                    contentDescription = "Queued",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = Color.White.copy(alpha = 0.3f)
                                                )
                                                else -> {}
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isCurrent) animatedAccent else Color.White.copy(alpha = 0.4f)
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
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

