package com.agendamedica.app.ui.patient

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.ui.components.AccessibleIconButton
import com.agendamedica.app.ui.components.CancelConfirmDialog
import com.agendamedica.app.ui.components.ConsultaCard
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.components.formatarDataHora
import com.agendamedica.app.ui.theme.AgendaMedicaColors

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
    onLogout: () -> Unit,
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

    LaunchedEffect(viewModel) {
        viewModel.navigateToLogin.collect { onLogout() }
    }

    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            AccessibleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Minhas consultas",
                    style = MaterialTheme.typography.titleLarge,
                    color = AgendaMedicaColors.inkPrimary,
                )
                TextButton(onClick = viewModel::logout) {
                    Text("Sair", color = AgendaMedicaColors.accentPrimary)
                }
            }
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
                                onCancelar = { viewModel.onCancelarClick(item.consulta.id) },
                                onReagendar = { onReagendar(item.consulta.doctorId, item.consulta.id) },
                            )
                        }
                    }
                    state.consultas.firstOrNull { it.consulta.id == state.confirmandoId }?.let { item ->
                        CancelConfirmDialog(
                            quem = "${item.consulta.doctorName}, ${formatarDataHora(item.consulta.start)}",
                            cancelando = state.cancelandoId != null,
                            onSim = viewModel::onCancelarSim,
                            onNao = viewModel::onCancelarNao,
                        )
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
