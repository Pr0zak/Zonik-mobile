package com.zonik.wear.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.zonik.wear.media.ConnectionState
import com.zonik.wear.media.WearMediaManager
import com.zonik.wear.ui.components.ConnectionBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BrowseScreen(
    mediaManager: WearMediaManager,
    onNodeClick: (String) -> Unit,
) {
    val connectionState by mediaManager.connectionState.collectAsState()
    var rootChildren by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.Connected) {
            isLoading = true
            rootChildren = withContext(Dispatchers.IO) { mediaManager.getChildren("root") }
            isLoading = false
        }
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
                ConnectionBanner(state = connectionState)
            }

            item {
                ListHeader { Text("Browse", color = MaterialTheme.colorScheme.primary) }
            }

            if (isLoading && connectionState == ConnectionState.Connected) {
                item {
                    Text(
                        "Loading…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(rootChildren, key = { it.mediaId }) { item ->
                    val meta = item.mediaMetadata
                    val icon = iconForBrowseNode(item.mediaId)
                    FilledTonalButton(
                        onClick = {
                            if (meta.isBrowsable == true) onNodeClick(item.mediaId)
                            else mediaManager.playFromMediaId(item.mediaId)
                        },
                        icon = icon?.let { iv ->
                            { Icon(imageVector = iv, contentDescription = null, modifier = Modifier.size(20.dp)) }
                        },
                        label = {
                            Text(
                                text = meta.title?.toString() ?: item.mediaId,
                                maxLines = 1,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

private fun iconForBrowseNode(mediaId: String): ImageVector? = when (mediaId) {
    "mix" -> Icons.Default.Shuffle
    "recent" -> Icons.Default.NewReleases
    "library" -> Icons.Default.Album
    "playlists" -> Icons.AutoMirrored.Filled.FeaturedPlayList
    else -> null
}
