package com.agendamedica.app.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendamedica.app.data.repository.AppointmentRepository
import com.agendamedica.app.data.repository.CancelResult
import com.agendamedica.app.data.repository.MinhaConsulta
import com.agendamedica.app.data.repository.toAppError
import com.agendamedica.app.data.repository.toUserMessage
import com.agendamedica.app.domain.agenda.podeAlterarConsulta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

const val MSG_SEM_CONSULTAS = "Você ainda não tem consultas agendadas."

/** A card of Minhas Consultas; [bloqueada] = starts in less than 24h (cancel/reschedule disabled). */
data class ConsultaItem(val consulta: MinhaConsulta, val bloqueada: Boolean)

data class MinhasConsultasUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val consultas: List<ConsultaItem> = emptyList(),
    /** Appointment whose inline "Cancelar?" confirmation (Sim/Não) is open. */
    val confirmandoId: String? = null,
    /** Appointment being cancelled right now (blocks a second submit). */
    val cancelandoId: String? = null,
    /** Cancellation failure shown above the list; never technical text (AD-9). */
    val actionMessage: String? = null,
)

/** Minhas Consultas (FR8, FR9): the patient's upcoming appointments with inline cancellation. */
class MinhasConsultasViewModel(
    private val repository: AppointmentRepository = AppointmentRepository(),
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MinhasConsultasUiState())
    val uiState: StateFlow<MinhasConsultasUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(showSpinner = true)
    }

    fun retry() = load(showSpinner = true)

    /** Silent re-read when the screen comes back (e.g. after rescheduling); skipped while a load runs. */
    fun refresh() {
        // A snapshot taken while a cancel is in flight could still contain the appointment.
        if (loadJob?.isActive == true || _uiState.value.cancelandoId != null) return
        load(showSpinner = false)
    }

    fun onCancelarClick(id: String) {
        val item = _uiState.value.consultas.firstOrNull { it.consulta.id == id } ?: return
        if (item.bloqueada || _uiState.value.cancelandoId != null) return
        _uiState.update { it.copy(confirmandoId = id, actionMessage = null) }
    }

    fun onCancelarNao() {
        if (_uiState.value.cancelandoId != null) return
        _uiState.update { it.copy(confirmandoId = null) }
    }

    fun onCancelarSim() {
        val state = _uiState.value
        val id = state.confirmandoId ?: return
        if (state.cancelandoId != null) return
        _uiState.update { it.copy(cancelandoId = id, actionMessage = null) }
        viewModelScope.launch {
            when (val result = repository.cancelAppointment(id)) {
                CancelResult.Success -> {
                    // A load started before the cancel would bring the cancelled card back: drop it.
                    loadJob?.cancel()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            cancelandoId = null,
                            confirmandoId = null,
                            consultas = it.consultas.filterNot { item -> item.consulta.id == id },
                        )
                    }
                }
                CancelResult.WindowClosed -> {
                    _uiState.update {
                        it.copy(cancelandoId = null, confirmandoId = null, actionMessage = MSG_JANELA_24H)
                    }
                    // The 24h boundary was crossed with the screen open: show the blocked state.
                    load(showSpinner = false)
                }
                is CancelResult.Failure -> {
                    _uiState.update {
                        it.copy(cancelandoId = null, confirmandoId = null, actionMessage = result.error.toUserMessage())
                    }
                    // E.g. already cancelled elsewhere: re-read so the stale card goes away.
                    load(showSpinner = false)
                }
            }
        }
    }

    private fun load(showSpinner: Boolean) {
        loadJob?.cancel()
        if (showSpinner) _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            val now = clock.instant()
            repository.getMyUpcomingAppointments(now).fold(
                onSuccess = { list ->
                    val items = list.sortedBy { it.start }.map { ConsultaItem(it, !podeAlterarConsulta(it.start, now)) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            consultas = items,
                            confirmandoId = it.confirmandoId?.takeIf { id -> items.any { i -> i.consulta.id == id && !i.bloqueada } },
                        )
                    }
                },
                onFailure = { t ->
                    // A load cancelled by a newer one must not flash an error.
                    if (t is CancellationException) return@fold
                    // A silent refresh keeps the current list; only a visible load shows the error.
                    if (showSpinner) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = t.toAppError().toUserMessage()) }
                    }
                },
            )
        }
    }
}
