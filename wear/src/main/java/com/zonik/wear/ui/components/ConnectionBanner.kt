package com.zonik.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.zonik.wear.media.ConnectionState

@Composable
fun ConnectionBanner(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    if (state == ConnectionState.Connected) return

    val (text, bgColor) = when (state) {
        ConnectionState.Connecting -> "Connecting..." to Color(0xFF3D3670)
        ConnectionState.Disconnected -> "Phone disconnected" to Color(0xFF3B1010)
        else -> return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}
