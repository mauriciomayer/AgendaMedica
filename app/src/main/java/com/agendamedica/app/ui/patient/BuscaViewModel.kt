package com.agendamedica.app.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendamedica.app.data.location.LocationProvider
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.data.repository.toAppError
import com.agendamedica.app.data.repository.toUserMessage
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.search.DoctorSummary
import com.agendamedica.app.domain.search.GeoPoint
import com.agendamedica.app.domain.search.searchDoctors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MINHA_LOCALIZACAO = "Minha localização"
const val MSG_PERMISSAO_NEGADA = "Permissão de localização negada. Busque por cidade ou bairro."
const val MSG_SEM_POSICAO = "Não foi possível obter sua localização. Busque por cidade ou bairro."
const val MSG_SEM_RESULTADOS = "Nenhum médico encontrado com esses filtros."

data class BuscaUiState(
    /** Null means "Todas". */
    val especialidade: Especialidade? = null,
    val regionText: String = "",
    /** Non-null while GPS ordering is active. */
    val origin: GeoPoint? = null,
    val doctors: List<DoctorSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isLocating: Boolean = false,
    val errorMessage: String? = null,
    val locationNotice: String? = null,
) {
    val isGpsActive: Boolean get() = origin != null

    /** What the region text field shows: the fixed label with GPS, the typed text otherwise. */
    val regionFieldText: String get() = if (isGpsActive) MINHA_LOCALIZACAO else regionText

    /** Results after region filter/ordering (pure domain logic). */
    val results: List<DoctorSummary> get() = searchDoctors(doctors, regionText, origin)
}

/** Busca de médicos (FR4): Especialidade filter on the server, region/ordering in the domain layer. */
class BuscaViewModel(
    private val doctorRepository: DoctorRepository = DoctorRepository(),
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuscaUiState())
    val uiState: StateFlow<BuscaUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var locateJob: Job? = null

    init {
        load()
    }

    fun onEspecialidadeSelected(value: Especialidade?) {
        if (_uiState.value.especialidade == value) return
        _uiState.update { it.copy(especialidade = value) }
        load()
    }

    fun onRegionChanged(value: String) {
        // Typing wins over a GPS fix that is still in flight (it would otherwise overwrite the text).
        locateJob?.cancel()
        _uiState.update { state ->
            val text = if (state.isGpsActive) {
                if (value.length < MINHA_LOCALIZACAO.length) "" else value.removePrefix(MINHA_LOCALIZACAO)
            } else {
                value
            }
            state.copy(regionText = text, origin = null, locationNotice = null, isLocating = false)
        }
    }

    fun onPermissionDenied() {
        locateJob?.cancel()
        _uiState.update { it.copy(locationNotice = MSG_PERMISSAO_NEGADA, isLocating = false) }
    }

    /** Called once the location permission is granted (or was already). */
    fun onUseLocation() {
        if (_uiState.value.isLocating) return
        locateJob = viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, locationNotice = null) }
            val position = try {
                locationProvider.currentLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _uiState.update {
                if (position == null) {
                    it.copy(isLocating = false, locationNotice = MSG_SEM_POSICAO)
                } else {
                    it.copy(isLocating = false, origin = position, regionText = "", locationNotice = null)
                }
            }
        }
    }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val especialidade = _uiState.value.especialidade
        loadJob = viewModelScope.launch {
            doctorRepository.searchDoctors(especialidade)
                .onSuccess { list ->
                    _uiState.update { it.copy(doctors = list, isLoading = false, errorMessage = null) }
                }
                .onFailure { throwable ->
                    // A load cancelled by a newer one must not flash an error or end the spinner.
                    if (throwable is CancellationException) return@onFailure
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.toAppError().toUserMessage())
                    }
                }
        }
    }
}
