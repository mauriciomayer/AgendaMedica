package com.agendamedica.app.ui.patient

import com.agendamedica.app.data.repository.AppError
import com.agendamedica.app.data.repository.AppointmentRepository
import com.agendamedica.app.data.repository.BookedSlotChange
import com.agendamedica.app.data.repository.BookingResult
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.data.repository.RescheduleResult
import com.agendamedica.app.domain.agenda.DoctorDetail
import com.agendamedica.app.domain.agenda.MOTIVO_ANTECEDENCIA
import com.agendamedica.app.domain.agenda.SlotMotivo
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.DiaSemana
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.model.ScheduleBlock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DetalheMedicoViewModelTest {

    private val repository: DoctorRepository = mockk()
    private val appointments: AppointmentRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    // Monday 2026-09-21 10:00 in Sao Paulo (13:00 UTC); device zone deliberately different.
    private val clock = Clock.fixed(Instant.parse("2026-09-21T13:00:00Z"), ZoneId.of("Asia/Tokyo"))

    private fun detail(schedule: List<ScheduleBlock>) = DoctorDetail(
        "d1", "Dra. Ana", Especialidade.CARDIOLOGIA, "São Paulo", "Centro", listOf(Convenio.UNIMED), schedule,
    )

    // 08:00-10:00 gives 3 grid positions (08:00, 08:45, 09:30) under the 45-min grid (Story 5.1).
    private val monWedFri = listOf(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA)
        .map { ScheduleBlock(it, LocalTime.of(8, 0), LocalTime.of(10, 0)) }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.getDoctorDetail("d1") } returns Result.success(detail(monWedFri))
        coEvery { repository.getBookedSlots("d1", any(), any()) } returns Result.success(emptySet())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = DetalheMedicoViewModel("d1", repository, clock, appointments)

    @Test
    fun `opens with header, working days and no selection`() = runTest(testDispatcher) {
        val s = vm().uiState.value
        assertFalse(s.isLoading)
        assertEquals("Dra. Ana", s.doctor?.name)
        assertEquals(5, s.dias.size)
        assertTrue(s.dias.all { it.dayOfWeek.value in setOf(1, 3, 5) })
        assertNull(s.selectedDia)
        assertNull(s.selectedSlot)
    }

    @Test
    fun `loading state while data has not arrived`() = runTest(testDispatcher) {
        coEvery { repository.getDoctorDetail("d1") } coAnswers { awaitCancellation() }
        assertTrue(vm().uiState.value.isLoading)
    }

    @Test
    fun `selecting a day shows 45 minute grid slots with reasons`() = runTest(testDispatcher) {
        val vm = vm()
        vm.onDiaSelected(LocalDate.of(2026, 9, 21)) // today: under 48h
        val slots = vm.uiState.value.slots
        assertEquals(3, slots.size)
        assertTrue(slots.all { it.motivo == SlotMotivo.ANTECEDENCIA })
        assertEquals(MOTIVO_ANTECEDENCIA, slots.first().motivo?.texto)
        vm.onDiaSelected(LocalDate.of(2026, 9, 30))
        assertTrue(vm.uiState.value.slots.all { it.habilitado })
    }

    @Test
    fun `booked slot is Ocupado even if also under 48h`() = runTest(testDispatcher) {
        coEvery { repository.getBookedSlots("d1", any(), any()) } returns
            Result.success(setOf(Instant.parse("2026-09-21T11:00:00Z"))) // 08:00 SP
        val vm = vm()
        vm.onDiaSelected(LocalDate.of(2026, 9, 21))
        assertEquals(SlotMotivo.OCUPADO, vm.uiState.value.slots.first().motivo)
        assertEquals(SlotMotivo.ANTECEDENCIA, vm.uiState.value.slots[1].motivo)
    }

    @Test
    fun `enabled slot is highlighted, disabled ignored, changing day clears it`() = runTest(testDispatcher) {
        val vm = vm()
        vm.onDiaSelected(LocalDate.of(2026, 9, 21))
        vm.onSlotSelected(vm.uiState.value.slots.first())
        assertNull(vm.uiState.value.selectedSlot)

        vm.onDiaSelected(LocalDate.of(2026, 9, 30))
        val slot = vm.uiState.value.slots.first()
        vm.onSlotSelected(slot)
        assertEquals(slot.start, vm.uiState.value.selectedSlot)

        vm.onDiaSelected(LocalDate.of(2026, 9, 28))
        assertNull(vm.uiState.value.selectedSlot)
    }

    @Test
    fun `day without slots yields empty grid`() = runTest(testDispatcher) {
        val vm = vm()
        vm.onDiaSelected(LocalDate.of(2026, 9, 22)) // Tuesday, no block
        assertNotNull(vm.uiState.value.selectedDia)
        assertTrue(vm.uiState.value.slots.isEmpty())
    }

    @Test
    fun `doctor without days in the window has empty carousel`() = runTest(testDispatcher) {
        coEvery { repository.getDoctorDetail("d1") } returns Result.success(detail(emptyList()))
        assertTrue(vm().uiState.value.dias.isEmpty())
    }

    @Test
    fun `network failure shows generic message and retry recovers`() = runTest(testDispatcher) {
        coEvery { repository.getDoctorDetail("d1") } returns Result.failure(IOException("boom"))
        val vm = vm()
        assertEquals("Algo deu errado. Tente novamente.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoading)

        coEvery { repository.getDoctorDetail("d1") } returns Result.success(detail(monWedFri))
        vm.retry()
        assertNull(vm.uiState.value.errorMessage)
        assertNotNull(vm.uiState.value.doctor)
    }

    @Test
    fun `booked slots failure also shows error`() = runTest(testDispatcher) {
        coEvery { repository.getBookedSlots("d1", any(), any()) } returns Result.failure(IOException("x"))
        assertNotNull(vm().uiState.value.errorMessage)
    }

    // ---- Story 2.3: booking ----

    private val twoConvenios = detail(monWedFri).copy(convenios = listOf(Convenio.UNIMED, Convenio.AMIL))
    private val target = LocalDate.of(2026, 9, 30) // Wednesday, more than 48h ahead

    private fun DetalheMedicoViewModel.pickSlot(): Instant {
        onDiaSelected(target)
        val slot = uiState.value.slots.first()
        onSlotSelected(slot)
        return slot.start
    }

    @Test
    fun `button needs doctor, day, slot and convenio`() = runTest(testDispatcher) {
        coEvery { repository.getDoctorDetail("d1") } returns Result.success(twoConvenios)
        val vm = vm()
        assertFalse(vm.uiState.value.podeConfirmar)
        vm.onDiaSelected(target)
        assertFalse(vm.uiState.value.podeConfirmar)
        vm.onSlotSelected(vm.uiState.value.slots.first())
        assertFalse(vm.uiState.value.podeConfirmar)
        vm.onConvenioSelected(Convenio.AMIL)
        assertTrue(vm.uiState.value.podeConfirmar)
    }

    @Test
    fun `single convenio comes preselected`() = runTest(testDispatcher) {
        assertEquals(Convenio.UNIMED, vm().uiState.value.selectedConvenio)
    }

    @Test
    fun `success exposes the confirmation summary`() = runTest(testDispatcher) {
        val vm = vm()
        val start = vm.pickSlot()
        coEvery { appointments.bookAppointment("d1", start, "Unimed") } returns BookingResult.Success("a1")
        vm.confirmar()
        val c = vm.uiState.value.confirmacao
        assertNotNull(c)
        assertEquals("Dra. Ana", c!!.doctorName)
        assertEquals(start, c.start)
        assertEquals("Unimed", c.convenio)
        assertFalse(vm.uiState.value.isSubmitting)
        vm.onConfirmacaoConsumida()
        assertNull(vm.uiState.value.confirmacao)
    }

    @Test
    fun `no double submit while sending`() = runTest(testDispatcher) {
        val vm = vm()
        val gate = CompletableDeferred<BookingResult>()
        coEvery { appointments.bookAppointment(any(), any(), any()) } coAnswers { gate.await() }
        vm.pickSlot()
        vm.confirmar()
        assertTrue(vm.uiState.value.isSubmitting)
        assertFalse(vm.uiState.value.podeConfirmar)
        vm.confirmar()
        gate.complete(BookingResult.Success(null))
        coVerify(exactly = 1) { appointments.bookAppointment(any(), any(), any()) }
    }

    @Test
    fun `conflict shows exact message, marks Ocupado and clears selection`() = runTest(testDispatcher) {
        val vm = vm()
        val start = vm.pickSlot()
        coEvery { appointments.bookAppointment(any(), any(), any()) } returns BookingResult.SlotTaken
        vm.confirmar()
        val s = vm.uiState.value
        assertEquals("Este horário acabou de ser reservado, escolha outro.", s.bookingMessage)
        assertNull(s.selectedSlot)
        assertNull(s.confirmacao)
        assertEquals(SlotMotivo.OCUPADO, s.slots.first { it.start == start }.motivo)
        // initial load + refetch after the conflict
        coVerify(atLeast = 2) { repository.getBookedSlots("d1", any(), any()) }
    }

    @Test
    fun `lead time shows clear message without technical text`() = runTest(testDispatcher) {
        val vm = vm()
        vm.pickSlot()
        coEvery { appointments.bookAppointment(any(), any(), any()) } returns BookingResult.LeadTime
        vm.confirmar()
        assertEquals(MSG_ANTECEDENCIA, vm.uiState.value.bookingMessage)
        assertNull(vm.uiState.value.selectedSlot)
    }

    @Test
    fun `network failure shows generic message and does not navigate`() = runTest(testDispatcher) {
        val vm = vm()
        vm.pickSlot()
        coEvery { appointments.bookAppointment(any(), any(), any()) } returns
            BookingResult.Failure(AppError.Unexpected("boom"))
        vm.confirmar()
        assertEquals("Algo deu errado. Tente novamente.", vm.uiState.value.bookingMessage)
        assertNull(vm.uiState.value.confirmacao)
        assertFalse(vm.uiState.value.isSubmitting)
        assertNotNull(vm.uiState.value.selectedSlot)
    }

    @Test
    fun `realtime marks slot Ocupado and clears a selected one with notice`() = runTest(testDispatcher) {
        val changes = MutableSharedFlow<BookedSlotChange>()
        every { appointments.observeBookedSlots("d1") } returns changes
        val vm = vm()
        vm.startObserving()
        val start = vm.pickSlot()
        val other = vm.uiState.value.slots[1].start

        changes.emit(BookedSlotChange.Taken(other))
        assertEquals(SlotMotivo.OCUPADO, vm.uiState.value.slots[1].motivo)
        assertEquals(start, vm.uiState.value.selectedSlot)

        changes.emit(BookedSlotChange.Taken(start))
        assertNull(vm.uiState.value.selectedSlot)
        assertEquals(MSG_RESERVADO_REALTIME, vm.uiState.value.bookingMessage)

        changes.emit(BookedSlotChange.Freed(other))
        assertNull(vm.uiState.value.slots[1].motivo)
        vm.stopObserving()
    }

    // ---- Story 2.4: reschedule mode ----

    private fun reschedulingVm() = DetalheMedicoViewModel("d1", repository, clock, appointments, "a1")

    private fun DetalheMedicoViewModel.pickFreeSlot(): Instant = pickSlot()

    @Test
    fun `reschedule mode needs no convenio and never books`() = runTest(testDispatcher) {
        coEvery { repository.getDoctorDetail("d1") } returns Result.success(twoConvenios)
        val vm = reschedulingVm()
        assertTrue(vm.uiState.value.reagendando)
        val start = vm.pickFreeSlot()
        assertNull(vm.uiState.value.selectedConvenio)
        assertTrue(vm.uiState.value.podeConfirmar)
        coEvery { appointments.rescheduleAppointment("a1", start) } returns RescheduleResult.Success
        vm.confirmar()
        assertTrue(vm.uiState.value.reagendado)
        assertNull(vm.uiState.value.confirmacao)
        assertFalse(vm.uiState.value.isSubmitting)
        coVerify(exactly = 0) { appointments.bookAppointment(any(), any(), any()) }
        vm.onReagendadoConsumido()
        assertFalse(vm.uiState.value.reagendado)
    }

    @Test
    fun `reschedule conflict shows exact message and marks Ocupado`() = runTest(testDispatcher) {
        val vm = reschedulingVm()
        val start = vm.pickFreeSlot()
        coEvery { appointments.rescheduleAppointment(any(), any()) } returns RescheduleResult.SlotTaken
        vm.confirmar()
        val s = vm.uiState.value
        assertEquals("Este horário acabou de ser reservado, escolha outro.", s.bookingMessage)
        assertNull(s.selectedSlot)
        assertFalse(s.reagendado)
        assertEquals(SlotMotivo.OCUPADO, s.slots.first { it.start == start }.motivo)
    }

    @Test
    fun `reschedule lead time, closed window and failure show clear messages`() = runTest(testDispatcher) {
        val vm = reschedulingVm()
        vm.pickFreeSlot()
        coEvery { appointments.rescheduleAppointment(any(), any()) } returns RescheduleResult.LeadTime
        vm.confirmar()
        assertEquals(MSG_ANTECEDENCIA, vm.uiState.value.bookingMessage)

        vm.pickFreeSlot()
        coEvery { appointments.rescheduleAppointment(any(), any()) } returns RescheduleResult.WindowClosed
        vm.confirmar()
        assertEquals(MSG_JANELA_24H, vm.uiState.value.bookingMessage)

        vm.pickFreeSlot()
        coEvery { appointments.rescheduleAppointment(any(), any()) } returns
            RescheduleResult.Failure(AppError.Unexpected("boom"))
        vm.confirmar()
        assertEquals("Algo deu errado. Tente novamente.", vm.uiState.value.bookingMessage)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `no double submit while rescheduling`() = runTest(testDispatcher) {
        val vm = reschedulingVm()
        val gate = CompletableDeferred<RescheduleResult>()
        coEvery { appointments.rescheduleAppointment(any(), any()) } coAnswers { gate.await() }
        vm.pickFreeSlot()
        vm.confirmar()
        vm.confirmar()
        gate.complete(RescheduleResult.Success)
        coVerify(exactly = 1) { appointments.rescheduleAppointment(any(), any()) }
    }
}
