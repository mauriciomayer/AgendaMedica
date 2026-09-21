package com.agendamedica.app.ui.auth

import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.PasswordResetOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

@OptIn(ExperimentalCoroutinesApi::class)
class RecuperarSenhaViewModelTest {

    private val authRepository: AuthRepository = mockk()
    private lateinit var viewModel: RecuperarSenhaViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = RecuperarSenhaViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit disabled for empty or malformed e-mail`() {
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
        viewModel.onEmailChanged("maria@")
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
        viewModel.onEmailChanged("maria@example.com")
        assertTrue(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun `accepted request shows the neutral confirmation`() = runTest(testDispatcher) {
        viewModel.onEmailChanged("maria@example.com")
        coEvery { authRepository.requestPasswordReset("maria@example.com") } returns PasswordResetOutcome.Neutral
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.sent)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `server rejection is indistinguishable from success`() = runTest(testDispatcher) {
        // The repository maps any non rate-limit RestException to Neutral, same as success.
        viewModel.onEmailChanged("ghost@example.com")
        coEvery { authRepository.requestPasswordReset(any()) } returns PasswordResetOutcome.Neutral
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.sent)
        coVerify(exactly = 1) { authRepository.requestPasswordReset("ghost@example.com") }
    }

    @Test
    fun `rate limit shows the wait message`() = runTest(testDispatcher) {
        viewModel.onEmailChanged("maria@example.com")
        coEvery { authRepository.requestPasswordReset(any()) } returns PasswordResetOutcome.RateLimited
        viewModel.submit()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.sent)
        assertEquals(MSG_RESET_LIMITE, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `network failure shows a generic message`() = runTest(testDispatcher) {
        viewModel.onEmailChanged("maria@example.com")
        coEvery { authRepository.requestPasswordReset(any()) } returns PasswordResetOutcome.Failed
        viewModel.submit()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.sent)
        assertEquals(MSG_RESET_GENERICA, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `expired link notice is shown on request`() {
        viewModel.showExpiredLinkNotice()
        assertTrue(viewModel.uiState.value.expiredLinkBanner)
    }
}
