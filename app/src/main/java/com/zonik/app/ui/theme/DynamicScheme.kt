package com.zonik.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class AlbumPalette(
    val dark: Color,
    val vibrant: Color,
    val deep: Color,
)

private fun lighten(c: Color, amt: Float): Color {
    val r = c.red + (1f - c.red) * amt
    val g = c.green + (1f - c.green) * amt
    val b = c.blue + (1f - c.blue) * amt
    return Color(r, g, b, c.alpha)
}

private val Surface = Color(0xFF0F0C1A)
private val Deep = Color(0xFF0A0814)
private val OnSurface = Color(0xFFECE6F0)
private val OnPrimary = Color(0xFF0D0A18)
private val White = Color(0xFFFFFFFF)

private fun container(opacity: Float) = White.copy(alpha = opacity)

fun buildSchemeFromPalette(palette: AlbumPalette): ColorScheme = darkColorScheme(
    primary = palette.vibrant,
    onPrimary = OnPrimary,
    primaryContainer = palette.dark,
    onPrimaryContainer = White,
    secondary = lighten(palette.vibrant, 0.15f),
    onSecondary = OnPrimary,
    secondaryContainer = lighten(palette.dark, 0.10f),
    onSecondaryContainer = White,
    tertiary = palette.vibrant,
    onTertiary = OnPrimary,
    tertiaryContainer = palette.dark,
    onTertiaryContainer = White,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = container(0.05f),
    onSurfaceVariant = OnSurface.copy(alpha = 0.65f),
    surfaceContainerLowest = Deep,
    surfaceContainerLow = container(0.03f),
    surfaceContainer = container(0.05f),
    surfaceContainerHigh = container(0.08f),
    surfaceContainerHighest = container(0.11f),
    outline = OnSurface.copy(alpha = 0.18f),
    outlineVariant = OnSurface.copy(alpha = 0.10f),
)

val NEUTRAL_SCHEME: ColorScheme = run {
    val primary = Color(0xFFB5A2E8)
    val primaryContainer = Color(0xFF372A6E)
    darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF1F1147),
        primaryContainer = primaryContainer,
        onPrimaryContainer = White,
        secondary = lighten(primary, 0.1f),
        onSecondary = Color(0xFF1F1147),
        secondaryContainer = Color(0xFF2A2240),
        onSecondaryContainer = White,
        tertiary = primary,
        onTertiary = Color(0xFF1F1147),
        tertiaryContainer = primaryContainer,
        onTertiaryContainer = White,
        background = Surface,
        onBackground = OnSurface,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = container(0.05f),
        onSurfaceVariant = OnSurface.copy(alpha = 0.65f),
        surfaceContainerLowest = Deep,
        surfaceContainerLow = container(0.03f),
        surfaceContainer = container(0.05f),
        surfaceContainerHigh = container(0.08f),
        surfaceContainerHighest = container(0.11f),
        outline = OnSurface.copy(alpha = 0.18f),
        outlineVariant = OnSurface.copy(alpha = 0.10f),
    )
}

val NEUTRAL_PALETTE = AlbumPalette(
    dark = Color(0xFF372A6E),
    vibrant = Color(0xFFB5A2E8),
    deep = Deep,
)

fun bgRadialGradient(palette: AlbumPalette): Brush =
    Brush.radialGradient(
        colors = listOf(palette.dark.copy(alpha = 0.55f), Color.Transparent),
        radius = 900f,
    )

val LocalAlbumPalette = compositionLocalOf { NEUTRAL_PALETTE }

@Composable
fun rememberSchemeFor(palette: AlbumPalette?): ColorScheme {
    return if (palette != null) buildSchemeFromPalette(palette) else NEUTRAL_SCHEME
}
