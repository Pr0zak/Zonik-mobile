package com.zonik.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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

@Composable
fun ZonikTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> NEUTRAL_SCHEME
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
