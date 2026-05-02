package com.zonik.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zonik.app.R

private val RobotoFlex = FontFamily(
    Font(R.font.roboto_flex, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.roboto_flex, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.roboto_flex, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.roboto_flex, FontWeight.Bold, FontStyle.Normal),
)

val ZonikFont: FontFamily = RobotoFlex

private fun base(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = RobotoFlex,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

val ZonikTypography: Typography = Typography(
    displayLarge = base(57, 64, FontWeight.Normal, -0.25f),
    displayMedium = base(36, 44, FontWeight.Normal, 0f),
    displaySmall = base(36, 44, FontWeight.Normal, 0f),

    headlineLarge = base(32, 40, FontWeight.Normal, 0f),
    headlineMedium = base(28, 36, FontWeight.Normal, 0f),
    headlineSmall = base(24, 32, FontWeight.Medium, 0f),

    titleLarge = base(22, 28, FontWeight.Medium, 0f),
    titleMedium = base(16, 24, FontWeight.SemiBold, 0.15f),
    titleSmall = base(14, 20, FontWeight.SemiBold, 0.1f),

    bodyLarge = base(16, 24, FontWeight.Normal, 0.5f),
    bodyMedium = base(14, 20, FontWeight.Normal, 0.25f),
    bodySmall = base(12, 16, FontWeight.Normal, 0.4f),

    labelLarge = base(14, 20, FontWeight.SemiBold, 0.1f),
    labelMedium = base(12, 16, FontWeight.SemiBold, 0.5f),
    labelSmall = base(11, 16, FontWeight.SemiBold, 0.5f),
)
