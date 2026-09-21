package com.agendamedica.app.ui.patient

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.domain.agenda.AgendaSlot
import com.agendamedica.app.domain.agenda.diaSemanaDe
import com.agendamedica.app.ui.components.AccessibleIconButton
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.TagChip
import com.agendamedica.app.ui.components.ToggleChip
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.ShapeMd
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HORA_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private const val COLUNAS = 3

/** Detalhe do Médico (FR5, FR6): header, day carousel and the 15-minute slot grid. */
@Composable
fun DetalheMedicoScreen(doctorId: String, onBack: () -> Unit) {
    val viewModel: DetalheMedicoViewModel = viewModel(
        key = "detalhe-$doctorId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DetalheMedicoViewModel(doctorId) as T
        },
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            AccessibleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
            )
            val errorMessage = state.errorMessage
            val doctor = state.doctor
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
                doctor != null -> DetalheContent(state, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetalheContent(state: DetalheMedicoUiState, viewModel: DetalheMedicoViewModel) {
    val doctor = state.doctor ?: return
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(doctor.name, style = MaterialTheme.typography.titleLarge, color = AgendaMedicaColors.inkPrimary)
        Text(
            doctor.especialidade.label,
            style = MaterialTheme.typography.bodyMedium,
            color = AgendaMedicaColors.inkSecondary,
        )
        Text(doctor.city, style = MaterialTheme.typography.bodySmall, color = AgendaMedicaColors.inkTertiary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            doctor.convenios.forEach { TagChip(it.label) }
        }
        Spacer(Modifier.height(20.dp))

        if (state.dias.isEmpty()) {
            Text(MSG_SEM_DIAS, style = MaterialTheme.typography.bodyMedium, color = AgendaMedicaColors.inkSecondary)
            return@Column
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.dias, key = { it.toEpochDay() }) { dia ->
                ToggleChip(
                    text = rotuloDia(dia),
                    selected = dia == state.selectedDia,
                    onClick = { viewModel.onDiaSelected(dia) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (state.selectedDia == null) {
            Text(
                "Selecione um dia para ver os horários.",
                style = MaterialTheme.typography.bodyMedium,
                color = AgendaMedicaColors.inkSecondary,
            )
        } else {
            if (state.slots.isEmpty()) {
                Text(
                    MSG_SEM_HORARIOS,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgendaMedicaColors.inkSecondary,
                )
            } else {
                state.slots.chunked(COLUNAS).forEach { linha ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        linha.forEach { slot ->
                            SlotCell(
                                slot = slot,
                                selected = slot.start == state.selectedSlot,
                                onClick = { viewModel.onSlotSelected(slot) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(COLUNAS - linha.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private fun rotuloDia(dia: LocalDate): String {
    val nome = diaSemanaDe(dia).label.take(3)
    return "$nome ${dia.format(DateTimeFormatter.ofPattern("dd/MM"))}"
}

@Composable
private fun SlotCell(slot: AgendaSlot, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hora = slot.hora.format(HORA_FORMAT)
    val enabled = slot.habilitado
    val border = when {
        selected -> BorderStroke(2.dp, AgendaMedicaColors.accentPrimary)
        enabled -> BorderStroke(1.dp, AgendaMedicaColors.borderInput)
        else -> BorderStroke(1.dp, AgendaMedicaColors.borderHairline)
    }
    val container = when {
        selected -> AgendaMedicaColors.accentPrimaryTint
        enabled -> AgendaMedicaColors.surfaceCard
        else -> AgendaMedicaColors.surfaceDisabled
    }
    val content = when {
        selected -> AgendaMedicaColors.accentPrimary
        enabled -> AgendaMedicaColors.inkPrimary
        else -> AgendaMedicaColors.inkDisabled
    }
    val description = slot.motivo?.let { "$hora, ${it.texto}" } ?: hora
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .background(container, ShapeMd)
            .border(border, ShapeMd)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected // state must not depend on color/border alone (UX-DR8)
                if (!enabled) disabled()
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(hora, style = MaterialTheme.typography.labelLarge, color = content)
        slot.motivo?.let {
            Text(it.texto, style = MaterialTheme.typography.labelSmall, color = content, textAlign = TextAlign.Center)
        }
    }
}
