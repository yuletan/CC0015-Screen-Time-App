package com.intent.screentime.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type is doing the heavy lifting on this surface, so the scale is deliberately steep:
 * a hero number, a clear section voice, and quiet supporting text. Nothing sits between
 * two steps to hedge.
 *
 * The system font is used on purpose. Bundling a display face would add hundreds of
 * kilobytes to an app whose whole point is being light on the device.
 */
private val Sans = FontFamily.Default

internal val IntentTypography = Typography(
    // Hero numerals: screen time, score.
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W700,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W700,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.0).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W600,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
    ),

    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W700,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W600,
        fontSize = 23.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W600,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp,
    ),

    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W400,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W400,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W400,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    ),

    // Uppercase section eyebrows and chip text.
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W700,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.W600,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.9.sp,
    ),
)
