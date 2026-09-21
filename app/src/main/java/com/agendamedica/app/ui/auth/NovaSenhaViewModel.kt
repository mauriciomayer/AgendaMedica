package com.agendamedica.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendamedica.app.data.repository.AuthRepository
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

data class NovaSenhaUiState(
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isSubmitEnabled: Boolean
        get() = !isLoading && password.length >= 6
}

/** Nova Senha: saves the password, ends the recovery session, then goes to Login (never stays signed in). */
class NovaSenhaViewModel @JvmOverloads constructor(
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(NovaSenhaUiState())
    val uiState: StateFlow<NovaSenhaUiState> = _uiState.asStateFlow()

    /** Emits true when the password was changed (Login shows the success notice), false on plain exit. */
    private val _navigateToLogin = MutableSharedFlow<Boolean>()
    val navigateToLogin: SharedFlow<Boolean> = _navigateToLogin.asSharedFlow()

    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        if (!state.isSubmitEnabled) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.updatePassword(state.password)
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.toAppError().toUserMessage())
                    }
                    return@launch
                }
            authRepository.signOut()
            _uiState.update { it.copy(isLoading = false) }
            _navigateToLogin.emit(true)
        }
    }

    /** Leaving without saving also ends the recovery session. */
    fun onBack() {
        viewModelScope.launch {
            authRepository.signOut()
            _navigateToLogin.emit(false)
        }
    }
}
