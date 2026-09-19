package com.agendamedica.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color tokens from DESIGN.md (_bmad-output/planning-artifacts/ux-designs/ux-AgendaMedica-2026-09-17/DESIGN.md).
 *
 * DESIGN.md specifies most tokens in OKLCH. Compose's [Color] constructor takes sRGB, so the
 * values below are sRGB approximations of those OKLCH tokens (computed by eye against the
 * documented lightness/chroma/hue). `accent-primary` is given as an exact hex in DESIGN.md and
 * is reproduced exactly. If pixel-perfect fidelity to the prototype ever matters, recompute
 * these with a proper OKLCH -> sRGB conversion against the OKLCH strings in DESIGN.md's
 * frontmatter rather than adjusting them by eye.
 */

// Surfaces
val SurfaceCanvas = Color(0xFFF3F8F4) // oklch(0.97 0.015 150)
val SurfaceCard = Color(0xFFFFFFFF) // #FFFFFF (exact)
val SurfaceSubtle = Color(0xFFEFECE7) // oklch(0.95 0.01 75)
val SurfaceInput = Color(0xFFF9F8F6) // oklch(0.98 0.005 75)
val SurfaceDisabled = Color(0xFFD3CFC8) // oklch(0.85 0.01 75)

// Borders
val BorderHairline = Color(0xFFE7E3DD) // oklch(0.92 0.01 75)
val BorderInput = Color(0xFFDCD8D1) // oklch(0.88 0.01 75)
val BorderInputSubtle = Color(0xFFD2CEC7) // oklch(0.85 0.01 75)

// Text (ink)
val InkPrimary = Color(0xFF3A342E) // oklch(0.25 0.02 75)
val InkSecondary = Color(0xFF7A7268) // oklch(0.5 0.02 75)
val InkTertiary = Color(0xFF857D72) // oklch(0.55 0.02 75)
val InkDisabled = Color(0xFFB3AEA7) // oklch(0.7 0.01 75)

// Brand — the single accent color, reserved for actions/selection (never decoration)
val AccentPrimary = Color(0xFF3B6FE0) // exact
val AccentPrimaryTint = Color(0xFFE3EBFB) // oklch(0.94 0.05 250)
val AccentPrimaryTintSoft = Color(0xFFEBEEF7) // oklch(0.95 0.02 250)
val AccentPrimaryInkSoft = Color(0xFF3A5AA6) // oklch(0.4 0.1 250)

// State families — used only to communicate state, never as decoration
val SuccessInk = Color(0xFF2F7A52) // oklch(0.45 0.1 150)
val SuccessBg = Color(0xFFE0F3E7) // oklch(0.94 0.05 150)
val SuccessInkMuted = Color(0xFF336B4C) // oklch(0.4 0.08 150)

val WarningInk = Color(0xFFA15A1E) // oklch(0.5 0.12 40)
val WarningBg = Color(0xFFFBEEDD) // oklch(0.95 0.04 40)

val DangerInk = Color(0xFFC43D2E) // oklch(0.5 0.14 25)
val DangerBorder = Color(0xFFE8C4BD) // oklch(0.85 0.05 25)
