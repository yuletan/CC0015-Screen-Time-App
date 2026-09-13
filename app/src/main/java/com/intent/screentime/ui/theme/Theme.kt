package com.intent.screentime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * The app's theme.
 *
 * Material You dynamic colour is available but **off by default**: the authored Ember &
 * Ink palette is the app's identity, and a wallpaper-derived scheme would dissolve it.
 * When it is switched on, the semantic data colours stay fixed, because production and
 * consumption must keep meaning the same thing regardless of wallpaper.
 */
@Composable
fun IntentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val data = if (darkTheme) DarkData else LightData

    CompositionLocalProvider(LocalDataColors provides data) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = IntentTypography,
            shapes = IntentShapes,
            content = content,
        )
    }
}
