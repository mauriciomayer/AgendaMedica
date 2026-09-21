package com.agendamedica.app.ui.patient

import com.agendamedica.app.data.location.LocationProvider
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.model.Localizacao
import com.agendamedica.app.domain.search.DoctorSummary
import com.agendamedica.app.domain.search.GeoPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BuscaViewModelTest {

    private val repository: DoctorRepository = mockk()
    private val locationProvider: LocationProvider = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun doctor(id: String, name: String, loc: Localizacao, esp: Especialidade = Especialidade.CARDIOLOGIA) =
        DoctorSummary(id, name, esp, loc.city, loc.neighborhood, loc.latitude, loc.longitude, listOf(Convenio.UNIMED))

    private val zelia = doctor("1", "Zélia", Localizacao.SP_PINHEIROS)
    private val ana = doctor("2", "Ana", Localizacao.CAMPINAS_CAMBUI)
    private val bruno = doctor("3", "Bruno", Localizacao.SANTOS_GONZAGA, Especialidade.PEDIATRIA)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.searchDoctors(null) } returns Result.success(listOf(zelia, ana, bruno))
        coEvery { repository.searchDoctors(Especialidade.CARDIOLOGIA) } returns Result.success(listOf(zelia, ana))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = BuscaViewModel(repository, locationProvider)

    @Test
    fun `no filters lists every doctor alphabetically`() = runTest(testDispatcher) {
        val state = viewModel().uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Ana", "Bruno", "Zélia"), state.results.map { it.name })
    }

    @Test
    fun `loading state is shown while the list has not arrived`() = runTest(testDispatcher) {
        coEvery { repository.searchDoctors(null) } coAnswers { awaitCancellation() }
        assertTrue(viewModel().uiState.value.isLoading)
    }

    @Test
    fun `especialidade filter queries the server and Todas clears it`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onEspecialidadeSelected(Especialidade.CARDIOLOGIA)
        assertEquals(listOf("Ana", "Zélia"), vm.uiState.value.results.map { it.name })
        coVerify { repository.searchDoctors(Especialidade.CARDIOLOGIA) }

        vm.onEspecialidadeSelected(null)
        assertEquals(3, vm.uiState.value.results.size)
    }

    @Test
    fun `gps granted orders by distance and shows Minha localizacao`() = runTest(testDispatcher) {
        coEvery { locationProvider.currentLocation() } returns
            GeoPoint(Localizacao.SANTOS_GONZAGA.latitude, Localizacao.SANTOS_GONZAGA.longitude)
        val vm = viewModel()
        vm.onUseLocation()

        val state = vm.uiState.value
        assertEquals("Minha localização", state.regionFieldText)
        assertEquals(listOf("Bruno", "Zélia", "Ana"), state.results.map { it.name })
        assertNull(state.locationNotice)
    }

    @Test
    fun `permission denied shows a message and manual search still works`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onPermissionDenied()
        assertEquals(MSG_PERMISSAO_NEGADA, vm.uiState.value.locationNotice)

        vm.onRegionChanged("campinas")
        assertEquals(listOf("Ana"), vm.uiState.value.results.map { it.name })
    }

    @Test
    fun `gps without a fix shows the message and keeps the list`() = runTest(testDispatcher) {
        coEvery { locationProvider.currentLocation() } returns null
        val vm = viewModel()
        vm.onUseLocation()

        assertEquals(MSG_SEM_POSICAO, vm.uiState.value.locationNotice)
        assertFalse(vm.uiState.value.isGpsActive)
        assertEquals(3, vm.uiState.value.results.size)
    }

    @Test
    fun `typing after gps returns to manual mode`() = runTest(testDispatcher) {
        coEvery { locationProvider.currentLocation() } returns GeoPoint(0.0, 0.0)
        val vm = viewModel()
        vm.onUseLocation()
        assertTrue(vm.uiState.value.isGpsActive)

        vm.onRegionChanged("Minha localizaçã")
        assertFalse(vm.uiState.value.isGpsActive)
        assertEquals("", vm.uiState.value.regionFieldText)
    }

    @Test
    fun `typing an extra character in gps mode keeps only the new text as the region`() = runTest(testDispatcher) {
        coEvery { locationProvider.currentLocation() } returns GeoPoint(0.0, 0.0)
        val vm = viewModel()
        vm.onUseLocation()

        vm.onRegionChanged("Minha localizaçãoX")
        assertFalse(vm.uiState.value.isGpsActive)
        assertEquals("X", vm.uiState.value.regionFieldText)
    }

    @Test
    fun `a gps fix that arrives after the user typed does not overwrite the typed text`() = runTest(testDispatcher) {
        val fix = CompletableDeferred<GeoPoint?>()
        coEvery { locationProvider.currentLocation() } coAnswers { fix.await() }
        val vm = viewModel()
        vm.onUseLocation()
        assertTrue(vm.uiState.value.isLocating)

        vm.onRegionChanged("campinas")
        fix.complete(GeoPoint(0.0, 0.0))

        assertFalse(vm.uiState.value.isGpsActive)
        assertFalse(vm.uiState.value.isLocating)
        assertEquals("campinas", vm.uiState.value.regionFieldText)
        assertEquals(listOf("Ana"), vm.uiState.value.results.map { it.name })
    }

    @Test
    fun `a cancelled load does not surface an error`() = runTest(testDispatcher) {
        coEvery { repository.searchDoctors(null) } returns Result.failure(CancellationException("superseded"))
        val vm = viewModel()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `manual region search without accents orders by name`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onRegionChanged("PINHEIROS")
        assertEquals(listOf("Zélia"), vm.uiState.value.results.map { it.name })
        vm.onRegionChanged("sao paulo")
        assertEquals(listOf("Zélia"), vm.uiState.value.results.map { it.name })
    }

    @Test
    fun `no match yields an empty result list`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onRegionChanged("curitiba")
        assertTrue(vm.uiState.value.results.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `network failure shows a generic message and retry recovers`() = runTest(testDispatcher) {
        coEvery { repository.searchDoctors(null) } returns Result.failure(IOException("Unable to resolve host"))
        val vm = viewModel()
        assertEquals("Algo deu errado. Tente novamente.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoading)

        coEvery { repository.searchDoctors(null) } returns Result.success(listOf(ana))
        vm.retry()
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(listOf("Ana"), vm.uiState.value.results.map { it.name })
    }
}
