package com.zonik.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun WithAlbumScheme(
    coverArtId: String?,
    content: @Composable () -> Unit,
) {
    val palette = rememberAlbumPalette(coverArtId)
    val scheme = if (palette != null) buildSchemeFromPalette(palette) else NEUTRAL_SCHEME
    val effectivePalette = palette ?: NEUTRAL_PALETTE
    CompositionLocalProvider(LocalAlbumPalette provides effectivePalette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ZonikTypography,
            content = content,
        )
    }
}

@Composable
fun WithNeutralScheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAlbumPalette provides NEUTRAL_PALETTE) {
        MaterialTheme(
            colorScheme = NEUTRAL_SCHEME,
            typography = ZonikTypography,
            content = content,
        )
    }
}
