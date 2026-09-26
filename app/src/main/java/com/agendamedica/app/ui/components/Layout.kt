package com.agendamedica.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.ShapeLg
import com.agendamedica.app.ui.theme.ShapeMd
import com.agendamedica.app.ui.theme.ShapePill

/**
 * Screen shell of the design prototype (spec-8-1): a white app bar (title, optional subtitle, optional
 * round back button, optional actions on the right, hairline below) that extends behind the status
 * bar, over the canvas body with 18dp side padding and 16dp top/bottom padding. The Login (Story 6.7)
 * and every other screen use it, so the bar looks the same everywhere.
 *
 * The back button is drawn 36dp but its touch target is 48dp (the project's accessibility floor);
 * the bar's vertical padding is reduced when it is present so the bar keeps the prototype's height.
 *
 * @param scrollable when false the body is a plain [Column], for screens that host their own lazy list.
 * @param espacamento vertical gap between body children (14dp in the prototype; forms use 16dp).
 * @param rodape fixed white bar under the body (hairline on top, 14dp/18dp padding), e.g. the confirm button
 *   of Detalhe do médico; it takes over the bottom navigation inset (for screens without text fields:
 *   the keyboard inset is only applied to the body).
 */
@Composable
fun TelaPadrao(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    espacamento: Dp = 14.dp,
    rodape: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas, modifier = modifier) { padding ->
        val direcao = LocalLayoutDirection.current
        val ladoInicio = 18.dp + padding.calculateStartPadding(direcao)
        val ladoFim = 18.dp + padding.calculateEndPadding(direcao)
        Column(modifier = Modifier.fillMaxSize()) {
            // The white bar extends behind the status bar; only its content is pushed below it.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgendaMedicaColors.surfaceCard)
                    .padding(top = padding.calculateTopPadding())
                    .padding(
                        start = ladoInicio,
                        end = ladoFim,
                        top = if (onBack != null) 12.dp else 18.dp,
                        bottom = if (onBack != null) 8.dp else 14.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onBack != null) BotaoVoltar(onBack)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
                        color = AgendaMedicaColors.inkPrimary,
                        maxLines = 2, // wraps at large font scales instead of being cut off
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = AgendaMedicaColors.inkTertiary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                actions()
            }
            HorizontalDivider(color = AgendaMedicaColors.borderHairline)

            val corpo = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding() // edge-to-edge: without this the keyboard would cover the focused field and the button
                .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
                .padding(start = ladoInicio, end = ladoFim, top = 16.dp, bottom = 16.dp + (if (rodape == null) padding.calculateBottomPadding() else 0.dp))
            Column(modifier = corpo, verticalArrangement = Arrangement.spacedBy(espacamento), content = content)
            if (rodape != null) {
                HorizontalDivider(color = AgendaMedicaColors.borderHairline)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AgendaMedicaColors.surfaceCard)
                        .padding(start = ladoInicio, end = ladoFim, top = 14.dp, bottom = 14.dp + padding.calculateBottomPadding()),
                ) { rodape() }
            }
        }
    }
}

/** Round back button: 36dp drawn (prototype), 48dp touch target, announced as "Voltar". */
@Composable
private fun BotaoVoltar(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Voltar" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(AgendaMedicaColors.surfaceSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = AgendaMedicaColors.inkPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Tone of an [InfoBox]: success (green), warning/error (warm) or info (blue). */
enum class TomInfoBox { SUCESSO, AVISO, INFO }

/**
 * Rounded message box of the prototype (login errors, "senha redefinida", "link enviado", notes). It is a
 * polite live region, so screen readers announce it when it appears: compose it only while there is a message
 * to show (as every caller does), never as a permanently present, empty box. A note that is always on the
 * screen (not a reaction to something the user did) passes `anunciar = false`.
 */
@Composable
fun InfoBox(
    text: String,
    modifier: Modifier = Modifier,
    tom: TomInfoBox = TomInfoBox.AVISO,
    fontSize: TextUnit = 13.sp,
    anunciar: Boolean = true,
) {
    val (fundo, tinta) = when (tom) {
        TomInfoBox.SUCESSO -> AgendaMedicaColors.successBg to AgendaMedicaColors.inkPrimary
        TomInfoBox.AVISO -> AgendaMedicaColors.warningBg to AgendaMedicaColors.dangerInk
        TomInfoBox.INFO -> AgendaMedicaColors.accentPrimaryTintSoft to AgendaMedicaColors.accentPrimaryInkSoft
    }
    Text(
        text = text,
        color = tinta,
        fontSize = fontSize,
        modifier = modifier
            .semantics { if (anunciar) liveRegion = LiveRegionMode.Polite }
            .fillMaxWidth()
            .background(fundo, ShapeMd)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** White card of the prototype: 1dp hairline border, 14dp corners, 14dp padding. Optional whole-card click. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cores = CardDefaults.cardColors(containerColor = AgendaMedicaColors.surfaceCard)
    val borda = BorderStroke(1.dp, AgendaMedicaColors.borderHairline)
    val tamanho = modifier.fillMaxWidth()
    if (onClick != null) {
        Card(onClick = onClick, shape = ShapeLg, colors = cores, border = borda, modifier = tamanho) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(shape = ShapeLg, colors = cores, border = borda, modifier = tamanho) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Big tappable choice card ("Sou paciente" / "Sou médico"): title 15 bold, description 12.5, whole card clickable. */
@Composable
fun CartaoEscolha(titulo: String, descricao: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, onClick = onClick, contentPadding = 16.dp) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            color = AgendaMedicaColors.inkPrimary,
        )
        Text(
            text = descricao,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = AgendaMedicaColors.inkSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Doctor/patient avatar of the prototype: filled `accent-primary` circle with the initials in white
 * (semibold, ~34% of the size). Purely decorative — the name is always written next to it.
 */
@Composable
fun Avatar(nome: String, modifier: Modifier = Modifier, tamanho: Dp = 44.dp) {
    Box(
        modifier = modifier.size(tamanho).background(AgendaMedicaColors.accentPrimary, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(nome),
            color = Color.White,
            // dp -> sp through toSp(): the letters keep their size relative to the circle at any font scale.
            fontSize = with(LocalDensity.current) { (tamanho * 0.34f).toSp() },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** First letters of the first two words, ignoring a leading "Dr."/"Dra." title. */
internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val significant = words.filterNot { it.trimEnd('.').lowercase() in setOf("dr", "dra") }.ifEmpty { words }
    return significant.take(2).joinToString("") { it.first().uppercase() }
}

/** Small outlined pill button of the prototype's bar ("Minhas consultas"): blue border and text, 12.5sp semibold. */
@Composable
fun BotaoPilula(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = ShapePill,
        border = BorderStroke(1.dp, AgendaMedicaColors.accentPrimary),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = AgendaMedicaColors.surfaceCard, contentColor = AgendaMedicaColors.accentPrimary),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier,
    ) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Text action for the right side of the bar ("Sair"): 13sp semibold in the accent color, 48dp touch target. */
@Composable
fun BotaoBarra(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.sizeIn(minHeight = 48.dp)) {
        Text(text, color = AgendaMedicaColors.accentPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
