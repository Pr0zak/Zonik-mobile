package com.zonik.app.ui.tv

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class ParticleShape { ORB, RING, SPARKLE }

private class Particle(
    var x: Float, var y: Float,
    var dx: Float, var dy: Float,
    var baseRadius: Float, var radius: Float,
    var color: Color, var shape: ParticleShape,
    var alpha: Float,
    val trail: MutableList<Offset> = mutableListOf(),
    var pulseDecay: Float = 0f
)

// Glow ring expanding from center on bass hit
private class GlowRing(
    var radius: Float = 0f,
    var alpha: Float = 0.6f,
    var color: Color = Color.White
)

private fun createParticles(colors: List<Color>): List<Particle> {
    val shapes = ParticleShape.entries
    return List(30) { i ->
        val shape = shapes[i % 3]
        Particle(
            x = Math.random().toFloat(), y = Math.random().toFloat(),
            dx = (Math.random().toFloat() - 0.5f) * 0.0008f,
            dy = (Math.random().toFloat() - 0.5f) * 0.0008f,
            baseRadius = when (shape) {
                ParticleShape.ORB -> 12f + Math.random().toFloat() * 20f
                ParticleShape.RING -> 8f + Math.random().toFloat() * 16f
                ParticleShape.SPARKLE -> 3f + Math.random().toFloat() * 5f
            },
            radius = 0f, color = colors[i % colors.size], shape = shape,
            alpha = when (shape) {
                ParticleShape.ORB -> 0.08f + Math.random().toFloat() * 0.07f
                ParticleShape.RING -> 0.1f + Math.random().toFloat() * 0.1f
                ParticleShape.SPARKLE -> 0.15f + Math.random().toFloat() * 0.15f
            }
        ).also { it.radius = it.baseRadius }
    }
}

