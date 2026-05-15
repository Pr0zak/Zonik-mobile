package com.zonik.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

// Wear Material 3 ColorScheme — keep the phone app's violet/AMOLED-black
// identity. Material 3 has a much larger surface tonal palette than M2 did;
// most app-level surfaces map to surfaceContainerLow / Medium / High.
private val zonikWearColorScheme = ColorScheme(
    primary = Color(0xFFAFA9EC),
    primaryDim = Color(0xFF8F89D0),
    primaryContainer = Color(0xFF3D3670),
    onPrimary = Color(0xFF1A1540),
    onPrimaryContainer = Color(0xFFE4DFFF),

    secondary = Color(0xFF9B93D8),
    secondaryDim = Color(0xFF7B74B5),
    secondaryContainer = Color(0xFF2C2856),
    onSecondary = Color(0xFF1A1540),
    onSecondaryContainer = Color(0xFFE4DFFF),

    tertiary = Color(0xFF66BDF7),
    tertiaryDim = Color(0xFF4A99CC),
    tertiaryContainer = Color(0xFF003258),
    onTertiary = Color(0xFF001E2F),
    onTertiaryContainer = Color(0xFFCFE5FF),

    surfaceContainerLow = Color(0xFF0D0B18),
    surfaceContainer = Color(0xFF14111F),
    surfaceContainerHigh = Color(0xFF1C1836),
    onSurface = Color(0xFFE4E1F0),
    onSurfaceVariant = Color(0xFFA09CB8),
    outline = Color(0xFF3D3670),
    outlineVariant = Color(0xFF26213F),

    background = Color.Black,
    onBackground = Color(0xFFE4E1F0),

    error = Color(0xFFEF5350),
    errorContainer = Color(0xFF4B0F0F),
    onError = Color.Black,
    onErrorContainer = Color(0xFFFFD9D6),
)

@Composable
fun ZonikWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = zonikWearColorScheme,
        content = content,
    )
}
