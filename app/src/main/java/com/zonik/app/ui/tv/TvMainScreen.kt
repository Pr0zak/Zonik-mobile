package com.zonik.app.ui.tv

import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.media.PlaybackManager
import com.zonik.app.model.Album
import com.zonik.app.model.Track
import com.zonik.app.ui.components.CoverArt
import com.zonik.app.ui.theme.ZonikColors
import com.zonik.app.ui.theme.ZonikShapes
import com.zonik.app.ui.util.formatDuration
import com.zonik.app.ui.util.formatDurationMs
import com.zonik.app.ui.util.tvFocusHighlight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class TvViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val libraryRepository: LibraryRepository,
    private val syncManager: com.zonik.app.data.repository.SyncManager,
    private val logUploader: com.zonik.app.data.api.LogUploader,
    private val updateChecker: com.zonik.app.data.api.UpdateChecker
) : ViewModel() {

    // Playback state (delegated from PlaybackManager)
    val currentTrack: StateFlow<Track?> = playbackManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val queue: StateFlow<List<Track>> = playbackManager.queue

    // Library data
    val albums: StateFlow<List<Album>> = libraryRepository.getAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tracks: StateFlow<List<Track>> = libraryRepository.getAllTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTracks: StateFlow<List<Track>> = libraryRepository.getRecentTracks(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recentAlbums = MutableStateFlow<List<Album>>(emptyList())
    val recentAlbums: StateFlow<List<Album>> = _recentAlbums.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.getRecentAlbums(20).collect { _recentAlbums.value = it }
        }
    }

    fun shuffleMix() {
        viewModelScope.launch {
            try {
                val songs = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    libraryRepository.getRandomSongs(100)
                }
                if (songs.isNotEmpty()) {
                    playbackManager.playTracks(songs)
                }
            } catch (e: Exception) {
                com.zonik.app.data.DebugLog.e("TvVM", "Shuffle mix failed", e)
            }
        }
    }

    fun shuffleFavorites() {
        viewModelScope.launch {
            try {
                val starred = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    libraryRepository.getStarredTracks().shuffled().take(100)
                }
                if (starred.isNotEmpty()) {
                    playbackManager.playTracks(starred)
                }
            } catch (e: Exception) {
                com.zonik.app.data.DebugLog.e("TvVM", "Shuffle favorites failed", e)
            }
        }
    }

    fun playTrack(track: Track) {
        val allTracks = tracks.value
        val index = allTracks.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            playbackManager.playTracks(allTracks, index)
        } else {
            playbackManager.playTracks(listOf(track))
        }
    }

    fun playAlbum(albumId: String) {
        viewModelScope.launch {
            try {
                val (_, albumTracks) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    libraryRepository.getAlbumDetail(albumId)
                }
                if (albumTracks.isNotEmpty()) {
                    playbackManager.playTracks(albumTracks)
                }
            } catch (_: Exception) {}
        }
    }

    fun togglePlayPause() = playbackManager.togglePlayPause()
    fun skipNext() = playbackManager.skipNext()
    fun skipPrevious() = playbackManager.skipPrevious()
    fun getCurrentPosition(): Long = playbackManager.getCurrentPosition()
    fun getDuration(): Long = playbackManager.getDuration()

    private val _isStarred = MutableStateFlow(false)
    val isStarred: StateFlow<Boolean> = _isStarred.asStateFlow()

    fun refreshStarred() {
        val track = currentTrack.value ?: return
        _isStarred.value = track.starred
    }

    // Beat detection via Visualizer
    private val _bassLevel = MutableStateFlow(0f)
    val bassLevel: StateFlow<Float> = _bassLevel.asStateFlow()
    private var visualizer: android.media.audiofx.Visualizer? = null

    private var beatJob: kotlinx.coroutines.Job? = null

    fun startVisualizer() {
        if (visualizer != null || beatJob?.isActive == true) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(2000)
                val sessionId = playbackManager.getAudioSessionId()
                com.zonik.app.data.DebugLog.d("TvVM", "Got audio session ID: $sessionId")
                if (sessionId != 0) {
                    val viz = android.media.audiofx.Visualizer(sessionId)
                    viz.captureSize = 128
                    viz.setDataCaptureListener(object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: android.media.audiofx.Visualizer?, waveform: ByteArray?, rate: Int) {}
                        override fun onFftDataCapture(v: android.media.audiofx.Visualizer?, fft: ByteArray?, rate: Int) {
                            fft ?: return
                            var bass = 0f
                            for (i in 1..4) {
                                val re = fft[2 * i].toFloat()
                                val im = if (2 * i + 1 < fft.size) fft[2 * i + 1].toFloat() else 0f
                                bass += kotlin.math.sqrt(re * re + im * im)
                            }
                            _bassLevel.value = (bass / 512f).coerceIn(0f, 1f)
                        }
                    }, android.media.audiofx.Visualizer.getMaxCaptureRate() / 2, false, true)
                    viz.enabled = true
                    visualizer = viz
                    com.zonik.app.data.DebugLog.d("TvVM", "Visualizer started (session=$sessionId)")
                    return@launch
                }
            } catch (e: Exception) {
                com.zonik.app.data.DebugLog.w("TvVM", "Visualizer failed: ${e.message}")
            }
            // Fallback: simulate beat with random pulses (~120 BPM)
            com.zonik.app.data.DebugLog.d("TvVM", "Using simulated beat (Visualizer unavailable)")
            beatJob = viewModelScope.launch {
                while (true) {
                    if (isPlaying.value) {
                        _bassLevel.value = 0.4f + (Math.random().toFloat() * 0.4f)
                        kotlinx.coroutines.delay(50)
                        _bassLevel.value = 0f
                    }
                    kotlinx.coroutines.delay(450 + (Math.random() * 100).toLong()) // ~120 BPM
                }
            }
        }
    }

    fun stopVisualizer() {
        visualizer?.release()
        visualizer = null
        beatJob?.cancel()
        beatJob = null
        _bassLevel.value = 0f
    }

    override fun onCleared() {
        super.onCleared()
        stopVisualizer()
    }

    fun toggleStar() {
        val track = currentTrack.value ?: return
        viewModelScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                if (_isStarred.value) {
                    libraryRepository.unstar(track.id)
                } else {
                    libraryRepository.star(track.id)
                }
            }
            _isStarred.value = !_isStarred.value
        }
    }

    val syncState = syncManager.syncState

    fun syncNow() {
        viewModelScope.launch { syncManager.fullSync() }
    }

    private val _logUploadResult = MutableStateFlow<String?>(null)
    val logUploadResult: StateFlow<String?> = _logUploadResult.asStateFlow()

    fun uploadLogs() {
        viewModelScope.launch {
            _logUploadResult.value = "Uploading..."
            val id = logUploader.uploadLogsToServer()
            _logUploadResult.value = if (id != null) "Uploaded (ID: $id)" else "Upload failed"
        }
    }

    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus: StateFlow<String?> = _updateStatus.asStateFlow()

    private val _updateProgress = MutableStateFlow<Float?>(null)
    val updateProgress: StateFlow<Float?> = _updateProgress.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateStatus.value = "Checking..."
            try {
                val update = updateChecker.checkForUpdate()
                if (update != null) {
                    _updateStatus.value = "Downloading v${update.version}..."
                    val success = updateChecker.downloadAndInstall(update) { progress ->
                        _updateProgress.value = progress
                    }
                    _updateStatus.value = if (success) "Installing..." else "Download failed"
                    _updateProgress.value = null
                } else {
                    _updateStatus.value = "Up to date"
                }
            } catch (e: Exception) {
                _updateStatus.value = "Failed: ${e.message?.take(30)}"
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Tab definitions
// ──────────────────────────────────────────────────────────────────────────────

private enum class TvTab(val label: String) {
    HOME("Home"),
    SETTINGS("Settings")
}

private enum class LibrarySubTab(val label: String) {
    ALBUMS("Albums"),
    TRACKS("Tracks"),
    FAVORITES("Favorites")
}

// ──────────────────────────────────────────────────────────────────────────────
// Colors
// ──────────────────────────────────────────────────────────────────────────────

private val TvBackground = Color(0xFF151320)
private val TvCardBackground = Color(0xFF1E1C2A)

// ──────────────────────────────────────────────────────────────────────────────
// Main Screen
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun TvMainScreen(
    onNavigateToAlbum: (String) -> Unit = {},
    onDisconnected: () -> Unit = {},
    viewModel: TvViewModel = hiltViewModel()
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var selectedTab by remember { mutableStateOf(TvTab.HOME) }
    var isScreensaver by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Screensaver timer: activate after 30s idle when playing
    LaunchedEffect(lastInteraction, isPlaying, selectedTab) {
        if (isPlaying && selectedTab == TvTab.HOME && !isScreensaver) {
            delay(10_000)
            isScreensaver = true
        }
    }

    BackHandler(enabled = isScreensaver || selectedTab != TvTab.HOME) {
        if (isScreensaver) {
            isScreensaver = false
            lastInteraction = System.currentTimeMillis()
        } else {
            selectedTab = TvTab.HOME
        }
    }

    // Ambient colors from album art palette
    var ambientDominant by remember { mutableStateOf(TvBackground) }
    var ambientAccent by remember { mutableStateOf(Color(0xFF7C4DFF)) }
    var ambientMuted by remember { mutableStateOf(Color(0xFF534AB7)) }
    val animatedBg by animateColorAsState(ambientDominant, tween(1200), label = "bg")
    val animatedAccent by animateColorAsState(ambientAccent, tween(1200), label = "bgAcc")
    val animatedMuted by animateColorAsState(ambientMuted, tween(1200), label = "bgMut")
    val paletteCtx = LocalContext.current
    LaunchedEffect(currentTrack?.coverArt) {
        val coverArtId = currentTrack?.coverArt ?: return@LaunchedEffect
        try {
            val request = ImageRequest.Builder(paletteCtx)
                .data("http://localhost/rest/getCoverArt.view?id=$coverArtId&size=300")
                .allowHardware(false)
                .build()
            val result = paletteCtx.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
                val palette = Palette.from(bitmap).generate()
                ambientDominant = Color(palette.getDarkMutedColor(0xFF151320.toInt()))
                ambientAccent = Color(palette.getVibrantColor(0xFF7C4DFF.toInt()))
                ambientMuted = Color(palette.getLightMutedColor(palette.getMutedColor(0xFF534AB7.toInt())))
            }
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (currentTrack != null)
                    Brush.radialGradient(listOf(animatedBg.copy(alpha = 0.6f), TvBackground))
                else TvBackground.let { Brush.verticalGradient(listOf(it, it)) }
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    // Screensaver: only back exits, D-pad left/right/center control playback
                    if (isScreensaver) {
                        com.zonik.app.data.DebugLog.d("TV-Key", "Screensaver key: code=${keyEvent.nativeKeyEvent.keyCode} name=${android.view.KeyEvent.keyCodeToString(keyEvent.nativeKeyEvent.keyCode)}")
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_BACK -> {
                                isScreensaver = false
                                lastInteraction = System.currentTimeMillis()
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                viewModel.skipPrevious()
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                viewModel.skipNext()
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                            android.view.KeyEvent.KEYCODE_BUTTON_SELECT,
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                viewModel.togglePlayPause()
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_UP,
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                // Ignore up/down in screensaver
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                if (!isPlaying) viewModel.togglePlayPause()
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                if (isPlaying) viewModel.togglePlayPause()
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent true // consume all other keys in screensaver
                        }
                    }
                    lastInteraction = System.currentTimeMillis()
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { viewModel.togglePlayPause(); true }
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!isPlaying) viewModel.togglePlayPause(); true }
                        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (isPlaying) viewModel.togglePlayPause(); true }
                        android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> { viewModel.skipNext(); true }
                        android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { viewModel.skipPrevious(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        if (!isScreensaver) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left sidebar navigation
            TvSidebar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            // Content + playback bar column
            Column(modifier = Modifier.weight(1f)) {
                // Content area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 48.dp, top = 27.dp)
                ) {
                    when (selectedTab) {
                        TvTab.HOME -> TvHomeContent(
                            viewModel = viewModel,
                            onAlbumClick = onNavigateToAlbum,
                            ambientColor = animatedBg
                        )
                        TvTab.SETTINGS -> TvSettingsContent(
                            viewModel = viewModel,
                            onDisconnected = onDisconnected
                        )
                    }
                }

                // (Playback bar removed — Now Playing card has controls + progress)
            }
        }
        } // end if (!isScreensaver)

        // Screensaver — replaces all content (prevents input leaking to buttons behind)
        if (isScreensaver && currentTrack != null) {
            // Start/stop visualizer with screensaver
            LaunchedEffect(Unit) {
                viewModel.startVisualizer()
            }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { viewModel.stopVisualizer() }
            }
            val bassLevel by viewModel.bassLevel.collectAsState()
            TvScreensaver(
                track = currentTrack!!,
                isPlaying = isPlaying,
                viewModel = viewModel,
                dominantColor = animatedBg,
                accentColor = animatedAccent,
                mutedColor = animatedMuted,
                bassLevel = bassLevel
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Screensaver
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvScreensaver(
    track: Track,
    isPlaying: Boolean,
    viewModel: TvViewModel,
    dominantColor: Color,
    accentColor: Color,
    mutedColor: Color = Color(0xFF534AB7),
    bassLevel: Float = 0f
) {
    val particleColors = listOf(accentColor, mutedColor, dominantColor)

    // Slow album art scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "ssAnim")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ssScale"
    )

    // Position polling
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, track) {
        while (true) {
            positionMs = viewModel.getCurrentPosition()
            durationMs = viewModel.getDuration()
            delay(1000L)
        }
    }

    // (Particle system composable handles all particle logic)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.9f),
                        Color(0xFF0A0910)
                    )
                )
            )
    ) {
        // Advanced particle system with beat reactivity
        ParticleSystem(
            bassLevel = bassLevel,
            colors = particleColors,
            modifier = Modifier.fillMaxSize(),
            centerX = 0.5f,
            centerY = 0.35f
        )

        // Centered content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 27.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Album art with slow breathing scale
            CoverArt(
                coverArtId = track.coverArt,
                contentDescription = track.title,
                modifier = Modifier
                    .size(300.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .clip(ZonikShapes.coverArtLargeShape),
                size = 600
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Track info
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Progress bar
            Spacer(modifier = Modifier.height(24.dp))
            val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDurationMs(positionMs), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
                Text(formatDurationMs(durationMs), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Sidebar Navigation
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvSidebar(
    selectedTab: TvTab,
    onTabSelected: (TvTab) -> Unit
) {
    val sidebarIcons = mapOf(
        TvTab.HOME to Icons.Default.Home,
        TvTab.SETTINGS to Icons.Default.Settings
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(Color(0xFF1A1824))
            .padding(vertical = 27.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo at top
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.zonik.app.R.drawable.ic_logo_z),
            contentDescription = "Zonik",
            tint = ZonikColors.gold,
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Nav items
        TvTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val icon = sidebarIcons[tab] ?: Icons.Default.Home
            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) ZonikColors.gold.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .tvFocusHighlight(RoundedCornerShape(12.dp))
                    .clickable { onTabSelected(tab) }
                    .focusable(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tab.label,
                    tint = if (isSelected) ZonikColors.gold else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) ZonikColors.gold else Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Home Tab
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvHomeContent(
    viewModel: TvViewModel,
    onAlbumClick: (String) -> Unit,
    ambientColor: Color = TvCardBackground
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val recentTracks by viewModel.recentTracks.collectAsState()
    val recentAlbums by viewModel.recentAlbums.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp)
    ) {
        // Shuffle buttons side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Shuffle Mix
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(ZonikShapes.buttonShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(ZonikColors.gradientStart, ZonikColors.gradientEnd)
                        ),
                        ZonikShapes.buttonShape
                    )
                    .tvFocusHighlight(ZonikShapes.buttonShape)
                    .clickable { viewModel.shuffleMix() }
                    .focusable(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Shuffle, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Shuffle Mix", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Shuffle Favorites
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(ZonikShapes.buttonShape)
                    .background(TvCardBackground, ZonikShapes.buttonShape)
                    .border(1.dp, ZonikColors.gold.copy(alpha = 0.3f), ZonikShapes.buttonShape)
                    .tvFocusHighlight(ZonikShapes.buttonShape)
                    .clickable { viewModel.shuffleFavorites() }
                    .focusable(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Favorite, null, tint = ZonikColors.gold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Shuffle Favorites", style = MaterialTheme.typography.titleLarge, color = ZonikColors.gold, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Now Playing section
        if (currentTrack != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                ambientColor.copy(alpha = 0.8f),
                                TvCardBackground
                            )
                        ),
                        ZonikShapes.cardShape
                    )
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(
                    coverArtId = currentTrack!!.coverArt,
                    contentDescription = currentTrack!!.title,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(ZonikShapes.coverArtLargeShape),
                    size = 600
                )
                Spacer(modifier = Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack!!.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentTrack!!.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentTrack!!.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Playback controls
                    Spacer(modifier = Modifier.height(16.dp))
                    val isStarred by viewModel.isStarred.collectAsState()
                    LaunchedEffect(currentTrack) { viewModel.refreshStarred() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Star/unstar
                        IconButton(
                            onClick = { viewModel.toggleStar() },
                            modifier = Modifier
                                .size(48.dp)
                                .tvFocusHighlight(CircleShape)
                        ) {
                            Icon(
                                if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (isStarred) "Unstar" else "Star",
                                tint = if (isStarred) ZonikColors.gold else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.skipPrevious() },
                            modifier = Modifier
                                .size(48.dp)
                                .tvFocusHighlight(CircleShape)
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(ZonikColors.gradientStart, ZonikColors.gradientEnd)),
                                    CircleShape
                                )
                                .tvFocusHighlight(CircleShape)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.skipNext() },
                            modifier = Modifier
                                .size(48.dp)
                                .tvFocusHighlight(CircleShape)
                        ) {
                            Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    // Progress bar
                    Spacer(modifier = Modifier.height(12.dp))
                    var positionMs by remember { mutableLongStateOf(0L) }
                    var durationMs by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(isPlaying, currentTrack) {
                        while (true) {
                            positionMs = viewModel.getCurrentPosition()
                            durationMs = viewModel.getDuration()
                            delay(500L)
                        }
                    }
                    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = ZonikColors.gold,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationMs(positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = formatDurationMs(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Bottom spacing for playback bar clearance
        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Library Tab
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvLibraryContent(
    viewModel: TvViewModel,
    onAlbumClick: (String) -> Unit
) {
    val albums by viewModel.albums.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    var subTab by remember { mutableStateOf(LibrarySubTab.ALBUMS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
    ) {
        // Sub-tab filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LibrarySubTab.entries.forEach { tab ->
                val isSelected = tab == subTab
                FilterChip(
                    selected = isSelected,
                    onClick = { subTab = tab },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZonikColors.gradientStart,
                        selectedLabelColor = Color.White,
                        containerColor = TvCardBackground,
                        labelColor = Color.White.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .tvFocusHighlight(ZonikShapes.buttonShape)
                        .focusable()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Grid content based on sub-tab
        when (subTab) {
            LibrarySubTab.ALBUMS -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albums, key = { it.id }) { album ->
                        TvAlbumGridCard(
                            album = album,
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }
            }
            LibrarySubTab.TRACKS -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tracks, key = { it.id }) { track ->
                        TvTrackGridCard(
                            track = track,
                            onClick = { viewModel.playTrack(track) }
                        )
                    }
                }
            }
            LibrarySubTab.FAVORITES -> {
                val favorites = tracks.filter { it.starred }
                if (favorites.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No favorites yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(favorites, key = { it.id }) { track ->
                            TvTrackGridCard(
                                track = track,
                                onClick = { viewModel.playTrack(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Placeholder tabs
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvSearchPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Search coming soon",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun TvSettingsContent(
    viewModel: TvViewModel,
    onDisconnected: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val syncState by viewModel.syncState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Sync
        TvSettingsButton(
            icon = Icons.Default.Sync,
            title = if (syncState.isSyncing) "Syncing..." else "Sync Library",
            subtitle = when {
                syncState.isSyncing -> syncState.phase.ifEmpty { "Starting..." }
                syncState.lastSyncResult != null -> syncState.lastSyncResult!!
                else -> "Sync tracks, albums, and artists from server"
            },
            onClick = { viewModel.syncNow() },
            enabled = !syncState.isSyncing,
            isLoading = syncState.isSyncing
        )

        // Upload Logs
        val logResult by viewModel.logUploadResult.collectAsState()
        TvSettingsButton(
            icon = Icons.Default.Upload,
            title = "Upload Logs",
            subtitle = logResult ?: "Send debug logs to server for troubleshooting",
            onClick = { viewModel.uploadLogs() },
            isLoading = logResult == "Uploading..."
        )

        // Check Update
        val updateStatus by viewModel.updateStatus.collectAsState()
        val updateProgress by viewModel.updateProgress.collectAsState()
        TvSettingsButton(
            icon = Icons.Default.SystemUpdate,
            title = "Check for Update",
            subtitle = updateStatus ?: "Download and install latest version",
            onClick = { viewModel.checkForUpdate() },
            isLoading = updateStatus == "Checking..." || updateProgress != null
        )

        // Disconnect
        TvSettingsButton(
            icon = Icons.Default.Logout,
            title = "Disconnect",
            subtitle = "Log out from server",
            onClick = onDisconnected,
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun TvSettingsButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    tint: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvCardBackground, ZonikShapes.cardShape)
            .tvFocusHighlight(ZonikShapes.cardShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = ZonikColors.gold
            )
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Playback Bar
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvPlaybackBar(
    track: Track,
    isPlaying: Boolean,
    viewModel: TvViewModel,
    modifier: Modifier = Modifier
) {
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // Poll playback position
    LaunchedEffect(isPlaying) {
        while (true) {
            positionMs = viewModel.getCurrentPosition()
            durationMs = viewModel.getDuration()
            delay(200L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ZonikColors.glassBg)
            .padding(bottom = 8.dp)
    ) {
        // Thin progress bar at top of playback bar
        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(ZonikColors.gold)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover art
            CoverArt(
                coverArtId = track.coverArt,
                contentDescription = track.title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(ZonikShapes.coverArtShape),
                size = 100
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Transport controls
            IconButton(
                onClick = { viewModel.skipPrevious() },
                modifier = Modifier
                    .size(40.dp)
                    .tvFocusHighlight(CircleShape)
                    .focusable()
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(ZonikColors.gradientStart, ZonikColors.gradientEnd)
                        ),
                        CircleShape
                    )
                    .tvFocusHighlight(CircleShape)
                    .focusable()
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { viewModel.skipNext() },
                modifier = Modifier
                    .size(40.dp)
                    .tvFocusHighlight(CircleShape)
                    .focusable()
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Position / Duration
            Text(
                text = "${formatDurationMs(positionMs)} / ${formatDurationMs(durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Card Components
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvAlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(ZonikShapes.cardShape)
            .background(TvCardBackground, ZonikShapes.cardShape)
            .tvFocusHighlight(ZonikShapes.cardShape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(8.dp)
    ) {
        CoverArt(
            coverArtId = album.coverArt,
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
                .clip(ZonikShapes.coverArtShape),
            size = 300
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvTrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(ZonikShapes.cardShape)
            .background(TvCardBackground, ZonikShapes.cardShape)
            .tvFocusHighlight(ZonikShapes.cardShape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(8.dp)
    ) {
        CoverArt(
            coverArtId = track.coverArt,
            contentDescription = track.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
                .clip(ZonikShapes.coverArtShape),
            size = 300
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvAlbumGridCard(
    album: Album,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(ZonikShapes.cardShape)
            .background(TvCardBackground, ZonikShapes.cardShape)
            .tvFocusHighlight(ZonikShapes.cardShape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(8.dp)
    ) {
        CoverArt(
            coverArtId = album.coverArt,
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(ZonikShapes.coverArtShape),
            size = 300
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (album.year != null) {
            Text(
                text = album.year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun TvTrackGridCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(ZonikShapes.cardShape)
            .background(TvCardBackground, ZonikShapes.cardShape)
            .tvFocusHighlight(ZonikShapes.cardShape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(8.dp)
    ) {
        CoverArt(
            coverArtId = track.coverArt,
            contentDescription = track.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(ZonikShapes.coverArtShape),
            size = 300
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (track.duration > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatDuration(track.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}
