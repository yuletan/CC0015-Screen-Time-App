package com.intent.screentime.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rounded, but not pill-shaped: enough softness to feel approachable, squared enough to
 * still read as an instrument. Pills are reserved for interactive chips where the shape
 * signals "tappable".
 */
internal val IntentShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
