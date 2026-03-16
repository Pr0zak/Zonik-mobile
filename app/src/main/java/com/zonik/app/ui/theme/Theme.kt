package com.zonik.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ZonikDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2D1F5E),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFF03DAC6),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF004D47),
    onSecondaryContainer = Color(0xFFB2FFF5),
    background = Color(0xFF0A0A0F),
    onBackground = Color(0xFFE8E8EC),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE8E8EC),
    surfaceVariant = Color(0xFF1E1E28),
    onSurfaceVariant = Color(0xFF9898A0),
    surfaceContainerHigh = Color(0xFF1A1A24),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
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
