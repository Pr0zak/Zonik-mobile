package com.zonik.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Semantic colors beyond M3 scheme
object ZonikColors {
    val gold = Color(0xFFE2CB6F)
    val goldDim = Color(0xFFB8A44E)
    val glassBg = Color(0xCC201E29) // ~80% opacity surface
    val gradientStart = Color(0xFF534AB7)
    val gradientEnd = Color(0xFF7C4DFF)
    val navBarBg = Color(0xE6201E29) // ~90% opacity
}

object ZonikShapes {
    val cardShape = RoundedCornerShape(16.dp)
    val miniPlayerShape = RoundedCornerShape(16.dp)
    val navBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val coverArtShape = RoundedCornerShape(12.dp)
    val coverArtLargeShape = RoundedCornerShape(24.dp)
    val buttonShape = RoundedCornerShape(12.dp)
    val badgeShape = RoundedCornerShape(6.dp)
}

private val ZonikDarkColorScheme = darkColorScheme(
    primary = Color(0xFFAFA9EC),
    onPrimary = Color(0xFF1A1540),
    primaryContainer = Color(0xFF534AB7),
    onPrimaryContainer = Color(0xFFE0DEFA),
    secondary = Color(0xFF9B93D8),
    onSecondary = Color(0xFF1A1540),
    secondaryContainer = Color(0xFF3D3670),
    onSecondaryContainer = Color(0xFFD4D0F0),
    tertiary = Color(0xFFE2CB6F),
    onTertiary = Color(0xFF0D0B18),
    background = Color(0xFF07060F),
    onBackground = Color(0xFFE4E1F0),
    surface = Color(0xFF0D0B18),
    onSurface = Color(0xFFE4E1F0),
    surfaceVariant = Color(0xFF1C1836),
    onSurfaceVariant = Color(0xFFA09CB8),
    surfaceContainerHigh = Color(0xFF2A2933),
    surfaceContainer = Color(0xFF201E29),
    surfaceContainerLow = Color(0xFF1C1A25),
    outline = Color(0xFF3D3670),
    outlineVariant = Color(0xFF1C1836),
    error = Color(0xFFEF5350),
    onError = Color(0xFF1A0000),
    errorContainer = Color(0xFF3B1010),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val ZonikTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
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
        typography = ZonikTypography,
        content = content
    )
}
