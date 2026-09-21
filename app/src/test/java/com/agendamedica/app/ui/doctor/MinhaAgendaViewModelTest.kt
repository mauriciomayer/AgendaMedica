package com.agendamedica.app.ui.doctor

import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.domain.model.DoctorProfile
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.data.repository.AppError
import com.agendamedica.app.data.repository.AppointmentRepository
import com.agendamedica.app.data.repository.CancelResult
import com.agendamedica.app.data.repository.ConsultaDoMedico
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import com.agendamedica.app.ui.patient.MSG_JANELA_24H
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Covers Minha Agenda's loading/success/error branching (flagged as untested by this story's
 * own Reviewer Gate — the first screen every médico lands on after Story 1.1's cadastro flow).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MinhaAgendaViewModelTest {

    private val doctorRepository: DoctorRepository = mockk()
    private val appointments: AppointmentRepository = mockk()
    private val now = Instant.parse("2026-09-21T13:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))

    private fun consulta(id: String, start: Instant) = ConsultaDoMedico(id, "Paciente $id", start, "Unimed")
    private val far = consulta("far", now.plusSeconds(72 * 3600))
    private val exact24 = consulta("exact", now.plusSeconds(24 * 3600))
    private val near = consulta("near", now.plusSeconds(24 * 3600 - 1))
    private val profile = DoctorProfile("Dr. Ricardo Alves", Especialidade.CARDIOLOGIA, emptyList(), emptyList())

    private fun vm() = MinhaAgendaViewModel(doctorRepository, appointments, clock) { "doc-1" }

    // Shared with Dispatchers.Main — see CadastroMedicoViewModelTest for why an independent
    // UnconfinedTestDispatcher per side would not hand off correctly (not needed here since
    // this ViewModel has no SharedFlow, but kept consistent with the other two ViewModel tests).
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { doctorRepository.getMyProfile() } returns Result.success(profile)
        coEvery { appointments.getDoctorUpcomingAppointments() } returns Result.success(listOf(far, exact24, near))
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

        val viewModel = vm()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(profile, viewModel.uiState.value.profile)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `failed load surfaces a generic message and clears loading`() = runTest(testDispatcher) {
        coEvery { doctorRepository.getMyProfile() } returns
            Result.failure(IllegalStateException("Unable to resolve host"))

        val viewModel = vm()

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
        val viewModel = vm()

        viewModel.load()

        io.mockk.coVerify(exactly = 2) { doctorRepository.getMyProfile() } // init{} + explicit reload
    }

    @Test
    fun `lists appointments soonest first with blocked flag under 24h`() = runTest(testDispatcher) {
        val s = vm().uiState.value
        assertFalse(s.consultasLoading)
        assertEquals(listOf("near", "exact", "far"), s.consultas.map { it.consulta.id })
        assertEquals(listOf(true, false, false), s.consultas.map { it.bloqueada })
        assertEquals("doc-1", s.doctorId)
    }

    @Test
    fun `empty list shows no appointments`() = runTest(testDispatcher) {
        coEvery { appointments.getDoctorUpcomingAppointments() } returns Result.success(emptyList())
        val s = vm().uiState.value
        assertTrue(s.consultas.isEmpty())
        assertNull(s.consultasErro)
    }

    @Test
    fun `list failure keeps the profile visible with a generic message`() = runTest(testDispatcher) {
        coEvery { appointments.getDoctorUpcomingAppointments() } returns Result.failure(IOException("Unable to resolve host"))
        val viewModel = vm()
        val s = viewModel.uiState.value
        assertEquals(profile, s.profile)
        assertNull(s.errorMessage)
        assertNotNull(s.consultasErro)
        assertFalse(s.consultasErro!!.contains("resolve host"))

        coEvery { appointments.getDoctorUpcomingAppointments() } returns Result.success(listOf(far))
        viewModel.retryConsultas()
        assertNull(viewModel.uiState.value.consultasErro)
        assertEquals(1, viewModel.uiState.value.consultas.size)
    }

    @Test
    fun `cancel opens confirmation, Nao closes it and changes nothing`() = runTest(testDispatcher) {
        val viewModel = vm()
        viewModel.onCancelarClick("far")
        assertEquals("far", viewModel.uiState.value.confirmandoId)
        viewModel.onCancelarNao()
        assertNull(viewModel.uiState.value.confirmandoId)
        assertEquals(3, viewModel.uiState.value.consultas.size)
        coVerify(exactly = 0) { appointments.cancelAppointment(any()) }
    }

    @Test
    fun `cancel is not offered for a blocked appointment`() = runTest(testDispatcher) {
        val viewModel = vm()
        viewModel.onCancelarClick("near")
        assertNull(viewModel.uiState.value.confirmandoId)
    }

    @Test
    fun `exactly 24h can be cancelled and Sim removes the card`() = runTest(testDispatcher) {
        coEvery { appointments.cancelAppointment("exact") } returns CancelResult.Success
        val viewModel = vm()
        viewModel.onCancelarClick("exact")
        viewModel.onCancelarSim()
        val s = viewModel.uiState.value
        assertEquals(listOf("near", "far"), s.consultas.map { it.consulta.id })
        assertNull(s.confirmandoId)
        assertNull(s.cancelandoId)
        coVerify(exactly = 1) { appointments.cancelAppointment("exact") }
    }

    @Test
    fun `double Sim submits a single cancellation`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<CancelResult>()
        coEvery { appointments.cancelAppointment("far") } coAnswers { gate.await() }
        val viewModel = vm()
        viewModel.onCancelarClick("far")
        viewModel.onCancelarSim()
        assertEquals("far", viewModel.uiState.value.cancelandoId)
        viewModel.onCancelarSim()
        gate.complete(CancelResult.Success)
        coVerify(exactly = 1) { appointments.cancelAppointment("far") }
    }

    @Test
    fun `a list load in flight before the cancel cannot bring the card back`() = runTest(testDispatcher) {
        val viewModel = vm()
        val stale = CompletableDeferred<Result<List<ConsultaDoMedico>>>()
        coEvery { appointments.getDoctorUpcomingAppointments() } coAnswers { stale.await() }
        coEvery { appointments.cancelAppointment("far") } returns CancelResult.Success

        viewModel.refresh() // snapshot requested while "far" still exists
        viewModel.onCancelarClick("far")
        viewModel.onCancelarSim()
        stale.complete(Result.success(listOf(far, exact24, near)))

        val s = viewModel.uiState.value
        assertFalse(s.consultas.any { it.consulta.id == "far" })
        assertNull(s.cancelandoId)
    }

    @Test
    fun `refresh is skipped while a cancellation is in flight`() = runTest(testDispatcher) {
        val viewModel = vm()
        val pending = CompletableDeferred<CancelResult>()
        coEvery { appointments.cancelAppointment("far") } coAnswers { pending.await() }

        viewModel.onCancelarClick("far")
        viewModel.onCancelarSim()
        viewModel.refresh()
        pending.complete(CancelResult.Success)

        coVerify(exactly = 1) { appointments.getDoctorUpcomingAppointments() } // only the initial load
        assertFalse(viewModel.uiState.value.consultas.any { it.consulta.id == "far" })
    }

    @Test
    fun `an open confirmation is closed when the reload finds the appointment gone`() = runTest(testDispatcher) {
        val viewModel = vm()
        viewModel.onCancelarClick("far")
        assertEquals("far", viewModel.uiState.value.confirmandoId)

        coEvery { appointments.getDoctorUpcomingAppointments() } returns Result.success(listOf(exact24, near))
        viewModel.refresh()

        assertNull(viewModel.uiState.value.confirmandoId)
    }

    @Test
    fun `a silent reload that fails cannot leave the list spinner running`() = runTest(testDispatcher) {
        val viewModel = vm()
        val slow = CompletableDeferred<Result<List<ConsultaDoMedico>>>()
        coEvery { appointments.getDoctorUpcomingAppointments() } coAnswers { slow.await() }
        viewModel.retryConsultas() // visible load, still running
        assertTrue(viewModel.uiState.value.consultasLoading)

        coEvery { appointments.getDoctorUpcomingAppointments() } returns Result.failure(IOException("boom"))
        viewModel.load() // silent-style reload that supersedes it and fails

        assertFalse(viewModel.uiState.value.consultasLoading)
    }

    @Test
    fun `cancel window closed shows the 24h note and reloads`() = runTest(testDispatcher) {
        coEvery { appointments.cancelAppointment("far") } returns CancelResult.WindowClosed
        val viewModel = vm()
        viewModel.onCancelarClick("far")
        viewModel.onCancelarSim()
        assertEquals(MSG_JANELA_24H, viewModel.uiState.value.actionMessage)
        assertNull(viewModel.uiState.value.confirmandoId)
        coVerify(atLeast = 2) { appointments.getDoctorUpcomingAppointments() }
    }

    @Test
    fun `cancel failure (already cancelled) shows a generic message and reloads`() = runTest(testDispatcher) {
        coEvery { appointments.cancelAppointment("far") } returns CancelResult.Failure(AppError.Invalid("INVALID: x"))
        coEvery { appointments.getDoctorUpcomingAppointments() } returnsMany
            listOf(Result.success(listOf(far)), Result.success(emptyList()))
        val viewModel = vm()
        viewModel.onCancelarClick("far")
        viewModel.onCancelarSim()
        val s = viewModel.uiState.value
        assertNotNull(s.actionMessage)
        assertFalse(s.actionMessage!!.contains("INVALID"))
        assertTrue(s.consultas.isEmpty())
    }

    @Test
    fun `refresh re-reads the list silently`() = runTest(testDispatcher) {
        val viewModel = vm()
        coEvery { appointments.getDoctorUpcomingAppointments() } returns Result.success(listOf(far))
        viewModel.refresh()
        assertEquals(listOf("far"), viewModel.uiState.value.consultas.map { it.consulta.id })
        assertFalse(viewModel.uiState.value.consultasLoading)
    }
}
