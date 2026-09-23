package com.agendamedica.app.ui.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.patient.ConsultaCard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.domain.model.DoctorProfile
import com.agendamedica.app.ui.components.TagChip
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.ShapeCircle
import com.agendamedica.app.ui.theme.ShapeLg
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

    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Minha Agenda",
                    style = MaterialTheme.typography.titleLarge,
                    color = AgendaMedicaColors.inkPrimary,
                )
                TextButton(onClick = viewModel::logout) {
                    Text("Sair", color = AgendaMedicaColors.accentPrimary)
                }
            }
            Spacer(Modifier.height(16.dp))

            when {
                uiState.isLoading -> Box(Modifier.fillMaxWidth().wrapContentSize(Alignment.Center)) {
                    CircularProgressIndicator(color = AgendaMedicaColors.accentPrimary)
                }
                uiState.errorMessage != null -> Text(
                    text = uiState.errorMessage!!,
                    color = AgendaMedicaColors.dangerInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
                uiState.profile != null -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { DoctorProfileCard(uiState.profile!!) }
                    item {
                        Text(
                            text = "Próximas consultas",
                            style = MaterialTheme.typography.labelLarge,
                            color = AgendaMedicaColors.inkSecondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    uiState.actionMessage?.let { msg ->
                        item {
                            Text(msg, style = MaterialTheme.typography.bodyMedium, color = AgendaMedicaColors.dangerInk)
                        }
                    }
                    when {
                        uiState.consultasLoading -> item {
                            Box(Modifier.fillMaxWidth().wrapContentSize(Alignment.Center)) {
                                CircularProgressIndicator(color = AgendaMedicaColors.accentPrimary)
                            }
                        }
                        uiState.consultasErro != null -> item {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = uiState.consultasErro!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AgendaMedicaColors.dangerInk,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlineButton(text = "Tentar novamente", onClick = viewModel::retryConsultas)
                            }
                        }
                        uiState.consultas.isEmpty() -> item {
                            Text(
                                text = MSG_AGENDA_VAZIA,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AgendaMedicaColors.inkTertiary,
                            )
                        }
                        else -> items(uiState.consultas, key = { it.consulta.id }) { item ->
                            ConsultaCard(
                                titulo = item.consulta.patientName,
                                subtitulo = null,
                                start = item.consulta.start,
                                convenio = item.consulta.convenio,
                                bloqueada = item.bloqueada,
                                confirmando = uiState.confirmandoId == item.consulta.id,
                                cancelando = uiState.cancelandoId == item.consulta.id,
                                onCancelar = { viewModel.onCancelarClick(item.consulta.id) },
                                onReagendar = { uiState.doctorId?.let { onReagendar(it, item.consulta.id) } },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoctorProfileCard(profile: DoctorProfile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgendaMedicaColors.surfaceCard, ShapeLg)
            .border(1.dp, AgendaMedicaColors.borderHairline, ShapeLg)
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(AgendaMedicaColors.accentPrimary, ShapeCircle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsOf(profile.name),
                color = AgendaMedicaColors.surfaceCard,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            color = AgendaMedicaColors.inkPrimary,
        )
        Text(
            text = profile.especialidade.label,
            style = MaterialTheme.typography.bodyMedium,
            color = AgendaMedicaColors.inkSecondary,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            profile.convenios.forEach { convenio -> TagChip(text = convenio.label) }
        }
        if (profile.schedule.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            profile.schedule.forEach { block ->
                Text(
                    text = "${block.dia.label}: ${formatHora(block.startTime)} - ${formatHora(block.endTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgendaMedicaColors.inkSecondary,
                )
            }
        }
    }
}

/** Postgres `time` serializes with seconds ("08:00:00"); the schedule card only ever shows "HH:mm". */
internal fun formatHora(hora: String): String =
    LocalTime.parse(hora).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun initialsOf(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
