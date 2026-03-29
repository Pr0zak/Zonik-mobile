package com.zonik.app.ui.tv

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ParticleShape { ORB, RING, SPARKLE }

private class Particle(
    var x: Float,
    var y: Float,
    var dx: Float,
    var dy: Float,
    var baseRadius: Float,
    var radius: Float,
    var color: Color,
    var shape: ParticleShape,
    var alpha: Float,
    val trail: MutableList<Offset> = mutableListOf(),
    var pulseDecay: Float = 0f
)

private fun createParticles(colors: List<Color>): List<Particle> {
    val particles = mutableListOf<Particle>()
    val shapes = ParticleShape.entries

    repeat(30) { i ->
        val shape = shapes[i % 3]
        val baseRadius = when (shape) {
            ParticleShape.ORB -> 12f + Math.random().toFloat() * 20f
            ParticleShape.RING -> 8f + Math.random().toFloat() * 16f
            ParticleShape.SPARKLE -> 3f + Math.random().toFloat() * 5f
        }
        val alpha = when (shape) {
            ParticleShape.ORB -> 0.08f + Math.random().toFloat() * 0.07f
            ParticleShape.RING -> 0.1f + Math.random().toFloat() * 0.1f
            ParticleShape.SPARKLE -> 0.15f + Math.random().toFloat() * 0.15f
        }
        particles.add(Particle(
            x = Math.random().toFloat(),
            y = Math.random().toFloat(),
            dx = (Math.random().toFloat() - 0.5f) * 0.0008f,
            dy = (Math.random().toFloat() - 0.5f) * 0.0008f,
            baseRadius = baseRadius,
            radius = baseRadius,
            color = colors[i % colors.size],
            shape = shape,
            alpha = alpha
        ))
    }
    return particles
}

@Composable
fun ParticleSystem(
    bassLevel: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    centerX: Float = 0.5f,
    centerY: Float = 0.4f
) {
    val particles = remember(colors.hashCode()) { createParticles(colors) }
    var lastFrameTime by remember { mutableLongStateOf(System.nanoTime()) }
    var frameCounter by remember { mutableLongStateOf(0L) }

    // Animation loop ~30fps
    LaunchedEffect(Unit) {
        while (true) {
            frameCounter++
            delay(33L)
        }
    }

    Canvas(modifier = modifier) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastFrameTime = now

        val w = size.width
        val h = size.height
        val cx = centerX * w
        val cy = centerY * h

        // Use frameCounter to force recomposition
        @Suppress("UNUSED_EXPRESSION")
        frameCounter

        particles.forEach { p ->
            // Store trail position
            if (p.trail.size >= 8) p.trail.removeAt(0)
            p.trail.add(Offset(p.x * w, p.y * h))

            // Gravity toward center
            val toX = centerX - p.x
            val toY = centerY - p.y
            val dist = sqrt(toX * toX + toY * toY).coerceAtLeast(0.05f)
            val gravityStrength = 0.00002f / (dist * dist).coerceAtLeast(0.01f)
            val angle = atan2(toY, toX)
            p.dx += cos(angle) * gravityStrength * dt * 60f
            p.dy += sin(angle) * gravityStrength * dt * 60f

            // Damping
            p.dx *= 0.998f
            p.dy *= 0.998f

            // Beat pulse: expand on bass
            if (bassLevel > 0.3f) {
                p.pulseDecay = bassLevel * 1.5f
            }
            if (p.pulseDecay > 0f) {
                p.radius = p.baseRadius * (1f + p.pulseDecay)
                p.pulseDecay *= 0.92f
                if (p.pulseDecay < 0.01f) p.pulseDecay = 0f
            } else {
                p.radius = p.baseRadius
            }

            // Move
            p.x += p.dx * dt * 60f
            p.y += p.dy * dt * 60f

            // Wrap
            if (p.x < -0.05f) p.x += 1.1f
            if (p.x > 1.05f) p.x -= 1.1f
            if (p.y < -0.05f) p.y += 1.1f
            if (p.y > 1.05f) p.y -= 1.1f

            // Draw trails
            p.trail.forEachIndexed { idx, pos ->
                val trailAlpha = p.alpha * 0.3f * (idx.toFloat() / p.trail.size)
                val trailRadius = p.radius * 0.5f * (idx.toFloat() / p.trail.size)
                if (trailRadius > 0.5f) {
                    drawCircle(
                        color = p.color.copy(alpha = trailAlpha),
                        radius = trailRadius,
                        center = pos
                    )
                }
            }

            // Draw particle
            val pos = Offset(p.x * w, p.y * h)
            val beatAlpha = p.alpha + p.pulseDecay * 0.3f
            drawParticle(p, pos, beatAlpha)
        }
    }
}

private fun DrawScope.drawParticle(p: Particle, pos: Offset, alpha: Float) {
    when (p.shape) {
        ParticleShape.ORB -> {
            drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = p.radius,
                center = pos
            )
            // Soft glow
            drawCircle(
                color = p.color.copy(alpha = alpha * 0.3f),
                radius = p.radius * 1.8f,
                center = pos
            )
        }
        ParticleShape.RING -> {
            drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = p.radius,
                center = pos,
                style = Stroke(width = 2f)
            )
        }
        ParticleShape.SPARKLE -> {
            // Bright center dot
            drawCircle(
                color = p.color.copy(alpha = (alpha * 2f).coerceAtMost(1f)),
                radius = p.radius,
                center = pos
            )
            // 4 rays
            val rayLen = p.radius * 3f
            val rayAlpha = alpha * 0.6f
            for (a in 0 until 4) {
                val rad = a * (Math.PI / 2f).toFloat()
                drawLine(
                    color = p.color.copy(alpha = rayAlpha),
                    start = pos,
                    end = Offset(
                        pos.x + cos(rad) * rayLen,
                        pos.y + sin(rad) * rayLen
                    ),
                    strokeWidth = 1f
                )
            }
        }
    }
}
