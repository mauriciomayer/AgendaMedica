package com.agendamedica.app.ui.patient

import com.agendamedica.app.data.repository.AppError
import com.agendamedica.app.data.repository.AppointmentRepository
import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.CancelResult
import com.agendamedica.app.data.repository.MinhaConsulta
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
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
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import com.agendamedica.app.ui.components.MSG_JANELA_24H

@OptIn(ExperimentalCoroutinesApi::class)
class MinhasConsultasViewModelTest {

    private val repository: AppointmentRepository = mockk()
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-09-21T13:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))

    private fun consulta(id: String, start: Instant) =
        MinhaConsulta(id, "d-$id", "Dra. $id", "Cardiologia", start, "Unimed")

    private val far = consulta("far", now.plusSeconds(72 * 3600))
    private val exact24 = consulta("exact", now.plusSeconds(24 * 3600))
    private val near = consulta("near", now.plusSeconds(24 * 3600 - 1))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.getMyUpcomingAppointments(any()) } returns Result.success(listOf(far, exact24, near))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = MinhasConsultasViewModel(repository, clock, authRepository)

    @Test
    fun `lists appointments soonest first with blocked flag under 24h`() = runTest(testDispatcher) {
        val s = vm().uiState.value
        assertFalse(s.isLoading)
        assertEquals(listOf("near", "exact", "far"), s.consultas.map { it.consulta.id })
        assertEquals(listOf(true, false, false), s.consultas.map { it.bloqueada }) // exactly 24h is allowed
    }

    @Test
    fun `empty list shows no appointments`() = runTest(testDispatcher) {
        coEvery { repository.getMyUpcomingAppointments(any()) } returns Result.success(emptyList())
        val s = vm().uiState.value
        assertTrue(s.consultas.isEmpty())
        assertNull(s.errorMessage)
        assertEquals("Você ainda não tem consultas agendadas.", MSG_SEM_CONSULTAS)
    }

    @Test
    fun `loading state while data has not arrived`() = runTest(testDispatcher) {
        coEvery { repository.getMyUpcomingAppointments(any()) } coAnswers { awaitCancellation() }
        assertTrue(vm().uiState.value.isLoading)
    }

    @Test
    fun `network failure shows generic message and retry recovers`() = runTest(testDispatcher) {
        coEvery { repository.getMyUpcomingAppointments(any()) } returns Result.failure(IOException("boom"))
        val vm = vm()
        assertEquals("Algo deu errado. Tente novamente.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoading)
        coEvery { repository.getMyUpcomingAppointments(any()) } returns Result.success(listOf(far))
        vm.retry()
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.consultas.size)
    }

    @Test
    fun `failed silent refresh keeps the list`() = runTest(testDispatcher) {
        val vm = vm()
        coEvery { repository.getMyUpcomingAppointments(any()) } returns Result.failure(IOException("boom"))
        vm.refresh()
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(3, vm.uiState.value.consultas.size)
    }

    @Test
    fun `cancel then No closes the confirmation and changes nothing`() = runTest(testDispatcher) {
        val vm = vm()
        vm.onCancelarClick("far")
        assertEquals("far", vm.uiState.value.confirmandoId)
        vm.onCancelarNao()
        assertNull(vm.uiState.value.confirmandoId)
        assertEquals(3, vm.uiState.value.consultas.size)
        coVerify(exactly = 0) { repository.cancelAppointment(any()) }
    }

    @Test
    fun `cancel then Yes removes the card`() = runTest(testDispatcher) {
        coEvery { repository.cancelAppointment("far") } returns CancelResult.Success
        val vm = vm()
        vm.onCancelarClick("far")
        vm.onCancelarSim()
        assertEquals(listOf("near", "exact"), vm.uiState.value.consultas.map { it.consulta.id })
        assertNull(vm.uiState.value.confirmandoId)
        assertNull(vm.uiState.value.cancelandoId)
    }

    @Test
    fun `blocked appointment cannot start a cancellation`() = runTest(testDispatcher) {
        val vm = vm()
        vm.onCancelarClick("near")
        assertNull(vm.uiState.value.confirmandoId)
    }

    @Test
    fun `double tap on Yes sends a single cancellation`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<CancelResult>()
        coEvery { repository.cancelAppointment("far") } coAnswers { gate.await() }
        val vm = vm()
        vm.onCancelarClick("far")
        vm.onCancelarSim()
        assertEquals("far", vm.uiState.value.cancelandoId)
        vm.onCancelarSim()
        gate.complete(CancelResult.Success)
        coVerify(exactly = 1) { repository.cancelAppointment("far") }
    }

    @Test
    fun `window closed on the server shows the note and reloads`() = runTest(testDispatcher) {
        coEvery { repository.cancelAppointment("far") } returns CancelResult.WindowClosed
        val vm = vm()
        vm.onCancelarClick("far")
        vm.onCancelarSim()
        assertEquals(MSG_JANELA_24H, vm.uiState.value.actionMessage)
        assertNull(vm.uiState.value.confirmandoId)
        assertEquals(3, vm.uiState.value.consultas.size)
        coVerify(atLeast = 2) { repository.getMyUpcomingAppointments(any()) }
    }

    @Test
    fun `a load that was in flight before the cancel cannot bring the card back`() = runTest(testDispatcher) {
        val vm = vm()
        val stale = CompletableDeferred<Result<List<MinhaConsulta>>>()
        coEvery { repository.getMyUpcomingAppointments(any()) } coAnswers { stale.await() }
        coEvery { repository.cancelAppointment("far") } returns CancelResult.Success

        vm.refresh() // snapshot requested while "far" still exists
        vm.onCancelarClick("far")
        vm.onCancelarSim()
        stale.complete(Result.success(listOf(far, exact24, near)))

        val s = vm.uiState.value
        assertFalse(s.consultas.any { it.consulta.id == "far" })
        assertFalse(s.isLoading)
        assertNull(s.cancelandoId)
    }

    @Test
    fun `refresh is skipped while a cancellation is in flight`() = runTest(testDispatcher) {
        val vm = vm()
        val pending = CompletableDeferred<CancelResult>()
        coEvery { repository.cancelAppointment("far") } coAnswers { pending.await() }

        vm.onCancelarClick("far")
        vm.onCancelarSim()
        vm.refresh()
        pending.complete(CancelResult.Success)

        coVerify(exactly = 1) { repository.getMyUpcomingAppointments(any()) } // only the initial load
        assertFalse(vm.uiState.value.consultas.any { it.consulta.id == "far" })
    }

    @Test
    fun `a rejected cancel, such as one already cancelled elsewhere, reloads and drops the stale card`() = runTest(testDispatcher) {
        val vm = vm()
        coEvery { repository.cancelAppointment("far") } returns CancelResult.Failure(AppError.Invalid("INVALID: consulta não está confirmada"))
        coEvery { repository.getMyUpcomingAppointments(any()) } returns Result.success(listOf(exact24, near))

        vm.onCancelarClick("far")
        vm.onCancelarSim()

        assertNotNullMessage(vm.uiState.value.actionMessage)
        assertFalse(vm.uiState.value.consultas.any { it.consulta.id == "far" })
        coVerify(exactly = 2) { repository.getMyUpcomingAppointments(any()) }
    }

    private fun assertNotNullMessage(message: String?) = assertTrue(message != null)

    @Test
    fun `cancel failure shows generic message and keeps the list`() = runTest(testDispatcher) {
        coEvery { repository.cancelAppointment("far") } returns CancelResult.Failure(AppError.Unexpected("boom"))
        val vm = vm()
        vm.onCancelarClick("far")
        vm.onCancelarSim()
        assertEquals("Algo deu errado. Tente novamente.", vm.uiState.value.actionMessage)
        assertEquals(3, vm.uiState.value.consultas.size)
        assertNull(vm.uiState.value.cancelandoId)
    }

    @Test
    fun `logout signs out and emits the navigate-to-login event`() = runTest(testDispatcher) {
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        val vm = vm()

        var navigated = false
        backgroundScope.launch { vm.navigateToLogin.collect { navigated = true } }

        vm.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.signOut() }
        assertTrue("logout must emit the navigation event", navigated)
    }

    @Test
    fun `logout works while the appointment list is still loading or in error`() = runTest(testDispatcher) {
        coEvery { repository.getMyUpcomingAppointments(any()) } returns Result.failure(IOException("boom"))
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        val vm = vm()
        assertNotNullMessage(vm.uiState.value.errorMessage)

        var navigated = false
        backgroundScope.launch { vm.navigateToLogin.collect { navigated = true } }

        vm.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.signOut() }
        assertTrue("logout must work regardless of the list's loading/error state", navigated)
    }

    @Test
    fun `logout navigates even when signOut fails`() = runTest(testDispatcher) {
        coEvery { authRepository.signOut() } returns Result.failure(IllegalStateException("network error"))
        val vm = vm()

        var navigated = false
        backgroundScope.launch { vm.navigateToLogin.collect { navigated = true } }

        vm.logout()
        advanceUntilIdle()

        assertTrue("logout must not check signOut()'s Result before navigating", navigated)
    }

    @Test
    fun `loading the appointment list does not sign the user out`() = runTest(testDispatcher) {
        vm()
        coVerify(exactly = 0) { authRepository.signOut() }
    }
}
