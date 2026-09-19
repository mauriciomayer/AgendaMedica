package com.agendamedica.app.ui.doctor

import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.domain.model.DoctorProfile
import com.agendamedica.app.domain.model.Especialidade
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Covers Minha Agenda's loading/success/error branching (flagged as untested by this story's
 * own Reviewer Gate — the first screen every médico lands on after Story 1.1's cadastro flow).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MinhaAgendaViewModelTest {

    private val doctorRepository: DoctorRepository = mockk()

    // Shared with Dispatchers.Main — see CadastroMedicoViewModelTest for why an independent
    // UnconfinedTestDispatcher per side would not hand off correctly (not needed here since
    // this ViewModel has no SharedFlow, but kept consistent with the other two ViewModel tests).
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful load populates profile and clears loading`() = runTest(testDispatcher) {
        val profile = DoctorProfile(
            name = "Dr. Ricardo Alves",
            especialidade = Especialidade.CARDIOLOGIA,
            convenios = emptyList(),
            schedule = emptyList(),
        )
        coEvery { doctorRepository.getMyProfile() } returns Result.success(profile)

        val viewModel = MinhaAgendaViewModel(doctorRepository)

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(profile, viewModel.uiState.value.profile)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `failed load surfaces a generic message and clears loading`() = runTest(testDispatcher) {
        coEvery { doctorRepository.getMyProfile() } returns
            Result.failure(IllegalStateException("Unable to resolve host"))

        val viewModel = MinhaAgendaViewModel(doctorRepository)

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.profile)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(
            "error message must not leak the raw network exception text",
            viewModel.uiState.value.errorMessage!!.contains("resolve host"),
        )
    }

    @Test
    fun `reload re-fetches the profile`() = runTest(testDispatcher) {
        val profile = DoctorProfile(
            name = "Dr. Ricardo Alves",
            especialidade = Especialidade.CARDIOLOGIA,
            convenios = emptyList(),
            schedule = emptyList(),
        )
        coEvery { doctorRepository.getMyProfile() } returns Result.success(profile)
        val viewModel = MinhaAgendaViewModel(doctorRepository)

        viewModel.load()

        io.mockk.coVerify(exactly = 2) { doctorRepository.getMyProfile() } // init{} + explicit reload
    }
}
