package com.agendamedica.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.agendamedica.app.domain.agenda.FUSO_AGENDA
import com.agendamedica.app.domain.agenda.JANELA_ALTERACAO_HORAS
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.ShapeMd
import com.agendamedica.app.ui.theme.ShapeSm
import com.agendamedica.app.ui.theme.ShapePill
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

// Shared by Minhas Consultas (Paciente) and Minha Agenda (Médico): lives here so neither feature
// package depends on the other (spec-7-2).

const val MSG_JANELA_24H = "Bloqueado: faltam menos de ${JANELA_ALTERACAO_HORAS}h — não é mais possível cancelar ou reagendar."

private val DATA_HORA_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

/** Formats [start] as São Paulo date and time (AD-8). */
fun formatarDataHora(start: Instant): String = DATA_HORA_FORMAT.format(start.atZone(FUSO_AGENDA))

private val DIA_HORA_FORMAT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", Locale("pt", "BR"))

/** Short form used inside the cards, as in the prototype: "26/09 às 09:00". */
fun formatarDiaHora(start: Instant): String = DIA_HORA_FORMAT.format(start.atZone(FUSO_AGENDA))

/** Card shared by Minhas Consultas (titulo = doctor) and Minha Agenda do Médico (titulo = patient). */
@Composable
internal fun ConsultaCard(
    titulo: String,
    subtitulo: String?,
    start: Instant,
    convenio: String,
    bloqueada: Boolean,
    onCancelar: () -> Unit,
    onReagendar: () -> Unit,
) {
    // Every card repeats the same button labels: name the appointment so TalkBack can tell them apart.
    val quem = "$titulo, ${formatarDataHora(start)}"
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(titulo, color = AgendaMedicaColors.inkPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (subtitulo != null) {
                        Text(subtitulo, color = AgendaMedicaColors.inkSecondary, fontSize = 12.5.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(bloqueada = bloqueada)
            }
            Text("${formatarDiaHora(start)} · $convenio", color = AgendaMedicaColors.inkPrimary, fontSize = 13.5.sp)
            if (bloqueada) {
                Text(
                    MSG_JANELA_24H,
                    color = AgendaMedicaColors.warningInk,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AgendaMedicaColors.warningBg, ShapeSm)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                BotaoAcao(
                    text = "Cancelar",
                    onClick = onCancelar,
                    enabled = !bloqueada,
                    cor = AgendaMedicaColors.dangerInk,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Cancelar a consulta com $quem" },
                )
                BotaoAcao(
                    text = "Reagendar",
                    onClick = onReagendar,
                    enabled = !bloqueada,
                    cor = AgendaMedicaColors.accentPrimary,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Reagendar a consulta com $quem" },
                )
            }
        }
    }
}

/** Compact outlined action of the appointment card (13sp semibold, 48dp touch target). */
@Composable
private fun BotaoAcao(text: String, onClick: () -> Unit, enabled: Boolean, cor: Color, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ShapeSm,
        border = BorderStroke(1.dp, if (enabled) cor else AgendaMedicaColors.borderInput),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = cor),
        contentPadding = PaddingValues(vertical = 9.dp),
        modifier = modifier.sizeIn(minHeight = 48.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Blocking confirmation dialog for cancellation (spec-6-4): rendered once per screen, outside the
 * list, instead of inline per-card (reverts UX-DR5/Stories 2.4-2.5 per explicit user decision).
 * Neither outside touch nor the system back gesture dismiss it -- only Sim/Não. (A reload that finds
 * the appointment gone still closes it via the caller no longer supplying a match -- that is the
 * pre-existing 24h/stale-data guard reacting to changed data, not a user dismissal.)
 */
@Composable
internal fun CancelConfirmDialog(quem: String, cancelando: Boolean, onSim: () -> Unit, onNao: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = ShapeMd, color = AgendaMedicaColors.surfaceCard) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cancelar esta consulta?", style = MaterialTheme.typography.titleMedium, color = AgendaMedicaColors.inkPrimary)
                Text(quem, style = MaterialTheme.typography.bodyMedium, color = AgendaMedicaColors.inkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlineButton(
                        text = if (cancelando) "Cancelando..." else "Sim",
                        onClick = onSim,
                        enabled = !cancelando,
                        borderColor = AgendaMedicaColors.dangerInk,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Sim, cancelar a consulta com $quem" },
                    )
                    OutlineButton(
                        text = "Não",
                        onClick = onNao,
                        enabled = !cancelando,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Não cancelar a consulta com $quem" },
                    )
                }
            }
        }
    }
}

/** "Confirmada" / "Bloqueada": state carried by text, not only by color. */
@Composable
private fun StatusBadge(bloqueada: Boolean) {
    val texto = if (bloqueada) "Bloqueada" else "Confirmada"
    val ink = if (bloqueada) AgendaMedicaColors.warningInk else AgendaMedicaColors.successInkMuted
    val bg = if (bloqueada) AgendaMedicaColors.warningBg else AgendaMedicaColors.successBg
    Text(
        text = texto,
        color = ink,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(bg, ShapePill).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}
