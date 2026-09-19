package com.agendamedica.app.ui.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.data.repository.toAppError
import com.agendamedica.app.data.repository.toUserMessage
import com.agendamedica.app.domain.model.DoctorProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MinhaAgendaUiState(
    val isLoading: Boolean = true,
    val profile: DoctorProfile? = null,
    val errorMessage: String? = null,
)

/**
 * Minha Agenda — read-only for this story (EXPERIENCE.md: "hoje somente leitura"). Loads the
 * doctor's own profile via [DoctorRepository] only (AD-2); the "próximas consultas" list is a
 * fixed empty state because `appointments` doesn't exist yet (Épico 2, explicitly out of
 * scope — spec's "Never").
 */
class MinhaAgendaViewModel @JvmOverloads constructor(
    private val doctorRepository: DoctorRepository = DoctorRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MinhaAgendaUiState())
    val uiState: StateFlow<MinhaAgendaUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            doctorRepository.getMyProfile()
                .onSuccess { profile ->
                    _uiState.update { it.copy(isLoading = false, profile = profile) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.toAppError().toUserMessage())
                    }
                }
        }
    }
}
