package com.agendamedica.app.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendamedica.app.data.repository.AppError
import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.PatientRepository
import com.agendamedica.app.data.repository.RegisterPatientRequest
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

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

private const val EMAIL_JA_CADASTRADO = "Já existe uma conta com este e-mail."
private const val CONTA_CRIADA_SEM_LOGIN =
    "Sua conta foi criada, mas não foi possível entrar agora. Volte e faça login com seu e-mail e senha."

data class CadastroPacienteUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isSubmitEnabled: Boolean
        get() = !isLoading &&
            name.isNotBlank() &&
            EMAIL_PATTERN.matches(email.trim()) &&
            password.length >= 6
}

/** Cadastro de Paciente (FR3): register via Edge Function, then sign in and go to Busca. */
class CadastroPacienteViewModel @JvmOverloads constructor(
    private val patientRepository: PatientRepository = PatientRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CadastroPacienteUiState())
    val uiState: StateFlow<CadastroPacienteUiState> = _uiState.asStateFlow()

    private val _navigateToBusca = MutableSharedFlow<Unit>()
    val navigateToBusca: SharedFlow<Unit> = _navigateToBusca.asSharedFlow()

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value, errorMessage = null) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        if (!state.isSubmitEnabled) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            patientRepository.registerPatient(
                RegisterPatientRequest(
                    name = state.name.trim(),
                    email = state.email.trim(),
                    password = state.password,
                ),
            ).onFailure { throwable ->
                val error = throwable.toAppError()
                val message = if (error is AppError.Conflict) EMAIL_JA_CADASTRADO else error.toUserMessage()
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                return@launch
            }

            authRepository.signIn(state.email.trim(), state.password)
                .onFailure {
                    // The account exists now; retrying "Criar conta" would only hit CONFLICT, so
                    // point the user at Login instead of a generic retry.
                    _uiState.update { it.copy(isLoading = false, errorMessage = CONTA_CRIADA_SEM_LOGIN) }
                    return@launch
                }

            _uiState.update { it.copy(isLoading = false) }
            _navigateToBusca.emit(Unit)
        }
    }
}
