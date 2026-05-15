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
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.zonik.wear.media.WearMediaManager
import kotlinx.coroutines.launch

@Composable
fun QueueScreen(mediaManager: WearMediaManager) {
    val queue by mediaManager.queue.collectAsState()
    val currentIndex by mediaManager.currentIndex.collectAsState()

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.scrollToItem(currentIndex + 1)
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    scope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            contentPadding = contentPadding,
        ) {
            item {
                ListHeader {
                    Text("Queue (${queue.size})", color = MaterialTheme.colorScheme.primary)
                }
            }

            if (queue.isEmpty()) {
                item {
                    Text(
                        "Queue is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                itemsIndexed(queue, key = { index, item -> "${index}_${item.mediaId}" }) { index, item ->
                    val isCurrent = index == currentIndex
                    val meta = item.mediaMetadata
                    val title = meta.title?.toString() ?: "Track ${index + 1}"
                    val artistText = meta.artist?.toString()

                    if (isCurrent) {
                        Button(
                            onClick = { mediaManager.skipToIndex(index) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            label = { Text(title, maxLines = 1) },
                            secondaryLabel = artistText?.let { { Text(it, maxLines = 1) } },
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { mediaManager.skipToIndex(index) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            label = { Text(title, maxLines = 1) },
                            secondaryLabel = artistText?.let { { Text(it, maxLines = 1) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
