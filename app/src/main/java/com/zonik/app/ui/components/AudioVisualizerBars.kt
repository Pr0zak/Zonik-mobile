package com.zonik.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zonik.app.media.AudioVisualizerManager

@Composable
fun AudioVisualizerBars(
    magnitudes: FloatArray,
    accentColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = AudioVisualizerManager.BAR_COUNT
) {
    // Animate each bar independently with spring physics
    val animatedBars = Array(barCount) { index ->
        val target = magnitudes.getOrElse(index) { 0f }
        val animated by animateFloatAsState(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
            label = "bar_$index"
        )
        animated
    }

    val gapDp = 2.dp
    val cornerDp = 2.dp

    Canvas(modifier = modifier) {
        val gapPx = gapDp.toPx()
        val cornerPx = cornerDp.toPx()
        val totalGaps = (barCount - 1) * gapPx
        val barWidth = (size.width - totalGaps) / barCount

        for (i in 0 until barCount) {
            val magnitude = animatedBars[i]
            val barHeight = magnitude * size.height
            if (barHeight < 1f) continue

            val x = i * (barWidth + gapPx)
            val y = size.height - barHeight

            val brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.5f),
                    accentColor.copy(alpha = 0.15f)
                ),
                startY = y,
                endY = size.height
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(cornerPx, cornerPx)
            )
        }
    }
}
