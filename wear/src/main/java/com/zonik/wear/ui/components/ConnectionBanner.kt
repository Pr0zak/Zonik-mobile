package com.zonik.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.zonik.wear.media.ConnectionState

/**
 * Renders a small banner above the screen when the controller-bind isn't
 * Connected. Disconnected = local MediaService unreachable (rare on a
 * standalone wear app, but possible during cold start).
 */
@Composable
fun ConnectionBanner(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    if (state == ConnectionState.Connected) return

    val (text, bgColor) = when (state) {
        ConnectionState.Connecting -> "Connecting…" to MaterialTheme.colorScheme.secondaryContainer
        ConnectionState.Disconnected -> "Player offline" to MaterialTheme.colorScheme.errorContainer
        else -> return
    }
    val fgColor = when (state) {
        ConnectionState.Connecting -> MaterialTheme.colorScheme.onSecondaryContainer
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fgColor,
            textAlign = TextAlign.Center,
        )
    }
}
