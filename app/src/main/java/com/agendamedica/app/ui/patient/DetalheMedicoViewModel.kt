package com.agendamedica.app.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.data.repository.toAppError
import com.agendamedica.app.data.repository.toUserMessage
import com.agendamedica.app.domain.agenda.AgendaSlot
import com.agendamedica.app.domain.agenda.DoctorDetail
import com.agendamedica.app.domain.agenda.diasCarrossel
import com.agendamedica.app.domain.agenda.fimJanela
import com.agendamedica.app.domain.agenda.inicioJanela
import com.agendamedica.app.domain.agenda.slotsDoDia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

const val MSG_SEM_DIAS = "Sem atendimento nos próximos dias."
const val MSG_SEM_HORARIOS = "Sem atendimento neste dia."

data class DetalheMedicoUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val doctor: DoctorDetail? = null,
    val dias: List<LocalDate> = emptyList(),
    val selectedDia: LocalDate? = null,
    val slots: List<AgendaSlot> = emptyList(),
    /** Start instant of the highlighted slot; only highlights, booking is Story 2.3. */
    val selectedSlot: Instant? = null,
)

/** Detalhe do Médico (FR5, FR6): the doctor's next days and the derived 15-minute grid. */
class DetalheMedicoViewModel(
    private val doctorId: String,
    private val repository: DoctorRepository = DoctorRepository(),
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalheMedicoUiState())
    val uiState: StateFlow<DetalheMedicoUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var ocupados: Set<Instant> = emptySet()

    init {
        load()
    }

    fun retry() = load()

    fun onDiaSelected(dia: LocalDate) {
        val doctor = _uiState.value.doctor ?: return
        _uiState.update {
            it.copy(
                selectedDia = dia,
                slots = slotsDoDia(dia, doctor.schedule, ocupados, clock),
                selectedSlot = null,
            )
        }
    }

    fun onSlotSelected(slot: AgendaSlot) {
        if (!slot.habilitado) return
        _uiState.update { it.copy(selectedSlot = slot.start) }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            val detail = repository.getDoctorDetail(doctorId).getOrElse { return@launch fail(it) }
            val booked = repository.getBookedSlots(doctorId, inicioJanela(clock), fimJanela(clock))
                .getOrElse { return@launch fail(it) }
            ocupados = booked
            _uiState.update {
                DetalheMedicoUiState(
                    isLoading = false,
                    doctor = detail,
                    dias = diasCarrossel(detail.schedule, clock),
                )
            }
        }
    }

    private fun fail(throwable: Throwable) {
        // A load cancelled by a newer one must not flash an error or end the spinner.
        if (throwable is CancellationException) return
        _uiState.update { it.copy(isLoading = false, errorMessage = throwable.toAppError().toUserMessage()) }
    }
}
