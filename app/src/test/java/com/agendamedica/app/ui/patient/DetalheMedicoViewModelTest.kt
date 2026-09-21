package com.agendamedica.app.ui.patient

import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.domain.agenda.DoctorDetail
import com.agendamedica.app.domain.agenda.MOTIVO_ANTECEDENCIA
import com.agendamedica.app.domain.agenda.SlotMotivo
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.DiaSemana
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.model.ScheduleBlock
import io.mockk.coEvery
import io.mockk.mockk
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DetalheMedicoViewModelTest {

    private val repository: DoctorRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    // Monday 2026-09-21 10:00 in Sao Paulo (13:00 UTC); device zone deliberately different.
    private val clock = Clock.fixed(Instant.parse("2026-09-21T13:00:00Z"), ZoneId.of("Asia/Tokyo"))

    private fun detail(schedule: List<ScheduleBlock>) = DoctorDetail(
        "d1", "Dra. Ana", Especialidade.CARDIOLOGIA, "São Paulo", "Centro", listOf(Convenio.UNIMED), schedule,
    )

    private val monWedFri = listOf(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA)
        .map { ScheduleBlock(it, "08:00", "09:00") }

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

    private fun vm() = DetalheMedicoViewModel("d1", repository, clock)

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
    fun `selecting a day shows 15 minute slots with reasons`() = runTest(testDispatcher) {
        val vm = vm()
        vm.onDiaSelected(LocalDate.of(2026, 9, 21)) // today: under 48h
        val slots = vm.uiState.value.slots
        assertEquals(4, slots.size)
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
}
