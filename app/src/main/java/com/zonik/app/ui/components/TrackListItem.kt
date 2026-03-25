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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import com.zonik.app.ui.theme.ZonikColors
import com.zonik.app.ui.theme.ZonikShapes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zonik.app.model.Track
import com.zonik.app.ui.util.formatDuration
import com.zonik.app.ui.util.formatFileSize

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrackListItem(
    track: Track,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onToggleMarkForDeletion: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    currentlyPlayingId: String? = null,
    showAlbum: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isCurrentlyPlaying = track.id == currentlyPlayingId
    var showMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.then(
        if (isCurrentlyPlaying) Modifier.drawBehind {
            drawRect(
                color = primaryColor,
                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
            )
        } else Modifier
    )) {
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
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isCurrentlyPlaying) {
                            Modifier.border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(10.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        track.suffix?.let { suffix ->
                            FormatBadge(suffix = suffix)
                        }
                        if (track.offlineCached) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.CloudDone,
                                contentDescription = "Offline",
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF4CAF50)
                            )
                        }
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
            if (onStartRadio != null) {
                DropdownMenuItem(
                    text = { Text("Start Radio") },
                    onClick = {
                        showMenu = false
                        onStartRadio()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Sensors, contentDescription = null)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Track Details") },
                onClick = {
                    showMenu = false
                    showDetails = true
                },
                leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = null)
                }
            )
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

        if (showDetails) {
            TrackDetailsSheet(track = track, onDismiss = { showDetails = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailsSheet(track: Track, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header with album art
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                CoverArt(
                    coverArtId = track.coverArt,
                    contentDescription = track.album,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    size = 300
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (track.artist.isNotBlank()) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (track.album.isNotBlank()) {
                        Text(
                            text = track.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Details grid
            track.track?.let { DetailRow("Track #", it.toString()) }
            track.year?.let { DetailRow("Year", it.toString()) }
            track.genre?.let { if (it.isNotBlank()) DetailRow("Genre", it) }
            if (track.duration > 0) DetailRow("Duration", formatDuration(track.duration))
            track.suffix?.let { DetailRow("Format", it.uppercase()) }
            track.bitRate?.let { DetailRow("Bitrate", "${it} kbps") }
            track.size?.let { size ->
                val formatted = formatFileSize(size)
                if (formatted.isNotBlank()) DetailRow("File Size", formatted)
            }
            track.contentType?.let { DetailRow("Content Type", it) }
            track.path?.let { path ->
                if (path.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Path",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FormatBadge(suffix: String) {
    val backgroundColor = when (suffix.lowercase()) {
        "flac", "alac" -> ZonikColors.gold.copy(alpha = 0.15f)
        "mp3", "aac", "ogg", "opus" -> Color(0xFF9E9E9E).copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (suffix.lowercase()) {
        "flac", "alac" -> ZonikColors.gold
        "mp3", "aac", "ogg", "opus" -> Color(0xFF9E9E9E)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = backgroundColor,
        shape = ZonikShapes.badgeShape
    ) {
        Text(
            text = suffix.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
