package com.zonik.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// Match the phone app's dark purple/violet theme on AMOLED black
val ZonikWearColors = Colors(
    primary = Color(0xFFAFA9EC),
    primaryVariant = Color(0xFF534AB7),
    secondary = Color(0xFF9B93D8),
    secondaryVariant = Color(0xFF3D3670),
    background = Color.Black,
    surface = Color(0xFF0D0B18),
    error = Color(0xFFEF5350),
    onPrimary = Color(0xFF1A1540),
    onSecondary = Color(0xFF1A1540),
    onBackground = Color(0xFFE4E1F0),
    onSurface = Color(0xFFE4E1F0),
    onError = Color.Black,
)

@Composable
fun ZonikWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = ZonikWearColors,
        content = content
    )
}
