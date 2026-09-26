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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.domain.agenda.ANTECEDENCIA_MINIMA_HORAS
import com.agendamedica.app.domain.agenda.AgendaSlot
import com.agendamedica.app.domain.agenda.diaSemanaDe
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.ui.components.AppCard
import com.agendamedica.app.ui.components.Avatar
import com.agendamedica.app.ui.components.InfoBox
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.components.TagChip
import com.agendamedica.app.ui.components.TelaPadrao
import com.agendamedica.app.ui.components.ToggleChip
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.ShapeLg
import com.agendamedica.app.ui.theme.ShapeMd
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HORA_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private const val COLUNAS = 3

/** Detalhe do Médico (FR5, FR6): header, day carousel and the 45-minute slot grid. */
@Composable
fun DetalheMedicoScreen(
    doctorId: String,
    onBack: () -> Unit,
    onBooked: (ConfirmacaoData) -> Unit,
    appointmentId: String? = null,
    onRescheduled: () -> Unit = {},
) {
    val viewModel: DetalheMedicoViewModel = viewModel(
        key = "detalhe-$doctorId-${appointmentId.orEmpty()}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DetalheMedicoViewModel(doctorId, appointmentId = appointmentId) as T
        },
    )
    val state by viewModel.uiState.collectAsState()

    // Realtime lives only while this screen is shown (AD-10).
    DisposableEffect(viewModel) {
        viewModel.startObserving()
        onDispose { viewModel.stopObserving() }
    }
    LaunchedEffect(state.confirmacao) {
        state.confirmacao?.let {
            viewModel.onConfirmacaoConsumida()
            onBooked(it)
        }
    }

    LaunchedEffect(state.reagendado) {
        if (state.reagendado) {
            viewModel.onReagendadoConsumido()
            onRescheduled()
        }
    }

    DetalheConteudo(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onDiaSelected = viewModel::onDiaSelected,
        onSlotSelected = viewModel::onSlotSelected,
        onConvenioSelected = viewModel::onConvenioSelected,
        onConfirmar = viewModel::confirmar,
    )
}

/** Stateless body of Detalhe do médico (spec-8-4), so it can be tested without a ViewModel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetalheConteudo(
    state: DetalheMedicoUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDiaSelected: (LocalDate) -> Unit,
    onSlotSelected: (AgendaSlot) -> Unit,
    onConvenioSelected: (Convenio) -> Unit,
    onConfirmar: () -> Unit,
) {
    val doctor = state.doctor
    val errorMessage = state.errorMessage
    val mostrandoDetalhe = !state.isLoading && errorMessage == null && doctor != null
    TelaPadrao(
        title = if (state.reagendando) "Reagendar consulta" else "Escolher horário",
        onBack = onBack,
        espacamento = 16.dp,
        scrollable = !state.isLoading,
        rodape = if (mostrandoDetalhe) {
            {
                // The booking failure sits with the pinned button: below the fold in the scrolling body it would go unseen.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.bookingMessage?.let { InfoBox(text = it) }
                    PrimaryButton(
                    text = when {
                        state.reagendando -> if (state.isSubmitting) "Reagendando..." else "Confirmar novo horário"
                        state.isSubmitting -> "Agendando..."
                        else -> "Confirmar agendamento"
                    },
                        onClick = onConfirmar,
                        enabled = state.podeConfirmar,
                    )
                }
            }
        } else {
            null
        },
    ) {
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
            doctor != null -> {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(nome = doctor.name, tamanho = 48.dp)
                        Column {
                            Text(doctor.name, color = AgendaMedicaColors.inkPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${doctor.especialidade.label} · ${doctor.city}",
                                color = AgendaMedicaColors.inkSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                if (state.reagendando) {
                    // The convênio of a rescheduled appointment does not change; when booking, the chips below are the choice.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        doctor.convenios.forEach { TagChip(it.label) }
                    }
                }

                if (state.dias.isEmpty()) {
                    Text(MSG_SEM_DIAS, style = MaterialTheme.typography.bodyMedium, color = AgendaMedicaColors.inkSecondary)
                } else {
                    Column {
                        TituloSecao("Escolha o dia")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.dias, key = { it.toEpochDay() }) { dia ->
                                DiaCard(dia = dia, selected = dia == state.selectedDia, onClick = { onDiaSelected(dia) })
                            }
                        }
                    }
                    Column {
                        TituloSecao("Horários disponíveis")
                        when {
                            state.selectedDia == null -> Text(
                                "Selecione um dia para ver os horários.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AgendaMedicaColors.inkSecondary,
                            )
                            state.slots.isEmpty() -> Text(
                                MSG_SEM_HORARIOS,
                                color = AgendaMedicaColors.inkTertiary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                            else -> state.slots.chunked(COLUNAS).forEach { linha ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                ) {
                                    linha.forEach { slot ->
                                        SlotCell(
                                            slot = slot,
                                            selected = slot.start == state.selectedSlot,
                                            onClick = { onSlotSelected(slot) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(COLUNAS - linha.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        Text(
                            "Agendamento exige mínimo de ${ANTECEDENCIA_MINIMA_HORAS}h de antecedência.",
                            color = AgendaMedicaColors.inkTertiary,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (!state.reagendando) {
                        Column {
                            TituloSecao("Convênio")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                doctor.convenios.forEach { convenio ->
                                    ToggleChip(
                                        text = convenio.label,
                                        selected = convenio == state.selectedConvenio,
                                        onClick = { onConvenioSelected(convenio) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TituloSecao(texto: String) {
    Text(
        texto,
        color = AgendaMedicaColors.inkPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
    )
}

/** Day card of the prototype: weekday over day of the month (52dp wide), selected = 2dp accent border + tint. */
@Composable
private fun DiaCard(dia: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val borda = if (selected) BorderStroke(2.dp, AgendaMedicaColors.accentPrimary) else BorderStroke(1.dp, AgendaMedicaColors.borderInput)
    val cor = if (selected) AgendaMedicaColors.accentPrimary else AgendaMedicaColors.inkSecondary
    val descricao = "${diaSemanaDe(dia).label}, ${dia.format(DateTimeFormatter.ofPattern("dd/MM"))}"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(52.dp)
            .sizeIn(minHeight = 48.dp)
            .background(if (selected) AgendaMedicaColors.accentPrimaryTint else AgendaMedicaColors.surfaceCard, ShapeLg)
            .border(borda, ShapeLg)
            .clip(ShapeLg)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = descricao }
            .padding(vertical = 8.dp),
    ) {
        Text(diaSemanaDe(dia).label.take(3).lowercase(), color = cor, fontSize = 11.sp)
        Text(dia.format(DateTimeFormatter.ofPattern("dd")), color = cor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
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
        Text(hora, color = content, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        slot.motivo?.let {
            Text(it.texto, style = MaterialTheme.typography.labelSmall, color = content, textAlign = TextAlign.Center)
        }
    }
}
