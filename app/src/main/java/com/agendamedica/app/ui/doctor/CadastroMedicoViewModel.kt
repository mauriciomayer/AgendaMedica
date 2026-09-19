package com.agendamedica.app.ui.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.data.repository.RegisterDoctorRequest
import com.agendamedica.app.data.repository.ScheduleInput
import com.agendamedica.app.data.repository.toAppError
import com.agendamedica.app.data.repository.toUserMessage
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.DiaSemana
import com.agendamedica.app.domain.model.Especialidade
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 30-minute options for the início/fim "select nativo (dropdown)" (DESIGN.md). */
val HORARIOS_DISPONIVEIS: List<String> = buildList {
    for (hour in 6..21) {
        add("%02d:00".format(hour))
        add("%02d:30".format(hour))
    }
    add("22:00")
}

data class CadastroMedicoUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val especialidade: Especialidade? = null,
    val conveniosSelecionados: Set<Convenio> = emptySet(),
    val diasSelecionados: Set<DiaSemana> = emptySet(),
    val startTime: String = "08:00",
    val endTime: String = "18:00",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    /**
     * Mirrors the I/O matrix: name/email/password/specialty required, at least one insurance,
     * at least one day, and a start time strictly before the end time. The button stays
     * disabled — never an inline error — until every rule is satisfied (State Patterns).
     */
    val isSubmitEnabled: Boolean
        get() = !isLoading &&
            name.isNotBlank() &&
            email.isNotBlank() &&
            password.length >= 6 &&
            especialidade != null &&
            conveniosSelecionados.isNotEmpty() &&
            diasSelecionados.isNotEmpty() &&
            startTime < endTime
}

/**
 * Cadastro de Médico — full self-registration form (FR1, FR2). Submitting does two things in
 * sequence, matching the architecture split between server-side identity and client-side
 * session:
 * 1. [DoctorRepository.registerDoctor] calls the `register-doctor` Edge Function, which is
 *    the only place `role = 'doctor'` and the initial `specialty`/`insurances` are ever set
 *    (AD-6, AD-11).
 * 2. On success, [AuthRepository.signIn] establishes the session locally so the user lands
 *    authenticated in Minha Agenda with no separate login step (FR1: "fica autenticado
 *    automaticamente").
 */
class CadastroMedicoViewModel @JvmOverloads constructor(
    private val doctorRepository: DoctorRepository = DoctorRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CadastroMedicoUiState())
    val uiState: StateFlow<CadastroMedicoUiState> = _uiState.asStateFlow()

    private val _navigateToMinhaAgenda = MutableSharedFlow<Unit>()
    val navigateToMinhaAgenda: SharedFlow<Unit> = _navigateToMinhaAgenda.asSharedFlow()

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value, errorMessage = null) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onEspecialidadeSelected(value: Especialidade) = _uiState.update { it.copy(especialidade = value) }
    fun onStartTimeSelected(value: String) = _uiState.update { it.copy(startTime = value) }
    fun onEndTimeSelected(value: String) = _uiState.update { it.copy(endTime = value) }

    fun onConvenioToggled(convenio: Convenio) = _uiState.update { state ->
        state.copy(
            conveniosSelecionados = state.conveniosSelecionados.toggle(convenio),
        )
    }

    fun onDiaToggled(dia: DiaSemana) = _uiState.update { state ->
        state.copy(diasSelecionados = state.diasSelecionados.toggle(dia))
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (contains(item)) this - item else this + item

    fun submit() {
        val state = _uiState.value
        val especialidade = state.especialidade
        if (!state.isSubmitEnabled || especialidade == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val request = RegisterDoctorRequest(
                name = state.name.trim(),
                email = state.email.trim(),
                password = state.password,
                specialty = especialidade.label,
                insurances = state.conveniosSelecionados.map { it.label },
                schedules = state.diasSelecionados.map { dia ->
                    ScheduleInput(weekday = dia.isoValue, startTime = state.startTime, endTime = state.endTime)
                },
            )

            doctorRepository.registerDoctor(request)
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.toAppError().toUserMessage())
                    }
                    return@launch
                }

            authRepository.signIn(state.email.trim(), state.password)
                .onFailure { throwable ->
                    // The account now exists server-side; only the local session failed to
                    // establish. Still a generic message (AD-9) — the user can retry via Login.
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.toAppError().toUserMessage())
                    }
                    return@launch
                }

            _uiState.update { it.copy(isLoading = false) }
            _navigateToMinhaAgenda.emit(Unit)
        }
    }
}
