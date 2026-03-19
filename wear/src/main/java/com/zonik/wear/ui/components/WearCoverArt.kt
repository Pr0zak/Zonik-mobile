package com.zonik.wear.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import coil.compose.SubcomposeAsyncImage

@Composable
fun WearCoverArt(
    mediaItem: MediaItem?,
    size: Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    val artworkUri = mediaItem?.mediaMetadata?.artworkUri

    if (artworkUri != null) {
        SubcomposeAsyncImage(
            model = artworkUri,
            contentDescription = "Album art",
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            loading = { CoverArtPlaceholder(size) },
            error = { CoverArtPlaceholder(size) }
        )
    } else {
        CoverArtPlaceholder(size, modifier)
    }
}

@Composable
private fun CoverArtPlaceholder(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF1C1836)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(size / 2)
        )
    }
}
