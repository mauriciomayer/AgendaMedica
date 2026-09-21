package com.agendamedica.app.ui.auth

import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.DoctorRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the Login rows of spec-1-1-medico-cadastro-perfil.md's I/O matrix:
 * - "Login com credenciais corretas" -> authenticated, navigates to Minha Agenda
 * - "Login com credenciais erradas" -> generic error, no technical detail, no navigation
 * - "Falha de rede durante ... login" -> generic error, no navigation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val authRepository: AuthRepository = mockk()
    private val doctorRepository: DoctorRepository = mockk()
    private lateinit var viewModel: LoginViewModel

    // Shared with Dispatchers.Main so viewModelScope (Main) and this test's runTest/backgroundScope
    // run on the SAME TestCoroutineScheduler — see CadastroMedicoViewModelTest for why two
    // independent UnconfinedTestDispatcher instances would not hand off emit/collect correctly.
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LoginViewModel(authRepository, doctorRepository)
        viewModel.onRoleSelected(LoginRole.MEDICO)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit disabled when email or password is blank`() {
        viewModel.onEmailChanged("medico@example.com")
        // Password left blank.
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun `switching role preserves fields already typed in the other tab`() {
        viewModel.onEmailChanged("medico@example.com")
        viewModel.onRoleSelected(LoginRole.PACIENTE)
        viewModel.onEmailChanged("paciente@example.com")
        viewModel.onRoleSelected(LoginRole.MEDICO)

        assertTrue(viewModel.uiState.value.medicoFields.email == "medico@example.com")
    }

    @Test
    fun `correct credentials authenticate and navigate to Minha Agenda`() = runTest(testDispatcher) {
        viewModel.onEmailChanged("medico@example.com")
        viewModel.onPasswordChanged("senha123")
        coEvery { authRepository.signIn("medico@example.com", "senha123") } returns Result.success(Unit)
        coEvery { doctorRepository.isCurrentUserDoctor() } returns Result.success(true)

        var navigated = false
        backgroundScope.launch { viewModel.navigateToMinhaAgenda.collect { navigated = true } }

        viewModel.submit()
        advanceUntilIdle()

        assertTrue("expected navigation to Minha Agenda after a correct login", navigated)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `patient credentials authenticate and navigate to Busca`() = runTest(testDispatcher) {
        viewModel.onRoleSelected(LoginRole.PACIENTE)
        viewModel.onEmailChanged("paciente@example.com")
        viewModel.onPasswordChanged("senha123")
        coEvery { authRepository.signIn("paciente@example.com", "senha123") } returns Result.success(Unit)
        coEvery { doctorRepository.isCurrentUserDoctor() } returns Result.success(false)

        var navigatedToBusca = false
        var navigatedToAgenda = false
        backgroundScope.launch { viewModel.navigateToBusca.collect { navigatedToBusca = true } }
        backgroundScope.launch { viewModel.navigateToMinhaAgenda.collect { navigatedToAgenda = true } }

        viewModel.submit()
        advanceUntilIdle()

        assertTrue("expected navigation to Busca for a patient", navigatedToBusca)
        assertFalse(navigatedToAgenda)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `wrong credentials show a generic error and do not navigate`() = runTest(testDispatcher) {
        viewModel.onEmailChanged("medico@example.com")
        viewModel.onPasswordChanged("senhaerrada")
        // Supabase Auth doesn't distinguish "wrong password" from "no such account" (AD-9) —
        // both surface as the same opaque failure.
        coEvery { authRepository.signIn("medico@example.com", "senhaerrada") } returns
            Result.failure(IllegalStateException("Invalid login credentials"))

        var navigated = false
        backgroundScope.launch { viewModel.navigateToMinhaAgenda.collect { navigated = true } }

        viewModel.submit()
        advanceUntilIdle()

        coVerify(exactly = 0) { doctorRepository.isCurrentUserDoctor() }
        assertFalse("must not navigate on failed login", navigated)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(
            "error message must not leak Supabase's raw wording",
            viewModel.uiState.value.errorMessage!!.contains("Invalid login credentials"),
        )
    }

    @Test
    fun `network failure during login shows a generic error`() = runTest(testDispatcher) {
        viewModel.onEmailChanged("medico@example.com")
        viewModel.onPasswordChanged("senha123")
        coEvery { authRepository.signIn(any(), any()) } returns
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
