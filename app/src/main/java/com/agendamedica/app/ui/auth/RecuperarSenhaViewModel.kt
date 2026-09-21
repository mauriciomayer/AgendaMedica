package com.agendamedica.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.PasswordResetOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

const val MSG_RESET_NEUTRA = "Se o e-mail informado existir, enviamos um link para redefinir a senha."
const val MSG_RESET_LIMITE = "Muitas tentativas. Aguarde alguns minutos e tente novamente."
const val MSG_RESET_GENERICA = "Algo deu errado. Tente novamente."
const val MSG_LINK_EXPIRADO = "Este link expirou ou já foi usado. Solicite um novo link."

data class RecuperarSenhaUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    /** True once the neutral confirmation is shown. */
    val sent: Boolean = false,
    val errorMessage: String? = null,
    val expiredLinkBanner: Boolean = false,
) {
    val isSubmitEnabled: Boolean
        get() = !isLoading && EMAIL_PATTERN.matches(email.trim())
}

/** Recuperar Senha (FR11): always the same neutral confirmation, whether or not the account exists. */
class RecuperarSenhaViewModel @JvmOverloads constructor(
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecuperarSenhaUiState())
    val uiState: StateFlow<RecuperarSenhaUiState> = _uiState.asStateFlow()

    fun showExpiredLinkNotice() = _uiState.update { it.copy(expiredLinkBanner = true) }

    fun onEmailChanged(value: String) =
        _uiState.update { it.copy(email = value, errorMessage = null, sent = false) }

    fun submit() {
        val state = _uiState.value
        if (!state.isSubmitEnabled) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, expiredLinkBanner = false) }
            when (authRepository.requestPasswordReset(state.email.trim())) {
                PasswordResetOutcome.Neutral ->
                    _uiState.update { it.copy(isLoading = false, sent = true) }
                PasswordResetOutcome.RateLimited ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = MSG_RESET_LIMITE) }
                PasswordResetOutcome.Failed ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = MSG_RESET_GENERICA) }
            }
        }
    }
}
