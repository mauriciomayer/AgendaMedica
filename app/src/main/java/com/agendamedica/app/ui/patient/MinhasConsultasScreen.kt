package com.agendamedica.app.ui.patient

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.ui.components.AccessibleIconButton
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.ShapeMd
import com.agendamedica.app.ui.theme.ShapePill

/**
 * Minhas Consultas (FR8, FR9). [lifecycleOwner] is the navigation entry: the list is re-read every
 * time the screen resumes (e.g. coming back from Reagendar).
 */
@Composable
fun MinhasConsultasScreen(
    lifecycleOwner: LifecycleOwner,
    onBack: () -> Unit,
    onNovaConsulta: () -> Unit,
    onReagendar: (doctorId: String, appointmentId: String) -> Unit,
) {
    val viewModel: MinhasConsultasViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            AccessibleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
            )
            Text(
                "Minhas consultas",
                style = MaterialTheme.typography.titleLarge,
                color = AgendaMedicaColors.inkPrimary,
            )
            Spacer(Modifier.height(12.dp))
            val errorMessage = state.errorMessage
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AgendaMedicaColors.accentPrimary)
                }
                errorMessage != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AgendaMedicaColors.dangerInk,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlineButton(text = "Tentar novamente", onClick = viewModel::retry)
                }
                state.consultas.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    state.actionMessage?.let { ActionMessage(it) }
                    Text(
                        text = MSG_SEM_CONSULTAS,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AgendaMedicaColors.inkSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(text = "+ Nova consulta", onClick = onNovaConsulta)
                }
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    state.actionMessage?.let { ActionMessage(it) }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        items(state.consultas, key = { it.consulta.id }) { item ->
                            ConsultaCard(
                                titulo = item.consulta.doctorName,
                                subtitulo = item.consulta.especialidade,
                                start = item.consulta.start,
                                convenio = item.consulta.convenio,
                                bloqueada = item.bloqueada,
                                confirmando = state.confirmandoId == item.consulta.id,
                                cancelando = state.cancelandoId == item.consulta.id,
                                onCancelar = { viewModel.onCancelarClick(item.consulta.id) },
                                onReagendar = { onReagendar(item.consulta.doctorId, item.consulta.id) },
                                onSim = viewModel::onCancelarSim,
                                onNao = viewModel::onCancelarNao,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = AgendaMedicaColors.dangerInk,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/** Card shared by Minhas Consultas (titulo = doctor) and Minha Agenda do Médico (titulo = patient). */
@Composable
internal fun ConsultaCard(
    titulo: String,
    subtitulo: String?,
    start: java.time.Instant,
    convenio: String,
    bloqueada: Boolean,
    confirmando: Boolean,
    cancelando: Boolean,
    onCancelar: () -> Unit,
    onReagendar: () -> Unit,
    onSim: () -> Unit,
    onNao: () -> Unit,
) {
    // Every card repeats the same button labels: name the appointment so TalkBack can tell them apart.
    val quem = "$titulo, ${formatarDataHora(start)}"
    Card(
        shape = ShapeMd,
        colors = CardDefaults.cardColors(containerColor = AgendaMedicaColors.surfaceCard),
        border = BorderStroke(1.dp, AgendaMedicaColors.borderHairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(titulo, style = MaterialTheme.typography.titleMedium, color = AgendaMedicaColors.inkPrimary)
                    if (subtitulo != null) {
                        Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = AgendaMedicaColors.inkSecondary)
                    }
                }
                Spacer(Modifier.padding(4.dp))
                StatusBadge(bloqueada = bloqueada)
            }
            Text(
                formatarDataHora(start),
                style = MaterialTheme.typography.bodyLarge,
                color = AgendaMedicaColors.inkPrimary,
            )
            Text("Convênio: $convenio", style = MaterialTheme.typography.bodySmall, color = AgendaMedicaColors.inkTertiary)
            Spacer(Modifier.height(8.dp))
            if (bloqueada) {
                Text(MSG_JANELA_24H, style = MaterialTheme.typography.bodySmall, color = AgendaMedicaColors.warningInk)
            }
            if (confirmando) {
                Text(
                    "Cancelar esta consulta?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgendaMedicaColors.inkPrimary,
                )
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
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlineButton(
                        text = "Cancelar",
                        onClick = onCancelar,
                        enabled = !bloqueada,
                        borderColor = AgendaMedicaColors.dangerInk,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Cancelar a consulta com $quem" },
                    )
                    OutlineButton(
                        text = "Reagendar",
                        onClick = onReagendar,
                        enabled = !bloqueada,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Reagendar a consulta com $quem" },
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
        style = MaterialTheme.typography.labelLarge,
        color = ink,
        modifier = Modifier.background(bg, ShapePill).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
