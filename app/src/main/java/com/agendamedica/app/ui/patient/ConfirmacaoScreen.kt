package com.agendamedica.app.ui.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agendamedica.app.ui.components.AppCard
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.components.TelaPadrao
import com.agendamedica.app.ui.components.formatarDiaHora
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import java.time.Instant

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
    TelaPadrao(title = "Confirmado", espacamento = 16.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Decorative: the title below already says what happened.
            Box(
                modifier = Modifier.size(64.dp).background(AgendaMedicaColors.successBg, CircleShape).clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = AgendaMedicaColors.successInkMuted,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                "Consulta agendada!",
                color = AgendaMedicaColors.inkPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
        }
        AppCard(contentPadding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(doctorName, color = AgendaMedicaColors.inkPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(especialidade, color = AgendaMedicaColors.inkSecondary, fontSize = 13.sp)
                Text(
                    formatarDiaHora(Instant.ofEpochMilli(startMillis)),
                    color = AgendaMedicaColors.inkPrimary,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text("Convênio: $convenio", color = AgendaMedicaColors.inkTertiary, fontSize = 12.sp)
            }
        }
        PrimaryButton(text = "Ver minhas consultas", onClick = onVerConsultas)
        OutlineButton(text = "Buscar outro médico", onClick = onVoltarBusca)
    }
}
