package com.zonik.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ZonikDarkColorScheme = darkColorScheme(
    primary = Color(0xFFEF9F27),
    onPrimary = Color(0xFF3A2500),
    primaryContainer = Color(0xFF5A3D00),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFFD4B976),
    onSecondary = Color(0xFF3A2E00),
    secondaryContainer = Color(0xFF524418),
    onSecondaryContainer = Color(0xFFF1E0A8),
    tertiary = Color(0xFFBA7517),
    onTertiary = Color(0xFF2E1B00),
    background = Color(0xFF090700),
    onBackground = Color(0xFFE8E1D4),
    surface = Color(0xFF100D02),
    onSurface = Color(0xFFE8E1D4),
    surfaceVariant = Color(0xFF1C1808),
    onSurfaceVariant = Color(0xFFA89E8A),
    surfaceContainerHigh = Color(0xFF181406),
    surfaceContainer = Color(0xFF131004),
    outline = Color(0xFF4A3C10),
    outlineVariant = Color(0xFF32280A),
    error = Color(0xFFEF5350),
    onError = Color(0xFF1A0000),
    errorContainer = Color(0xFF3B1010),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun ZonikTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> ZonikDarkColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicLightColorScheme(LocalContext.current)
        }
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
