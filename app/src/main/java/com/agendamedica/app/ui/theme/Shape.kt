package com.agendamedica.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape tokens from DESIGN.md: pill (100px), lg (14px), md (10-12px), circle (50%).
 * Nothing in the design is a hard rectangle — every interactive element gets at least
 * a small radius or is a pill.
 */
val ShapePill = RoundedCornerShape(percent = 50) // functionally "100px" for any control height used here
val ShapeLg = RoundedCornerShape(14.dp)
val ShapeMd = RoundedCornerShape(11.dp) // midpoint of the documented 10-12px range
val ShapeCircle = CircleShape

val AgendaMedicaShapes = Shapes(
    extraSmall = ShapeMd,
    small = ShapeMd,
    medium = ShapeLg,
    large = ShapeLg,
    extraLarge = ShapePill,
)
