package com.agendamedica.app.ui.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.domain.model.DoctorProfile
import com.agendamedica.app.ui.components.TagChip
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.ShapeCircle
import com.agendamedica.app.ui.theme.ShapeLg

/**
 * Minha Agenda — where a Médico lands right after registering, and on every subsequent login
 * (Code Map). Shows the doctor's own profile card plus a fixed "sem consultas" empty state
 * (State Patterns: "Nenhuma consulta agendada ainda.") since `appointments` is Épico 2 scope.
 */
@Composable
fun MinhaAgendaScreen(viewModel: MinhaAgendaViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(18.dp),
        ) {
            Text(
                text = "Minha Agenda",
                style = MaterialTheme.typography.titleLarge,
                color = AgendaMedicaColors.inkPrimary,
            )
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
                uiState.profile != null -> {
                    DoctorProfileCard(uiState.profile!!)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Próximas consultas",
                        style = MaterialTheme.typography.labelLarge,
                        color = AgendaMedicaColors.inkSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Nenhuma consulta agendada ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AgendaMedicaColors.inkTertiary,
                    )
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
                    text = "${block.dia.label}: ${block.startTime} - ${block.endTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgendaMedicaColors.inkSecondary,
                )
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
