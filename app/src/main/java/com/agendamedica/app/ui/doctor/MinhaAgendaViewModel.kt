package com.agendamedica.app.ui.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendamedica.app.data.repository.AppointmentRepository
import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.CancelResult
import com.agendamedica.app.data.repository.ConsultaDoMedico
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.data.repository.toAppError
import com.agendamedica.app.data.repository.toUserMessage
import com.agendamedica.app.domain.agenda.podeAlterarConsulta
import com.agendamedica.app.domain.model.DoctorProfile
import com.agendamedica.app.ui.components.MSG_JANELA_24H
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

const val MSG_AGENDA_VAZIA = "Nenhuma consulta agendada ainda."

/** A card of Minha Agenda; [bloqueada] = starts in less than 24h (cancel/reschedule disabled). */
data class ConsultaMedicoItem(val consulta: ConsultaDoMedico, val bloqueada: Boolean)

data class MinhaAgendaUiState(
    val isLoading: Boolean = true,
    val profile: DoctorProfile? = null,
    val errorMessage: String? = null,
    /** The doctor's own id (for Reagendar, which opens the doctor's own Detalhe). */
    val doctorId: String? = null,
    val consultasLoading: Boolean = true,
    /** Failure loading the appointments list; the profile stays visible (AD-9). */
    val consultasErro: String? = null,
    val consultas: List<ConsultaMedicoItem> = emptyList(),
    val confirmandoId: String? = null,
    val cancelandoId: String? = null,
    val actionMessage: String? = null,
)

/**
 * Minha Agenda: the doctor's profile plus the upcoming appointments with inline cancellation
 * (FR8, FR9). Data comes only from the repositories (AD-2).
 */
class MinhaAgendaViewModel @JvmOverloads constructor(
    private val doctorRepository: DoctorRepository = DoctorRepository(),
    private val appointmentRepository: AppointmentRepository = AppointmentRepository(),
    private val clock: Clock = Clock.systemUTC(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val doctorIdProvider: () -> String? = { AuthRepository().currentUserId() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(MinhaAgendaUiState())
    val uiState: StateFlow<MinhaAgendaUiState> = _uiState.asStateFlow()

    private val _navigateToLogin = MutableSharedFlow<Unit>()
    val navigateToLogin: SharedFlow<Unit> = _navigateToLogin.asSharedFlow()

    private var consultasJob: Job? = null

    init {
        load()
    }

    /** Ends the session (same pattern as `NovaSenhaViewModel`); the screen navigates to Login. */
    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            _navigateToLogin.emit(Unit)
        }
    }

    /** Full (visible) reload: profile and appointments. */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            doctorRepository.getMyProfile()
                .onSuccess { profile ->
                    _uiState.update {
                        it.copy(isLoading = false, profile = profile, doctorId = runCatching(doctorIdProvider).getOrNull())
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.toAppError().toUserMessage())
                    }
                }
        }
        loadConsultas(showSpinner = true)
    }

    fun retryConsultas() = loadConsultas(showSpinner = true)

    /** Silent re-read when the screen comes back (e.g. after Reagendar); skipped while a cancel runs. */
    fun refresh() {
        if (consultasJob?.isActive == true || _uiState.value.cancelandoId != null) return
        loadConsultas(showSpinner = false)
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
            when (val result = appointmentRepository.cancelAppointment(id)) {
                CancelResult.Success -> {
                    // A load started before the cancel would bring the card back: drop it.
                    consultasJob?.cancel()
                    _uiState.update {
                        it.copy(
                            consultasLoading = false,
                            cancelandoId = null,
                            confirmandoId = null,
                            consultas = it.consultas.filterNot { item -> item.consulta.id == id },
                        )
                    }
                }
                CancelResult.WindowClosed -> {
                    _uiState.update { it.copy(cancelandoId = null, confirmandoId = null, actionMessage = MSG_JANELA_24H) }
                    loadConsultas(showSpinner = false)
                }
                is CancelResult.Failure -> {
                    _uiState.update {
                        it.copy(cancelandoId = null, confirmandoId = null, actionMessage = result.error.toUserMessage())
                    }
                    // E.g. already cancelled by the patient: re-read so the stale card goes away.
                    loadConsultas(showSpinner = false)
                }
            }
        }
    }

    private fun loadConsultas(showSpinner: Boolean) {
        consultasJob?.cancel()
        if (showSpinner) _uiState.update { it.copy(consultasLoading = true, consultasErro = null) }
        consultasJob = viewModelScope.launch {
            val now = clock.instant()
            appointmentRepository.getDoctorUpcomingAppointments().fold(
                onSuccess = { list ->
                    val items = list.sortedBy { it.start }.map { ConsultaMedicoItem(it, !podeAlterarConsulta(it.start, now)) }
                    _uiState.update {
                        it.copy(
                            consultasLoading = false,
                            consultasErro = null,
                            consultas = items,
                            confirmandoId = it.confirmandoId?.takeIf { id -> items.any { i -> i.consulta.id == id && !i.bloqueada } },
                        )
                    }
                },
                onFailure = { t ->
                    if (t is CancellationException) return@fold
                    // A silent reload that replaced a visible load must not leave the spinner running.
                    _uiState.update {
                        if (showSpinner || it.consultasLoading) {
                            it.copy(consultasLoading = false, consultasErro = t.toAppError().toUserMessage())
                        } else {
                            it
                        }
                    }
                },
            )
        }
    }
}
