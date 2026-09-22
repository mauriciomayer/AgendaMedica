package com.agendamedica.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Exact red of the prototype's medical-cross seal (`#D6362E`). DESIGN.md defines no token for
 * it (it's an accent specific to the logo, not the brand's `accent-primary`), so it's kept as a
 * literal here rather than approximated against an existing color, per spec-4-1-splash-icone.md.
 */
private val LogoSealRed = Color(0xFFD6362E)

/**
 * App logo: a white "document" (rounded rect + 3 horizontal lines simulating text) with a red
 * medical-cross seal overlapping its bottom-right corner. Faithful reproduction of the
 * prototype's SVG (viewBox 72x72 — see spec-4-1-splash-icone.md's Design Notes for the exact
 * source markup), scaled uniformly to [size]. Used by [com.agendamedica.app.ui.splash.SplashScreen]
 * and available for future reuse (e.g. Login header, out of scope for this story).
 */
@Composable
fun AgendaMedicaLogo(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val scale = this.size.width / 72f

        // Document: white rounded rect, light-gray stroke, 3 horizontal "text" lines.
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(14f * scale, 10f * scale),
            size = Size(34f * scale, 46f * scale),
            cornerRadius = CornerRadius(6f * scale, 6f * scale),
        )
        drawRoundRect(
            color = SurfaceDisabled,
            topLeft = Offset(14f * scale, 10f * scale),
            size = Size(34f * scale, 46f * scale),
            cornerRadius = CornerRadius(6f * scale, 6f * scale),
            style = Stroke(width = 2f * scale),
        )
        drawLine(
            color = SurfaceDisabled,
            start = Offset(21f * scale, 22f * scale),
            end = Offset(41f * scale, 22f * scale),
            strokeWidth = 2f * scale,
        )
        drawLine(
            color = SurfaceDisabled,
            start = Offset(21f * scale, 29f * scale),
            end = Offset(41f * scale, 29f * scale),
            strokeWidth = 2f * scale,
        )
        drawLine(
            color = SurfaceDisabled,
            start = Offset(21f * scale, 36f * scale),
            end = Offset(34f * scale, 36f * scale),
            strokeWidth = 2f * scale,
        )

        // Seal: red rounded square with a white cross, overlapping the document's bottom-right.
        drawRoundRect(
            color = LogoSealRed,
            topLeft = Offset(34f * scale, 26f * scale),
            size = Size(34f * scale, 34f * scale),
            cornerRadius = CornerRadius(8f * scale, 8f * scale),
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(47.5f * scale, 34f * scale),
            size = Size(7f * scale, 18f * scale),
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(42f * scale, 39.5f * scale),
            size = Size(18f * scale, 7f * scale),
        )
    }
}
