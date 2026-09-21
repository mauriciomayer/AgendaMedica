package com.agendamedica.app.ui.auth

import com.agendamedica.app.data.repository.AuthRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class NovaSenhaViewModelTest {

    private val authRepository: AuthRepository = mockk()
    private lateinit var viewModel: NovaSenhaViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = NovaSenhaViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit disabled below 6 characters and enabled from 6`() {
        viewModel.onPasswordChanged("12345")
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
        viewModel.onPasswordChanged("123456")
        assertTrue(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun `success signs out and navigates to Login with notice`() = runTest(testDispatcher) {
        viewModel.onPasswordChanged("novasenha")
        coEvery { authRepository.updatePassword("novasenha") } returns Result.success(Unit)
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        val events = mutableListOf<Boolean>()
        backgroundScope.launch { viewModel.navigateToLogin.collect { events.add(it) } }

        viewModel.submit()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.signOut() }
        assertEquals(listOf(true), events)
    }

    @Test
    fun `server refusal shows generic message and stays`() = runTest(testDispatcher) {
        viewModel.onPasswordChanged("novasenha")
        coEvery { authRepository.updatePassword(any()) } returns
            Result.failure(IllegalStateException("New password should be different from the old password."))
        val events = mutableListOf<Boolean>()
        backgroundScope.launch { viewModel.navigateToLogin.collect { events.add(it) } }

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        coVerify(exactly = 0) { authRepository.signOut() }
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.errorMessage!!.contains("different"))
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `back signs out and navigates to Login without notice`() = runTest(testDispatcher) {
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        val events = mutableListOf<Boolean>()
        backgroundScope.launch { viewModel.navigateToLogin.collect { events.add(it) } }

        viewModel.onBack()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.signOut() }
        assertEquals(listOf(false), events)
    }
}
