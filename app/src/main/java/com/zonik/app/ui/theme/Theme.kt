package com.zonik.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ZonikDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00BFA5),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Color(0xFFA7F3EC),
    secondary = Color(0xFF4DD0E1),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFFB2EBF2),
    tertiary = Color(0xFF80CBC4),
    onTertiary = Color(0xFF003733),
    background = Color(0xFF0B0E11),
    onBackground = Color(0xFFE2E3E7),
    surface = Color(0xFF111518),
    onSurface = Color(0xFFE2E3E7),
    surfaceVariant = Color(0xFF1A1F24),
    onSurfaceVariant = Color(0xFF9EA3A8),
    surfaceContainerHigh = Color(0xFF171C20),
    surfaceContainer = Color(0xFF131719),
    outline = Color(0xFF3A4045),
    outlineVariant = Color(0xFF2A2F34),
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
