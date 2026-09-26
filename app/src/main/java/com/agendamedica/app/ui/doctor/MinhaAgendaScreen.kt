package com.agendamedica.app.ui.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.domain.model.DoctorProfile
import com.agendamedica.app.ui.components.AppCard
import com.agendamedica.app.ui.components.BotaoBarra
import com.agendamedica.app.ui.components.CancelConfirmDialog
import com.agendamedica.app.ui.components.ConsultaCard
import com.agendamedica.app.ui.components.InfoBox
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.TagChip
import com.agendamedica.app.ui.components.TelaPadrao
import com.agendamedica.app.ui.components.TomInfoBox
import com.agendamedica.app.ui.components.formatarDataHora
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Minha Agenda — where a Médico lands right after registering, and on every subsequent login
 * (Code Map). Shows the doctor's own profile card plus the upcoming appointments, with inline
 * cancellation and a Reagendar entry (Story 2.5). [lifecycleOwner] is the navigation entry: the list
 * is re-read whenever the screen resumes.
 */
@Composable
fun MinhaAgendaScreen(
    lifecycleOwner: LifecycleOwner,
    onReagendar: (doctorId: String, appointmentId: String) -> Unit,
    onLogout: () -> Unit,
    viewModel: MinhaAgendaViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateToLogin.collect { onLogout() }
    }

    MinhaAgendaConteudo(
        uiState = uiState,
        onSair = viewModel::logout,
        onRetryConsultas = viewModel::retryConsultas,
        onCancelar = viewModel::onCancelarClick,
        onReagendar = onReagendar,
        onCancelarSim = viewModel::onCancelarSim,
        onCancelarNao = viewModel::onCancelarNao,
    )
}

/** Stateless body of Minha Agenda (spec-8-6), so it can be tested without a ViewModel. */
@Composable
internal fun MinhaAgendaConteudo(
    uiState: MinhaAgendaUiState,
    onSair: () -> Unit,
    onRetryConsultas: () -> Unit,
    onCancelar: (String) -> Unit,
    onReagendar: (doctorId: String, appointmentId: String) -> Unit,
    onCancelarSim: () -> Unit,
    onCancelarNao: () -> Unit,
) {
    val profile = uiState.profile
    val erro = uiState.errorMessage
    TelaPadrao(
        title = "Minha agenda",
        // Only the profile branch hosts its own lazy list; loading and error text scroll with the shell.
        scrollable = uiState.isLoading || erro != null || profile == null,
        actions = { BotaoBarra(text = "Sair", onClick = onSair) },
    ) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxWidth().wrapContentSize(Alignment.Center)) {
                CircularProgressIndicator(color = AgendaMedicaColors.accentPrimary)
            }
            erro != null -> Text(text = erro, color = AgendaMedicaColors.dangerInk, style = MaterialTheme.typography.bodyMedium)
            profile != null -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    item { DoctorProfileCard(profile) }
                    item {
                        InfoBox(
                            text = "Conflitos de horário no mesmo período são bloqueados automaticamente pelo sistema — " +
                                "não é possível haver dois pacientes no mesmo horário.",
                            tom = TomInfoBox.INFO,
                            fontSize = 12.sp,
                            anunciar = false,
                        )
                    }
                    item {
                        Text(
                            text = "Próximas consultas",
                            color = AgendaMedicaColors.inkPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    uiState.actionMessage?.let { msg -> item { InfoBox(text = msg) } }
                    when {
                        uiState.consultasLoading -> item {
                            Box(Modifier.fillMaxWidth().wrapContentSize(Alignment.Center)) {
                                CircularProgressIndicator(color = AgendaMedicaColors.accentPrimary)
                            }
                        }
                        uiState.consultasErro != null -> item {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = uiState.consultasErro,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AgendaMedicaColors.dangerInk,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlineButton(text = "Tentar novamente", onClick = onRetryConsultas)
                            }
                        }
                        uiState.consultas.isEmpty() -> item {
                            Text(
                                text = MSG_AGENDA_VAZIA,
                                color = AgendaMedicaColors.inkTertiary,
                                fontSize = 13.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 10.dp),
                            )
                        }
                        else -> items(uiState.consultas, key = { it.consulta.id }) { item ->
                            ConsultaCard(
                                titulo = item.consulta.patientName,
                                subtitulo = null,
                                start = item.consulta.start,
                                convenio = item.consulta.convenio,
                                bloqueada = item.bloqueada,
                                onCancelar = { onCancelar(item.consulta.id) },
                                onReagendar = { uiState.doctorId?.let { onReagendar(it, item.consulta.id) } },
                            )
                        }
                    }
                }
                uiState.consultas.firstOrNull { it.consulta.id == uiState.confirmandoId }?.let { item ->
                    CancelConfirmDialog(
                        quem = "${item.consulta.patientName}, ${formatarDataHora(item.consulta.start)}",
                        cancelando = uiState.cancelandoId != null,
                        onSim = onCancelarSim,
                        onNao = onCancelarNao,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoctorProfileCard(profile: DoctorProfile) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(profile.name, color = AgendaMedicaColors.inkPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(profile.especialidade.label, color = AgendaMedicaColors.inkSecondary, fontSize = 13.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                profile.convenios.forEach { convenio -> TagChip(text = convenio.label) }
            }
            // Kept from before the prototype pass: the doctor must see the hours they registered.
            profile.schedule.forEach { block ->
                Text(
                    text = "${block.dia.label}: ${formatHora(block.startTime)} - ${formatHora(block.endTime)}",
                    color = AgendaMedicaColors.inkSecondary,
                    fontSize = 12.5.sp,
                )
            }
        }
    }
}

private val HORA_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** The schedule card only ever shows "HH:mm", whatever precision the [LocalTime] carries. */
internal fun formatHora(hora: LocalTime): String = hora.format(HORA_FORMAT)
