package com.agendamedica.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.data.repository.toAppError
import com.agendamedica.app.data.repository.toUserMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MSG_SENHA_REDEFINIDA = "Senha redefinida. Entre com a nova senha."

/** Which login form is currently shown — "tabs Paciente/Médico" (Code Map). */
enum class LoginRole { PACIENTE, MEDICO }

data class LoginFieldsState(val email: String = "", val password: String = "")

data class LoginUiState(
    val selectedRole: LoginRole = LoginRole.MEDICO,
    val pacienteFields: LoginFieldsState = LoginFieldsState(),
    val medicoFields: LoginFieldsState = LoginFieldsState(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val activeFields: LoginFieldsState
        get() = if (selectedRole == LoginRole.PACIENTE) pacienteFields else medicoFields

    val isSubmitEnabled: Boolean
        get() = !isLoading && activeFields.email.isNotBlank() && activeFields.password.isNotBlank()
}

/**
 * Login screen state/logic. Talks only to [AuthRepository] (sign-in/session) and
 * [DoctorRepository] (role lookup right after sign-in, to decide routing) — never to
 * supabase-kt directly (AD-2).
 *
 * Switching tabs preserves whatever was typed in the other tab (EXPERIENCE.md, Component
 * Patterns: "não muda dados já digitados no outro papel").
 */
class LoginViewModel @JvmOverloads constructor(
    private val authRepository: AuthRepository = AuthRepository(),
    private val doctorRepository: DoctorRepository = DoctorRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _navigateToMinhaAgenda = MutableSharedFlow<Unit>()
    val navigateToMinhaAgenda: SharedFlow<Unit> = _navigateToMinhaAgenda.asSharedFlow()

    private val _navigateToBusca = MutableSharedFlow<Unit>()
    val navigateToBusca: SharedFlow<Unit> = _navigateToBusca.asSharedFlow()

    fun showPasswordResetNotice() {
        _uiState.update { it.copy(infoMessage = MSG_SENHA_REDEFINIDA) }
    }

    fun onRoleSelected(role: LoginRole) {
        _uiState.update { it.copy(selectedRole = role, errorMessage = null) }
    }

    fun onEmailChanged(value: String) {
        _uiState.update { it.withActiveFields { fields -> fields.copy(email = value) } }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.withActiveFields { fields -> fields.copy(password = value) } }
    }

    private fun LoginUiState.withActiveFields(transform: (LoginFieldsState) -> LoginFieldsState): LoginUiState =
        when (selectedRole) {
            LoginRole.PACIENTE -> copy(pacienteFields = transform(pacienteFields))
            LoginRole.MEDICO -> copy(medicoFields = transform(medicoFields))
        }

    fun submit() {
        val state = _uiState.value
        if (!state.isSubmitEnabled) return
        val fields = state.activeFields

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }

            // I/O matrix: wrong credentials show a generic, non-technical message — Supabase
            // Auth itself doesn't distinguish "wrong password" from "no such account", so
            // neither does this screen (AD-9: never surface raw/inferred infra detail).
            authRepository.signIn(fields.email.trim(), fields.password)
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.toAppError().toUserMessage())
                    }
                    return@launch
                }

            val isDoctor = doctorRepository.isCurrentUserDoctor().getOrElse { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = throwable.toAppError().toUserMessage())
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = false) }

            if (isDoctor) {
                _navigateToMinhaAgenda.emit(Unit)
            } else {
                _navigateToBusca.emit(Unit)
            }
        }
    }
}
