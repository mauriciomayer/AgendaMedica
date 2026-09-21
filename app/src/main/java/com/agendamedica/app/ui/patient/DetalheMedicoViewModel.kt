package com.agendamedica.app.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendamedica.app.data.repository.AppointmentRepository
import com.agendamedica.app.data.repository.BookedSlotChange
import com.agendamedica.app.data.repository.BookingResult
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.data.repository.RescheduleResult
import com.agendamedica.app.data.repository.toAppError
import com.agendamedica.app.data.repository.toUserMessage
import com.agendamedica.app.domain.agenda.ANTECEDENCIA_MINIMA_HORAS
import com.agendamedica.app.domain.agenda.AgendaSlot
import com.agendamedica.app.domain.agenda.JANELA_ALTERACAO_HORAS
import com.agendamedica.app.domain.agenda.DoctorDetail
import com.agendamedica.app.domain.agenda.diasCarrossel
import com.agendamedica.app.domain.agenda.fimJanela
import com.agendamedica.app.domain.agenda.inicioJanela
import com.agendamedica.app.domain.agenda.slotsDoDia
import com.agendamedica.app.domain.model.Convenio
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
const val MSG_CONFLITO = "Este horário acabou de ser reservado, escolha outro."
const val MSG_ANTECEDENCIA = "Este horário exige antecedência mínima de $ANTECEDENCIA_MINIMA_HORAS horas. Escolha outro."
const val MSG_RESERVADO_REALTIME = "O horário que você escolheu acabou de ser reservado. Escolha outro."
const val MSG_JANELA_24H = "Bloqueado: faltam menos de ${JANELA_ALTERACAO_HORAS}h — não é mais possível cancelar ou reagendar."

/** What the Confirmação screen shows after a successful booking. */
data class ConfirmacaoData(
    val doctorName: String,
    val especialidade: String,
    val start: Instant,
    val convenio: String,
)

data class DetalheMedicoUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val doctor: DoctorDetail? = null,
    val dias: List<LocalDate> = emptyList(),
    val selectedDia: LocalDate? = null,
    val slots: List<AgendaSlot> = emptyList(),
    /** Start instant of the highlighted slot. */
    val selectedSlot: Instant? = null,
    val selectedConvenio: Convenio? = null,
    val isSubmitting: Boolean = false,
    /** Booking failure or notice shown near the button; never technical text (AD-9). */
    val bookingMessage: String? = null,
    /** Set on success; the screen navigates to Confirmação and calls [DetalheMedicoViewModel.onConfirmacaoConsumida]. */
    val confirmacao: ConfirmacaoData? = null,
    /** True when reopened from Minhas Consultas to move an existing appointment (no convênio choice). */
    val reagendando: Boolean = false,
    /** Set when the appointment was moved; the screen goes back and calls [DetalheMedicoViewModel.onReagendadoConsumido]. */
    val reagendado: Boolean = false,
) {
    val podeConfirmar: Boolean
        get() = doctor != null && selectedDia != null && selectedSlot != null && (reagendando || selectedConvenio != null) && !isSubmitting
}

