package com.agendamedica.app.ui.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.ui.components.BotaoBarra
import com.agendamedica.app.ui.components.CancelConfirmDialog
import com.agendamedica.app.ui.components.ConsultaCard
import com.agendamedica.app.ui.components.InfoBox
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.components.TelaPadrao
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

    MinhasConsultasConteudo(
        state = state,
        onBack = onBack,
        onSair = viewModel::logout,
        onNovaConsulta = onNovaConsulta,
        onRetry = viewModel::retry,
        onCancelar = viewModel::onCancelarClick,
        onReagendar = onReagendar,
        onCancelarSim = viewModel::onCancelarSim,
        onCancelarNao = viewModel::onCancelarNao,
    )
}

/** Stateless body of Minhas Consultas (spec-8-3), so it can be tested without a ViewModel. */
@Composable
internal fun MinhasConsultasConteudo(
    state: MinhasConsultasUiState,
    onBack: () -> Unit,
    onSair: () -> Unit,
    onNovaConsulta: () -> Unit,
    onRetry: () -> Unit,
    onCancelar: (String) -> Unit,
    onReagendar: (doctorId: String, appointmentId: String) -> Unit,
    onCancelarSim: () -> Unit,
    onCancelarNao: () -> Unit,
) {
    TelaPadrao(
        title = "Minhas consultas",
        onBack = onBack,
        scrollable = false,
        actions = { BotaoBarra(text = "Sair", onClick = onSair) },
    ) {
        val errorMessage = state.errorMessage
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AgendaMedicaColors.accentPrimary)
            }
            errorMessage != null -> Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgendaMedicaColors.dangerInk,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                OutlineButton(text = "Tentar novamente", onClick = onRetry)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                state.actionMessage?.let { mensagem -> item { InfoBox(text = mensagem) } }
                if (state.consultas.isEmpty()) {
                    item {
                        Text(
                            text = MSG_SEM_CONSULTAS,
                            color = AgendaMedicaColors.inkTertiary,
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 10.dp),
                        )
                    }
                }
                items(state.consultas, key = { it.consulta.id }) { item ->
                    ConsultaCard(
                        titulo = item.consulta.doctorName,
                        subtitulo = item.consulta.especialidade,
                        start = item.consulta.start,
                        convenio = item.consulta.convenio,
                        bloqueada = item.bloqueada,
                        onCancelar = { onCancelar(item.consulta.id) },
                        onReagendar = { onReagendar(item.consulta.doctorId, item.consulta.id) },
                    )
                }
                item { PrimaryButton(text = "+ Nova consulta", onClick = onNovaConsulta, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        state.consultas.firstOrNull { it.consulta.id == state.confirmandoId }?.let { item ->
            CancelConfirmDialog(
                quem = "${item.consulta.doctorName}, ${formatarDataHora(item.consulta.start)}",
                cancelando = state.cancelandoId != null,
                onSim = onCancelarSim,
                onNao = onCancelarNao,
            )
        }
    }
}
