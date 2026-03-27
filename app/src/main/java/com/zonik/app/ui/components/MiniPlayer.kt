package com.zonik.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.zonik.app.media.PlaybackManager
import com.zonik.app.model.Track
import com.zonik.app.ui.theme.ZonikColors
import com.zonik.app.ui.theme.ZonikShapes
import com.zonik.app.ui.util.tvFocusHighlight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val playbackManager: PlaybackManager
) : ViewModel() {

    val currentTrack: StateFlow<Track?> = playbackManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying

    fun getCurrentPosition(): Long = playbackManager.getCurrentPosition()
    fun getDuration(): Long = playbackManager.getDuration()

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun skipNext() {
        playbackManager.skipNext()
    }

    fun skipPrevious() {
        playbackManager.skipPrevious()
    }
}

@Composable
fun MiniPlayer(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = hiltViewModel()
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val track = currentTrack ?: return

    // Poll playback position while playing
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    LaunchedEffect(isPlaying, track) {
        while (isActive) {
            position = viewModel.getCurrentPosition()
            duration = viewModel.getDuration()
            delay(200)
        }
    }

    val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusHighlight(shape = ZonikShapes.miniPlayerShape)
            .clickable(onClick = onClick),
        color = ZonikColors.glassBg,
        shape = ZonikShapes.miniPlayerShape,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(
                    coverArtId = track.coverArt,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    size = 150
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play/Pause in a filled circle
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp),
                    onClick = { viewModel.togglePlayPause() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Progress bar at bottom
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
