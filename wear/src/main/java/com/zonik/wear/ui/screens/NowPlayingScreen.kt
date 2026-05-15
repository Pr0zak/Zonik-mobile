package com.zonik.wear.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.zonik.wear.WearApp
import com.zonik.wear.media.ConnectionState
import com.zonik.wear.media.WearMediaManager
import com.zonik.wear.ui.components.ConnectionBanner
import com.zonik.wear.ui.components.WearCoverArt

@Composable
fun NowPlayingScreen(
    mediaManager: WearMediaManager,
    onBrowseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as WearApp
    val hasInternet by app.networkMonitor.hasInternet.collectAsStateWithLifecycle()
    val connectionState by mediaManager.connectionState.collectAsState()
    val currentItem by mediaManager.currentMediaItem.collectAsState()
    val isPlaying by mediaManager.isPlaying.collectAsState()
    val isBuffering by mediaManager.isBuffering.collectAsState()
    val position by mediaManager.position.collectAsState()
    val duration by mediaManager.duration.collectAsState()

    val title = currentItem?.mediaMetadata?.title?.toString() ?: ""
    val artist = currentItem?.mediaMetadata?.artist?.toString() ?: ""
    val hasTrack = currentItem != null

    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        mediaManager.setPollingInterval(200L)
        onDispose { mediaManager.stopPolling() }
    }

    ScreenScaffold(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp),
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    if (hasTrack && duration > 0) {
                        val seekDelta = if (event.verticalScrollPixels > 0) 5000L else -5000L
                        val newPos = (position + seekDelta).coerceIn(0L, duration)
                        mediaManager.seekTo(newPos)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
        ) {
            if (hasTrack && duration > 0) {
                // Progress ring tracking position around the watch edge.
                CircularProgressIndicator(
                    progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    startAngle = 270f,
                    endAngle = 270f,
                    colors = androidx.wear.compose.material3.ProgressIndicatorDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    strokeWidth = 4.dp,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ConnectionBanner(state = connectionState)

                if (!hasInternet) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Connect to Wi-Fi or LTE to stream",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (!hasTrack && connectionState == ConnectionState.Connected) {
                    EmptyState(
                        onBrowseClick = onBrowseClick,
                        onSettingsClick = onSettingsClick,
                        onQuickMixClick = {
                            scope.launch {
                                try {
                                    val items = withContext(Dispatchers.IO) {
                                        app.library.getRandomSongs(size = 50)
                                            .mapNotNull { app.library.buildPlayableMediaItem(it) }
                                    }
                                    if (items.isNotEmpty()) mediaManager.playMediaItems(items, 0)
                                } catch (e: Exception) {
                                    android.util.Log.w("NowPlayingScreen", "Quick Mix failed: ${e.message}", e)
                                }
                            }
                        },
                    )
                } else if (hasTrack) {
                    WearCoverArt(mediaItem = currentItem, size = 72.dp)

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    TransportRow(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onPlayPause = mediaManager::togglePlayPause,
                        onPrevious = mediaManager::skipPrevious,
                        onNext = mediaManager::skipNext,
                    )

                    Spacer(Modifier.height(6.dp))

                    SecondaryRow(
                        onBrowseClick = onBrowseClick,
                        onQueueClick = onQueueClick,
                        onSettingsClick = onSettingsClick,
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        focusRequester.requestFocus()
        onDispose {}
    }
}

@Composable
private fun EmptyState(
    onBrowseClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onQuickMixClick: () -> Unit,
) {
    Text(
        text = "Zonik",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = onQuickMixClick,
        colors = ButtonDefaults.buttonColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text("Quick Mix")
    }
    Spacer(Modifier.height(4.dp))
    Button(
        onClick = onBrowseClick,
        colors = ButtonDefaults.filledTonalButtonColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text("Browse")
    }
    Spacer(Modifier.height(4.dp))
    Button(
        onClick = onSettingsClick,
        colors = ButtonDefaults.filledTonalButtonColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text("Settings")
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(20.dp))
        }
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            if (isBuffering) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SecondaryRow(
    onBrowseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBrowseClick, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Outlined.LibraryMusic, contentDescription = "Browse", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onQueueClick, modifier = Modifier.size(30.dp)) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onSettingsClick, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
        }
    }
}
