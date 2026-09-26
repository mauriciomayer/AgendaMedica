package com.agendamedica.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.agendamedica.app.R

/**
 * DESIGN.md specifies a single typeface, Inter (weights 400-700). The four static weights are bundled in
 * `res/font` (Latin subset of Inter, SIL Open Font License; see docs/licenses/Inter-OFL.txt), cut from the
 * variable font of the design prototype. Every text style below uses this family.
 */
val InterFontFamily: FontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * Scale from DESIGN.md.typography (11-19px, weights 400-700). Named after the tokens
 * documented there rather than Material's own naming, then mapped onto Material 3's
 * [Typography] slots below.
 */
val DisplayStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp)
val HeadingStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp)
val LabelStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
val BodyStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp)
val BodySmStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp)
val CaptionStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
val MicroStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp)

val AgendaMedicaTypography = Typography(
    displayLarge = DisplayStyle,
    titleLarge = HeadingStyle,
    titleMedium = LabelStyle,
    bodyLarge = BodyStyle,
    bodyMedium = BodySmStyle,
    labelLarge = CaptionStyle,
    labelSmall = MicroStyle,
)
