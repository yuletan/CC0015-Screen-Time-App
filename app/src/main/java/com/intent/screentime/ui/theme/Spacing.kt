package com.intent.screentime.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A 4/8/16/24/36 rhythm. Every vertical gap in the app is one of these values, so
 * spacing is a decision that was made once rather than a judgement call per screen.
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 36.dp
    val xxl: Dp = 48.dp

    /** Horizontal page gutter. */
    val gutter: Dp = 20.dp
}
