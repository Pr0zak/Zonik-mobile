package com.zonik.wear.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.zonik.wear.media.ConnectionState
import com.zonik.wear.media.WearMediaManager
import com.zonik.wear.ui.components.ConnectionBanner
import com.zonik.wear.ui.components.WearCoverArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BrowseChildrenScreen(
    mediaManager: WearMediaManager,
    parentId: String,
    onNodeClick: (String) -> Unit
) {
    val connectionState by mediaManager.connectionState.collectAsState()
    var children by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var parentTitle by remember { mutableStateOf("") }

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(parentId, connectionState) {
        if (connectionState == ConnectionState.Connected) {
            isLoading = true
            children = withContext(Dispatchers.IO) {
                mediaManager.getChildren(parentId)
            }
            // Derive parent title from parentId
            parentTitle = parentId
                .removePrefix("artist:")
                .removePrefix("album:")
                .removePrefix("genre:")
                .removePrefix("playlist:")
                .replaceFirstChar { it.uppercase() }
            isLoading = false
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
            ConnectionBanner(state = connectionState)
        }

        item {
            ListHeader {
                Text(
                    text = parentTitle,
                    color = MaterialTheme.colors.primary,
                    maxLines = 1
                )
            }
        }

        if (isLoading) {
            item {
                Text(
                    "Loading...",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        } else if (children.isEmpty()) {
            item {
                Text(
                    "No items",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            items(children, key = { it.mediaId }) { item ->
                val meta = item.mediaMetadata
                val isBrowsable = meta.isBrowsable == true
                val hasArt = meta.artworkUri != null

                Chip(
                    onClick = {
                        if (isBrowsable) {
                            onNodeClick(item.mediaId)
                        } else {
                            // Play this item via browse-and-play
                            mediaManager.playFromMediaId(item.mediaId)
                        }
                    },
                    label = {
                        Text(
                            text = meta.title?.toString() ?: item.mediaId,
                            maxLines = 1
                        )
                    },
                    secondaryLabel = (meta.subtitle ?: meta.artist)?.toString()?.let { sub ->
                        { Text(text = sub, maxLines = 1) }
                    },
                    icon = if (hasArt) {
                        {
                            WearCoverArt(
                                mediaItem = item,
                                size = 32.dp
                            )
                        }
                    } else if (!isBrowsable) {
                        {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else null,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
