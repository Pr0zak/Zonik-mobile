package com.zonik.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zonik.app.model.Track
import com.zonik.app.ui.util.formatDuration

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: Track,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onToggleMarkForDeletion: (() -> Unit)? = null,
    currentlyPlayingId: String? = null,
    showAlbum: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isCurrentlyPlaying = track.id == currentlyPlayingId
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ListItem(
            headlineContent = {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        track.markedForDeletion -> MaterialTheme.colorScheme.error
                        isCurrentlyPlaying -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            },
            supportingContent = {
                val text = if (showAlbum && track.album.isNotEmpty()) {
                    "${track.artist} \u00B7 ${track.album}"
                } else {
                    track.artist
                }
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                val artModifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (isCurrentlyPlaying) {
                            Modifier.border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                CoverArt(
                    coverArtId = track.coverArt,
                    contentDescription = track.album,
                    modifier = artModifier,
                    size = 80
                )
            },
            trailingContent = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (track.duration > 0) {
                        Text(
                            text = formatDuration(track.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    track.suffix?.let { suffix ->
                        Spacer(modifier = Modifier.height(2.dp))
                        FormatBadge(suffix = suffix)
                    }
                }
            },
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Play") },
                onClick = {
                    showMenu = false
                    onClick()
                }
            )
            if (onPlayNext != null) {
                DropdownMenuItem(
                    text = { Text("Play Next") },
                    onClick = {
                        showMenu = false
                        onPlayNext()
                    }
                )
            }
            if (onAddToQueue != null) {
                DropdownMenuItem(
                    text = { Text("Add to Queue") },
                    onClick = {
                        showMenu = false
                        onAddToQueue()
                    }
                )
            }
            if (onGoToAlbum != null) {
                DropdownMenuItem(
                    text = { Text("Go to Album") },
                    onClick = {
                        showMenu = false
                        onGoToAlbum()
                    }
                )
            }
            if (onGoToArtist != null) {
                DropdownMenuItem(
                    text = { Text("Go to Artist") },
                    onClick = {
                        showMenu = false
                        onGoToArtist()
                    }
                )
            }
            if (onToggleMarkForDeletion != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (track.markedForDeletion) "Unmark for Deletion" else "Mark for Deletion",
                            color = if (!track.markedForDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleMarkForDeletion()
                    },
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
}

@Composable
private fun FormatBadge(suffix: String) {
    val backgroundColor = when (suffix.lowercase()) {
        "flac", "alac" -> Color(0xFF2E7D32).copy(alpha = 0.15f)
        "mp3", "aac", "ogg", "opus" -> Color(0xFF1565C0).copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (suffix.lowercase()) {
        "flac", "alac" -> Color(0xFF2E7D32)
        "mp3", "aac", "ogg", "opus" -> Color(0xFF1565C0)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = suffix.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
