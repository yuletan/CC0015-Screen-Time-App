package com.intent.screentime.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Ember & Ink.
 *
 * The brand accent is a warm ember, and every neutral is tinted a few degrees toward it
 * so surfaces feel authored rather than left grey. This is a deliberate refusal of the
 * two reflexes for this category: calm teal "wellbeing" and indigo "tech".
 *
 * Ramps below are the sRGB fallbacks of an OKLCh construction, so lightness steps read
 * as roughly equal rather than merely being numerically even.
 */
private object Ember {
    val e50 = Color(0xFFFFF5F1)
    val e100 = Color(0xFFFFE8DE)
    val e200 = Color(0xFFFFCBB4)
    val e300 = Color(0xFFFFA584)
    val e400 = Color(0xFFFF7B52)
    val e500 = Color(0xFFF2542A)
    val e600 = Color(0xFFD93B12)
    val e700 = Color(0xFFB32D0D)
    val e800 = Color(0xFF8A250C)
    val e900 = Color(0xFF5E1A08)
}

/** Warm ink neutrals. Never pure grey, never pure black. */
private object Ink {
    val n0 = Color(0xFFFDF8F6)
    val n50 = Color(0xFFF7EFEB)
    val n100 = Color(0xFFF0E5E0)
    val n200 = Color(0xFFE2D5CF)
    val n300 = Color(0xFFCBBAB3)
    val n400 = Color(0xFF9E8A82)
    val n500 = Color(0xFF75645D)
    val n600 = Color(0xFF52443F)
    val n700 = Color(0xFF3A302D)
    val n800 = Color(0xFF241D1B)
    val n900 = Color(0xFF14100F)
    val n950 = Color(0xFF0C0908)
}

private object Accent {
    val secondary = Color(0xFF6B5B95)
    val secondaryContainerLight = Color(0xFFE9E1FF)
    val secondaryContainerDark = Color(0xFF4A3F73)
    val tertiary = Color(0xFF3F7D7A)
    val tertiaryContainerLight = Color(0xFFCBEBE7)
    val tertiaryContainerDark = Color(0xFF26524F)
    val errorLight = Color(0xFFBA1A1A)
    val errorDark = Color(0xFFFFB4AB)
    val errorContainerLight = Color(0xFFFFDAD6)
    val errorContainerDark = Color(0xFF93000A)
}

/**
 * Semantic data colours, kept separate from the Material roles because they carry
 * meaning that must survive a theme change.
 *
 * Production and consumption are separated by hue *and* lightness *and* an icon, so
 * the split never depends on colour alone.
 */
@Immutable
data class DataColors(
    val production: Color,
    val consumption: Color,
    val utility: Color,
    val neutral: Color,
    /** Sentiment, deliberately distinct from the category hues above. */
    val positive: Color,
    val negative: Color,
    /** Ordered palette for multi-series charts and category legends. */
    val series: List<Color>,
)

private val LightDataColors = DataColors(
    production = Color(0xFFC77A05),
    consumption = Color(0xFFA62B84),
    utility = Color(0xFF4C6376),
    neutral = Color(0xFF75645D),
    positive = Color(0xFF136B4F),
    negative = Color(0xFFB3261E),
    series = listOf(
        Color(0xFFD93B12),
        Color(0xFFC77A05),
        Color(0xFFA62B84),
        Color(0xFF6B5B95),
        Color(0xFF2F7D7A),
        Color(0xFF2C6BA8),
        Color(0xFF5E8C2A),
        Color(0xFFB03060),
    ),
)

private val DarkDataColors = DataColors(
    production = Color(0xFFFFC14D),
    consumption = Color(0xFFE86CC4),
    utility = Color(0xFF9DB4C8),
    neutral = Color(0xFFB5A49C),
    positive = Color(0xFF6FD3A6),
    negative = Color(0xFFFF8A80),
    series = listOf(
        Color(0xFFFF7B52),
        Color(0xFFFFC14D),
        Color(0xFFE86CC4),
        Color(0xFFAFA0E0),
        Color(0xFF6FCFC9),
        Color(0xFF7FB6F0),
        Color(0xFFA8D46A),
        Color(0xFFEE85A8),
    ),
)

