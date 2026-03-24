package com.zonik.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun WaveformBars(
    waveform: FloatArray,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = waveform.size
    val gapDp = 1.dp
    val cornerDp = 1.dp
    val minBarFraction = 0.05f

    Canvas(modifier = modifier) {
        if (barCount == 0) return@Canvas
        val gapPx = gapDp.toPx()
        val cornerPx = cornerDp.toPx()
        val totalGaps = (barCount - 1) * gapPx
        val barWidth = (size.width - totalGaps) / barCount
        val halfHeight = size.height / 2f

        for (i in 0 until barCount) {
            val magnitude = waveform[i].coerceAtLeast(minBarFraction)
            val barHeight = magnitude * size.height
            val x = i * (barWidth + gapPx)
            val y = halfHeight - barHeight / 2f

            val barProgress = (i + 0.5f) / barCount
            val color = if (barProgress <= progress) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(cornerPx, cornerPx)
            )
        }
    }
}
