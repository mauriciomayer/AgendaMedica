package com.agendamedica.app.ui.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agendamedica.app.domain.agenda.FUSO_AGENDA
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATA_HORA_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

/** Formats [start] as São Paulo date and time (AD-8). */
fun formatarDataHora(start: Instant): String = DATA_HORA_FORMAT.format(start.atZone(FUSO_AGENDA))

/** Confirmação (FR6): summary of the booked appointment. The "!" appears only in the title. */
@Composable
fun ConfirmacaoScreen(
    doctorName: String,
    especialidade: String,
    startMillis: Long,
    convenio: String,
    onVoltarBusca: () -> Unit,
    onVerConsultas: () -> Unit = {},
) {
    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Consulta agendada!",
                style = MaterialTheme.typography.titleLarge,
                color = AgendaMedicaColors.successInk,
            )
            Spacer(Modifier.height(8.dp))
            Linha("Médico", doctorName)
            Linha("Especialidade", especialidade)
            Linha("Data e horário", formatarDataHora(Instant.ofEpochMilli(startMillis)))
            Linha("Convênio", convenio)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = "Ver minhas consultas", onClick = onVerConsultas)
            Spacer(Modifier.height(8.dp))
            OutlineButton(text = "Voltar à Busca", onClick = onVoltarBusca)
        }
    }
}

@Composable
private fun Linha(rotulo: String, valor: String) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = AgendaMedicaColors.inkTertiary)
        Text(valor, style = MaterialTheme.typography.bodyLarge, color = AgendaMedicaColors.inkPrimary)
    }
}