val LocalDataColors = staticCompositionLocalOf { LightDataColors }

internal val LightScheme = lightColorScheme(
    primary = Ember.e600,
    onPrimary = Color.White,
    primaryContainer = Ember.e100,
    onPrimaryContainer = Ember.e900,
    inversePrimary = Ember.e300,

    secondary = Accent.secondary,
    onSecondary = Color.White,
    secondaryContainer = Accent.secondaryContainerLight,
    onSecondaryContainer = Color(0xFF211443),

    tertiary = Accent.tertiary,
    onTertiary = Color.White,
    tertiaryContainer = Accent.tertiaryContainerLight,
    onTertiaryContainer = Color(0xFF00201E),

    error = Accent.errorLight,
    onError = Color.White,
    errorContainer = Accent.errorContainerLight,
    onErrorContainer = Color(0xFF410002),

    background = Ink.n0,
    onBackground = Ink.n900,

    surface = Ink.n0,
    onSurface = Ink.n900,
    surfaceVariant = Ink.n100,
    onSurfaceVariant = Ink.n600,
    surfaceTint = Ember.e600,

    surfaceBright = Color(0xFFFFFBF9),
    surfaceDim = Ink.n100,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Ink.n50,
    surfaceContainer = Ink.n50,
    surfaceContainerHigh = Ink.n100,
    surfaceContainerHighest = Ink.n200,

    outline = Ink.n400,
    outlineVariant = Ink.n200,

    inverseSurface = Ink.n800,
    inverseOnSurface = Ink.n50,
    scrim = Color.Black,
)

internal val DarkScheme = darkColorScheme(
    primary = Ember.e400,
    onPrimary = Color(0xFF3D0F03),
    primaryContainer = Ember.e800,
    onPrimaryContainer = Ember.e100,
    inversePrimary = Ember.e700,

    secondary = Color(0xFFC9BEEB),
    onSecondary = Color(0xFF362B5C),
    secondaryContainer = Accent.secondaryContainerDark,
    onSecondaryContainer = Color(0xFFE9E1FF),

    tertiary = Color(0xFFA9CFCB),
    onTertiary = Color(0xFF0B3533),
    tertiaryContainer = Accent.tertiaryContainerDark,
    onTertiaryContainer = Color(0xFFCBEBE7),

    error = Accent.errorDark,
    onError = Color(0xFF690005),
    errorContainer = Accent.errorContainerDark,
    onErrorContainer = Color(0xFFFFDAD6),

    background = Ink.n900,
    onBackground = Color(0xFFF2E9E6),

    surface = Ink.n900,
    onSurface = Color(0xFFF2E9E6),
    surfaceVariant = Ink.n700,
    onSurfaceVariant = Ink.n300,
    surfaceTint = Ember.e400,

    surfaceBright = Ink.n700,
    surfaceDim = Ink.n950,
    surfaceContainerLowest = Ink.n950,
    surfaceContainerLow = Ink.n800,
    surfaceContainer = Ink.n800,
    surfaceContainerHigh = Ink.n700,
    surfaceContainerHighest = Color(0xFF423836),

    outline = Color(0xFF9C8A83),
    outlineVariant = Ink.n700,

    inverseSurface = Ink.n50,
    inverseOnSurface = Ink.n800,
    scrim = Color.Black,
)

internal val LightData = LightDataColors
internal val DarkData = DarkDataColors

/** Reads the semantic data palette for the active theme. */
val dataColors: DataColors
    @Composable
    @ReadOnlyComposable
    get() = LocalDataColors.current

/** Parses the `#RRGGBB` strings stored on category rows. */
fun String.toComposeColor(fallback: Color = Color.Gray): Color {
    val hex = trim().removePrefix("#")
    val value = hex.toLongOrNull(radix = 16) ?: return fallback
    return when (hex.length) {
        6 -> Color(value or 0xFF000000L)
        8 -> Color(value)
        else -> fallback
    }
}
