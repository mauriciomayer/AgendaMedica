package com.agendamedica.app.ui.doctor

import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.DiaSemana
import com.agendamedica.app.domain.model.Especialidade
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the "Cadastro de Médico" rows of spec-1-1-medico-cadastro-perfil.md's I/O matrix:
 * - "Especialidade não selecionada" -> submit button disabled
 * - "Convênio não selecionado" -> submit button disabled
 * - "Autocadastro válido" -> registerDoctor + signIn called, then navigates to Minha Agenda
 * - "Especialidade fora da lista fixa" / other INVALID -> generic error surfaced, no navigation
 * - "Falha de rede durante cadastro" -> generic error surfaced, no navigation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CadastroMedicoViewModelTest {

    private val doctorRepository: DoctorRepository = mockk()
    private val authRepository: AuthRepository = mockk()
    private lateinit var viewModel: CadastroMedicoViewModel

    // Shared with Dispatchers.Main so viewModelScope (Main) and this test's runTest/backgroundScope
    // run on the SAME TestCoroutineScheduler — two independent UnconfinedTestDispatcher instances
    // would each run their own coroutines eagerly but couldn't hand off suspension (e.g. a
    // no-replay SharedFlow's emit/collect rendezvous) to one another.
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CadastroMedicoViewModel(doctorRepository, authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillValidForm() {
        viewModel.onNameChanged("Dr. Ricardo Alves")
        viewModel.onEmailChanged("ricardo@example.com")
        viewModel.onPasswordChanged("senha123")
        viewModel.onEspecialidadeSelected(Especialidade.CARDIOLOGIA)
        viewModel.onConvenioToggled(Convenio.UNIMED)
        viewModel.onDiaToggled(DiaSemana.SEGUNDA)
        viewModel.onStartTimeSelected("08:00")
        viewModel.onEndTimeSelected("18:00")
    }

    @Test
    fun `submit disabled when especialidade not selected`() {
        viewModel.onNameChanged("Dr. Ricardo Alves")
        viewModel.onEmailChanged("ricardo@example.com")
        viewModel.onPasswordChanged("senha123")
        viewModel.onConvenioToggled(Convenio.UNIMED)
        viewModel.onDiaToggled(DiaSemana.SEGUNDA)

        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun `submit disabled when no convenio selected`() {
        viewModel.onNameChanged("Dr. Ricardo Alves")
        viewModel.onEmailChanged("ricardo@example.com")
        viewModel.onPasswordChanged("senha123")
        viewModel.onEspecialidadeSelected(Especialidade.CARDIOLOGIA)
        viewModel.onDiaToggled(DiaSemana.SEGUNDA)
        // No onConvenioToggled call at all.

        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun `submit disabled when no dia selected`() {
        viewModel.onNameChanged("Dr. Ricardo Alves")
        viewModel.onEmailChanged("ricardo@example.com")
        viewModel.onPasswordChanged("senha123")
        viewModel.onEspecialidadeSelected(Especialidade.CARDIOLOGIA)
        viewModel.onConvenioToggled(Convenio.UNIMED)
        // No onDiaToggled call at all.

        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun `submit enabled once every required field is filled`() {
        fillValidForm()
        assertTrue(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun `valid submit registers doctor, signs in, and navigates to Minha Agenda`() = runTest(testDispatcher) {
        fillValidForm()
        coEvery { doctorRepository.registerDoctor(any()) } returns Result.success(Unit)
        coEvery { authRepository.signIn("ricardo@example.com", "senha123") } returns Result.success(Unit)

        var navigated = false
        backgroundScope.launch { viewModel.navigateToMinhaAgenda.collect { navigated = true } }

        viewModel.submit()
        advanceUntilIdle()

        coVerify(exactly = 1) { doctorRepository.registerDoctor(any()) }
        coVerify(exactly = 1) { authRepository.signIn("ricardo@example.com", "senha123") }
        assertTrue("expected navigation to Minha Agenda after a successful cadastro", navigated)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `registerDoctor rejection surfaces a generic message and does not navigate`() = runTest(testDispatcher) {
        fillValidForm()
        coEvery { doctorRepository.registerDoctor(any()) } returns
            Result.failure(IllegalStateException("INVALID: especialidade inválida"))

        var navigated = false
        backgroundScope.launch { viewModel.navigateToMinhaAgenda.collect { navigated = true } }

        viewModel.submit()
        advanceUntilIdle()

        coVerify(exactly = 0) { authRepository.signIn(any(), any()) }
        assertFalse("must not navigate when registration is rejected", navigated)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(
            "error message must be generic, never the raw INVALID: prefix",
            viewModel.uiState.value.errorMessage!!.contains("INVALID"),
        )
    }

    @Test
    fun `network failure during cadastro surfaces a generic message`() = runTest(testDispatcher) {
        fillValidForm()
        coEvery { doctorRepository.registerDoctor(any()) } returns
            Result.failure(IllegalStateException("Unable to resolve host"))

        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(
            "error message must not leak the raw network exception text",
            viewModel.uiState.value.errorMessage!!.contains("resolve host"),
        )
    }
}
