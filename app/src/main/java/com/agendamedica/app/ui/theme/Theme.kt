package com.agendamedica.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Material 3's [ColorScheme] has no slots for the success/warning/danger state families or
 * for the tint variants DESIGN.md defines, so those are exposed directly as top-level tokens
 * (see Color.kt) rather than forced into Material's vocabulary. Components should reach for
 * `AgendaMedicaColors` for anything state-related and `MaterialTheme.colorScheme` only for the
 * handful of slots mapped below (mainly so default Material components — e.g. text field
 * cursors — pick up the right brand color without per-component overrides).
 */
object AgendaMedicaColors {
    val surfaceCanvas = SurfaceCanvas
    val surfaceCard = SurfaceCard
    val surfaceSubtle = SurfaceSubtle
    val surfaceInput = SurfaceInput
    val surfaceDisabled = SurfaceDisabled

    val borderHairline = BorderHairline
    val borderInput = BorderInput
    val borderInputSubtle = BorderInputSubtle

    val inkPrimary = InkPrimary
    val inkSecondary = InkSecondary
    val inkTertiary = InkTertiary
    val inkDisabled = InkDisabled

    val accentPrimary = AccentPrimary
    val accentPrimaryTint = AccentPrimaryTint
    val accentPrimaryTintSoft = AccentPrimaryTintSoft
    val accentPrimaryInkSoft = AccentPrimaryInkSoft

    val successInk = SuccessInk
    val successBg = SuccessBg
    val successInkMuted = SuccessInkMuted

    val warningInk = WarningInk
    val warningBg = WarningBg

    val dangerInk = DangerInk
    val dangerBorder = DangerBorder
}

private val AgendaMedicaColorScheme = lightColorScheme(
    primary = AccentPrimary,
    onPrimary = SurfaceCard,
    background = SurfaceCanvas,
    onBackground = InkPrimary,
    surface = SurfaceCard,
    onSurface = InkPrimary,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = InkSecondary,
    outline = BorderInput,
    error = DangerInk,
)

/**
 * App-wide Compose theme. Intentionally a single, fixed light theme: DESIGN.md defines no
 * dark palette, and the product's "calm clinical" brand is specified as one deliberate look,
 * not light/dark variants.
 */
@Composable
fun AgendaMedicaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgendaMedicaColorScheme,
        typography = AgendaMedicaTypography,
        shapes = AgendaMedicaShapes,
        content = content,
    )
}
