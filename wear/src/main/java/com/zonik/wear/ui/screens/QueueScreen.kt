package com.zonik.wear.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.zonik.wear.media.WearMediaManager
import kotlinx.coroutines.launch

@Composable
fun QueueScreen(mediaManager: WearMediaManager) {
    val queue by mediaManager.queue.collectAsState()
    val currentIndex by mediaManager.currentIndex.collectAsState()

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to current track
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            // +1 to account for the header item
            listState.scrollToItem(currentIndex + 1)
        }
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                coroutineScope.launch {
                    listState.scrollBy(event.verticalScrollPixels)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        state = listState
    ) {
        item {
            ListHeader {
                Text(
                    text = "Queue (${queue.size})",
                    color = MaterialTheme.colors.primary
                )
            }
        }

        if (queue.isEmpty()) {
            item {
                Text(
                    "Queue is empty",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            itemsIndexed(queue, key = { index, item -> "${index}_${item.mediaId}" }) { index, item ->
                val isCurrent = index == currentIndex
                val meta = item.mediaMetadata

                Chip(
                    onClick = { mediaManager.skipToIndex(index) },
                    label = {
                        Text(
                            text = meta.title?.toString() ?: "Track ${index + 1}",
                            maxLines = 1,
                            color = if (isCurrent) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
                        )
                    },
                    secondaryLabel = meta.artist?.toString()?.let { artist ->
                        {
                            Text(
                                text = artist,
                                maxLines = 1,
                                color = if (isCurrent)
                                    MaterialTheme.colors.primary.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (isCurrent) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = if (isCurrent) {
                        ChipDefaults.gradientBackgroundChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