@Composable
fun ParticleSystem(
    bassLevel: Float,
    fftMagnitudes: FloatArray = FloatArray(32),
    colors: List<Color>,
    modifier: Modifier = Modifier,
    centerX: Float = 0.5f,
    centerY: Float = 0.4f
) {
    val particles = remember(colors.hashCode()) { createParticles(colors) }
    val glowRings = remember { MutableList(5) { GlowRing() } }
    var frameCounter by remember { mutableLongStateOf(0L) }
    var lastFrameTime by remember { mutableLongStateOf(System.nanoTime()) }
    var auroraPhase by remember { mutableLongStateOf(0L) }
    var lastBassHit by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            frameCounter++
            auroraPhase++
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

        @Suppress("UNUSED_EXPRESSION")
        frameCounter

        // ═══════════════════════════════════════════
        // 1. Aurora / flowing colors (background)
        // ═══════════════════════════════════════════
        val phase = auroraPhase * 0.01f
        for (band in 0 until 5) {
            val bandColor = colors[band % colors.size]
            val xOffset = sin(phase * 0.3f + band * 1.2f).toFloat() * w * 0.15f
            val bandAlpha = 0.04f + bassLevel * 0.03f
            drawRect(
                color = bandColor.copy(alpha = bandAlpha),
                topLeft = Offset(w * band / 5f + xOffset, 0f),
                size = androidx.compose.ui.geometry.Size(w / 4f, h)
            )
        }

        // ═══════════════════════════════════════════
        // 2. Pulsing glow rings from center
        // ═══════════════════════════════════════════
        if (bassLevel > 0.4f && now - lastBassHit > 200_000_000L) {
            lastBassHit = now
            val ring = glowRings.minByOrNull { it.alpha } ?: glowRings[0]
            ring.radius = 280f // start just inside album art edge, expand outward
            ring.alpha = 0.5f + bassLevel * 0.3f
            ring.color = colors[(frameCounter % colors.size).toInt()]
        }
        glowRings.forEach { ring ->
            if (ring.alpha > 0.01f) {
                ring.radius += dt * 300f
                ring.alpha *= 0.96f
                drawCircle(
                    color = ring.color.copy(alpha = ring.alpha),
                    radius = ring.radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f)
                )
            }
        }

        // ═══════════════════════════════════════════
        // 3. Frequency spectrum circle around center
        // ═══════════════════════════════════════════
        val spectrumBins = fftMagnitudes.size.coerceAtMost(32)
        if (spectrumBins > 0) {
            val baseSpectrumRadius = 320f // outside album art (300dp = ~300px radius)
            for (i in 0 until spectrumBins) {
                val angle = (i.toFloat() / spectrumBins) * 2f * PI.toFloat()
                val magnitude = fftMagnitudes.getOrElse(i) { 0f }
                val barLen = magnitude * 80f + 5f
                val innerR = baseSpectrumRadius
                val outerR = baseSpectrumRadius + barLen

                val x1 = cx + cos(angle) * innerR
                val y1 = cy + sin(angle) * innerR
                val x2 = cx + cos(angle) * outerR
                val y2 = cy + sin(angle) * outerR

                val barColor = colors[i % colors.size]
                drawLine(
                    color = barColor.copy(alpha = 0.3f + magnitude * 0.4f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 4f
                )
            }
        }

        // ═══════════════════════════════════════════
        // 4. Particles with trails
        // ═══════════════════════════════════════════
        particles.forEach { p ->
            if (p.trail.size >= 8) p.trail.removeAt(0)
            p.trail.add(Offset(p.x * w, p.y * h))

            // Beat pulse
            if (bassLevel > 0.3f) p.pulseDecay = bassLevel * 1.5f
            if (p.pulseDecay > 0f) {
                p.radius = p.baseRadius * (1f + p.pulseDecay)
                p.pulseDecay *= 0.92f
                if (p.pulseDecay < 0.01f) p.pulseDecay = 0f
            } else {
                p.radius = p.baseRadius
            }

            // Move with bass speed boost
            val speed = 1f + bassLevel * 3f
            p.x += p.dx * dt * 60f * speed
            p.y += p.dy * dt * 60f * speed

            // Wrap
            if (p.x < -0.05f) p.x += 1.1f
            if (p.x > 1.05f) p.x -= 1.1f
            if (p.y < -0.05f) p.y += 1.1f
            if (p.y > 1.05f) p.y -= 1.1f

            // Trails
            p.trail.forEachIndexed { idx, pos ->
                val ta = p.alpha * 0.3f * (idx.toFloat() / p.trail.size)
                val tr = p.radius * 0.5f * (idx.toFloat() / p.trail.size)
                if (tr > 0.5f) drawCircle(p.color.copy(alpha = ta), tr, pos)
            }

            // Particle
            val pos = Offset(p.x * w, p.y * h)
            val beatAlpha = p.alpha + p.pulseDecay * 0.3f
            drawParticle(p, pos, beatAlpha)
        }
    }
}

private fun DrawScope.drawParticle(p: Particle, pos: Offset, alpha: Float) {
    when (p.shape) {
        ParticleShape.ORB -> {
            drawCircle(p.color.copy(alpha = alpha), p.radius, pos)
            drawCircle(p.color.copy(alpha = alpha * 0.3f), p.radius * 1.8f, pos) // glow
        }
        ParticleShape.RING -> {
            drawCircle(p.color.copy(alpha = alpha), p.radius, pos, style = Stroke(2f))
        }
        ParticleShape.SPARKLE -> {
            drawCircle(p.color.copy(alpha = (alpha * 2f).coerceAtMost(1f)), p.radius, pos)
            val rayLen = p.radius * 3f
            for (a in 0 until 4) {
                val rad = a * (PI / 2f).toFloat()
                drawLine(
                    p.color.copy(alpha = alpha * 0.6f), pos,
                    Offset(pos.x + cos(rad) * rayLen, pos.y + sin(rad) * rayLen), 1f
                )
            }
        }
    }
}
