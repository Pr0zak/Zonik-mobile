package com.zonik.wear.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.zonik.wear.media.ConnectionState
import com.zonik.wear.media.WearMediaManager
import com.zonik.wear.ui.components.ConnectionBanner
import com.zonik.wear.ui.components.WearCoverArt

@Composable
fun NowPlayingScreen(
    mediaManager: WearMediaManager,
    onBrowseClick: () -> Unit,
    onQueueClick: () -> Unit
) {
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
    var isStarred by remember { mutableStateOf(false) }

    // Start/stop position polling based on screen visibility
    DisposableEffect(Unit) {
        mediaManager.setPollingInterval(200L)
        onDispose {
            mediaManager.stopPolling()
        }
    }

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
            .focusable()
    ) {
        // Progress ring
        if (hasTrack && duration > 0) {
            CircularProgressIndicator(
                progress = (position.toFloat() / duration).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxSize(),
                startAngle = 270f,
                endAngle = 270f,
                indicatorColor = MaterialTheme.colors.primary,
                trackColor = Color(0xFF1C1836),
                strokeWidth = 4.dp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Connection banner
            ConnectionBanner(state = connectionState)

            if (!hasTrack && connectionState == ConnectionState.Connected) {
                // No track playing — show browse prompt
                Text(
                    text = "Zonik",
                    fontSize = 16.sp,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No track playing",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                CompactButton(
                    onClick = onBrowseClick,
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LibraryMusic,
                        contentDescription = "Browse"
                    )
                }
            } else if (hasTrack) {
                // Cover art
                WearCoverArt(
                    mediaItem = currentItem,
                    size = 64.dp
                )

                Spacer(Modifier.height(4.dp))

                // Title
                Text(
                    text = title,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Artist
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Controls row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip previous
                    CompactButton(
                        onClick = { mediaManager.skipPrevious() },
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Play/Pause (larger)
                    Button(
                        onClick = { mediaManager.togglePlayPause() },
                        modifier = Modifier.size(44.dp),
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Skip next
                    CompactButton(
                        onClick = { mediaManager.skipNext() },
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Bottom row: Star + Queue
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactButton(
                        onClick = {
                            isStarred = !isStarred
                            mediaManager.toggleStar()
                        },
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Icon(
                            imageVector = if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Star",
                            tint = if (isStarred) Color(0xFFEF5350) else MaterialTheme.colors.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    CompactButton(
                        onClick = onBrowseClick,
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LibraryMusic,
                            contentDescription = "Browse",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    CompactButton(
                        onClick = onQueueClick,
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Request focus for rotary input
    DisposableEffect(Unit) {
        focusRequester.requestFocus()
        onDispose {}
    }
}
