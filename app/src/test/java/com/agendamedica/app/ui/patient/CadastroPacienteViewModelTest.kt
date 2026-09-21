package com.agendamedica.app.ui.patient

import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.PatientRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Covers the Cadastro de Paciente rows of spec-1-2-paciente-se-cadastra.md's I/O matrix. */
@OptIn(ExperimentalCoroutinesApi::class)
class CadastroPacienteViewModelTest {

    private val patientRepository: PatientRepository = mockk()
    private val authRepository: AuthRepository = mockk()
    private lateinit var viewModel: CadastroPacienteViewModel

    // Shared between Dispatchers.Main and runTest — see CadastroMedicoViewModelTest.
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CadastroPacienteViewModel(patientRepository, authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillValidForm() {
        viewModel.onNameChanged("Maria Souza")
        viewModel.onEmailChanged("maria@example.com")
        viewModel.onPasswordChanged("senha123")
    }

    @Test
    fun `submit disabled when name is blank`() {
        viewModel.onEmailChanged("maria@example.com")
        viewModel.onPasswordChanged("senha123")
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun `submit disabled when email is malformed`() {
        viewModel.onNameChanged("Maria Souza")
        viewModel.onEmailChanged("maria@")
        viewModel.onPasswordChanged("senha123")
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun `submit disabled when password shorter than 6`() {
        viewModel.onNameChanged("Maria Souza")
        viewModel.onEmailChanged("maria@example.com")
        viewModel.onPasswordChanged("12345")
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun `submit enabled once every field is valid`() {
        fillValidForm()
        assertTrue(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun `valid submit registers, signs in and navigates to Busca`() = runTest(testDispatcher) {
        fillValidForm()
        coEvery { patientRepository.registerPatient(any()) } returns Result.success(Unit)
        coEvery { authRepository.signIn("maria@example.com", "senha123") } returns Result.success(Unit)

        var navigated = false
        backgroundScope.launch { viewModel.navigateToBusca.collect { navigated = true } }

        viewModel.submit()
        advanceUntilIdle()

        coVerify(exactly = 1) { patientRepository.registerPatient(any()) }
        coVerify(exactly = 1) { authRepository.signIn("maria@example.com", "senha123") }
        assertTrue("expected navigation to Busca", navigated)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `INVALID rejection shows a generic message and does not navigate`() = runTest(testDispatcher) {
        fillValidForm()
        coEvery { patientRepository.registerPatient(any()) } returns
            Result.failure(IllegalStateException("INVALID: nome é obrigatório"))

        var navigated = false
        backgroundScope.launch { viewModel.navigateToBusca.collect { navigated = true } }

        viewModel.submit()
        advanceUntilIdle()

        coVerify(exactly = 0) { authRepository.signIn(any(), any()) }
        assertFalse(navigated)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.errorMessage!!.contains("INVALID"))
    }

    @Test
    fun `CONFLICT shows the e-mail already registered message and does not navigate`() = runTest(testDispatcher) {
        fillValidForm()
        coEvery { patientRepository.registerPatient(any()) } returns
            Result.failure(IllegalStateException("CONFLICT: já existe uma conta com este e-mail"))

        var navigated = false
        backgroundScope.launch { viewModel.navigateToBusca.collect { navigated = true } }

        viewModel.submit()
        advanceUntilIdle()

        coVerify(exactly = 0) { authRepository.signIn(any(), any()) }
        assertFalse(navigated)
        assertEquals("Já existe uma conta com este e-mail.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `sign-in failure after registration tells the user to log in and does not navigate`() = runTest(testDispatcher) {
        fillValidForm()
        coEvery { patientRepository.registerPatient(any()) } returns Result.success(Unit)
        coEvery { authRepository.signIn(any(), any()) } returns
            Result.failure(IllegalStateException("Unable to resolve host"))

        var navigated = false
        backgroundScope.launch { viewModel.navigateToBusca.collect { navigated = true } }

        viewModel.submit()
        advanceUntilIdle()

        assertFalse(navigated)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("faça login"))
    }

    @Test
    fun `network failure shows a generic message and does not navigate`() = runTest(testDispatcher) {
        fillValidForm()
        coEvery { patientRepository.registerPatient(any()) } returns
            Result.failure(IllegalStateException("Unable to resolve host"))

        var navigated = false
        backgroundScope.launch { viewModel.navigateToBusca.collect { navigated = true } }

        viewModel.submit()
        advanceUntilIdle()

        assertFalse(navigated)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.errorMessage!!.contains("resolve host"))
    }
}