/** Detalhe do Médico (FR5, FR6, FR7): the doctor's next days, the 15-minute grid and booking. */
class DetalheMedicoViewModel(
    private val doctorId: String,
    private val repository: DoctorRepository = DoctorRepository(),
    private val clock: Clock = Clock.systemUTC(),
    private val appointments: AppointmentRepository = AppointmentRepository(),
    /** Non-null = reschedule mode: [confirmar] moves this appointment instead of booking. */
    private val appointmentId: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalheMedicoUiState(reagendando = appointmentId != null))
    val uiState: StateFlow<DetalheMedicoUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var observeJob: Job? = null
    private var ocupados: Set<Instant> = emptySet()

    init {
        load()
    }

    fun retry() = load()

    fun onDiaSelected(dia: LocalDate) {
        _uiState.update { it.copy(selectedDia = dia, selectedSlot = null, bookingMessage = null).redesenhado() }
    }

    fun onSlotSelected(slot: AgendaSlot) {
        if (!slot.habilitado) return
        _uiState.update { it.copy(selectedSlot = slot.start, bookingMessage = null) }
    }

    fun onConvenioSelected(convenio: Convenio) {
        _uiState.update { it.copy(selectedConvenio = convenio, bookingMessage = null) }
    }

    fun onReagendadoConsumido() {
        _uiState.update { it.copy(reagendado = false) }
    }

    fun onConfirmacaoConsumida() {
        _uiState.update { it.copy(confirmacao = null) }
    }

    /** Starts the Realtime subscription of this doctor's `booked_slots`; call when the screen is shown. */
    fun startObserving() {
        if (observeJob?.isActive == true) return
        observeJob = viewModelScope.launch {
            try {
                appointments.observeBookedSlots(doctorId).collect { onSlotChange(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Realtime only improves freshness: a conflict is still caught by the booking call.
            }
        }
    }

    /** Cancels the Realtime subscription; call when the screen is left. */
    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    override fun onCleared() {
        stopObserving()
    }

    fun confirmar() {
        val state = _uiState.value
        val doctor = state.doctor ?: return
        val start = state.selectedSlot ?: return
        if (!state.podeConfirmar) return
        _uiState.update { it.copy(isSubmitting = true, bookingMessage = null) }
        if (appointmentId != null) {
            reagendar(appointmentId, start)
            return
        }
        val convenio = state.selectedConvenio ?: return
        viewModelScope.launch {
            when (val result = appointments.bookAppointment(doctor.id, start, convenio.label)) {
                is BookingResult.Success -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        confirmacao = ConfirmacaoData(doctor.name, doctor.especialidade.label, start, convenio.label),
                    )
                }
                BookingResult.SlotTaken -> {
                    ocupados = ocupados + start
                    rejeitar(MSG_CONFLITO)
                    refetchOcupados(garantir = start)
                }
                BookingResult.LeadTime -> rejeitar(MSG_ANTECEDENCIA)
                is BookingResult.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, bookingMessage = result.error.toUserMessage())
                }
            }
        }
    }

    private fun reagendar(appointmentId: String, start: Instant) {
        viewModelScope.launch {
            when (val result = appointments.rescheduleAppointment(appointmentId, start)) {
                RescheduleResult.Success -> _uiState.update { it.copy(isSubmitting = false, reagendado = true) }
                RescheduleResult.SlotTaken -> {
                    ocupados = ocupados + start
                    rejeitar(MSG_CONFLITO)
                    refetchOcupados(garantir = start)
                }
                RescheduleResult.LeadTime -> rejeitar(MSG_ANTECEDENCIA)
                RescheduleResult.WindowClosed -> _uiState.update {
                    it.copy(isSubmitting = false, bookingMessage = MSG_JANELA_24H)
                }
                is RescheduleResult.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, bookingMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Clears the selection, redraws the grid and shows [message]. */
    private fun rejeitar(message: String) {
        _uiState.update {
            it.copy(isSubmitting = false, selectedSlot = null, bookingMessage = message).redesenhado()
        }
    }

    private fun DetalheMedicoUiState.redesenhado(): DetalheMedicoUiState {
        val doctor = doctor ?: return this
        val dia = selectedDia ?: return this
        return copy(slots = slotsDoDia(dia, doctor.schedule, ocupados, clock))
    }

    /** Re-reads `booked_slots`; [garantir] stays occupied even if the read is momentarily behind. */
    private suspend fun refetchOcupados(garantir: Instant? = null) {
        val fresh = repository.getBookedSlots(doctorId, inicioJanela(clock), fimJanela(clock)).getOrNull() ?: return
        ocupados = if (garantir != null) fresh + garantir else fresh
        _uiState.update { it.redesenhado() }
    }

    private suspend fun onSlotChange(change: BookedSlotChange) {
        when (change) {
            is BookedSlotChange.Taken -> {
                ocupados = ocupados + change.start
                _uiState.update {
                    // Our own booking's push can arrive before the RPC response: not a "lost" slot.
                    val perdeu = it.selectedSlot == change.start && !it.isSubmitting
                    it.copy(
                        selectedSlot = if (perdeu) null else it.selectedSlot,
                        bookingMessage = if (perdeu) MSG_RESERVADO_REALTIME else it.bookingMessage,
                    ).redesenhado()
                }
            }
            is BookedSlotChange.Freed -> {
                ocupados = ocupados - change.start
                _uiState.update { it.redesenhado() }
            }
            BookedSlotChange.Resync -> refetchOcupados()
        }
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
                    selectedConvenio = detail.convenios.singleOrNull(),
                    reagendando = appointmentId != null,
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
