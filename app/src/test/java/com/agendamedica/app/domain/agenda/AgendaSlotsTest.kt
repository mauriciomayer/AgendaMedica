package com.agendamedica.app.domain.agenda

import com.agendamedica.app.domain.model.DiaSemana
import com.agendamedica.app.domain.model.ScheduleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AgendaSlotsTest {

    private fun clockAt(zoned: ZonedDateTime) = Clock.fixed(zoned.toInstant(), ZoneId.of("UTC"))
    private fun sp(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, FUSO_AGENDA)

    // 2026-09-21 is a Monday.
    private val block = listOf(ScheduleBlock(DiaSemana.SEGUNDA, "08:00:00", "09:00"))

    @Test
    fun `block end is exclusive and start inclusive`() {
        val dia = LocalDate.of(2026, 10, 12) // Monday
        val sched = listOf(ScheduleBlock(DiaSemana.SEGUNDA, "08:00", "12:00"))
        val slots = slotsDoDia(dia, sched, emptySet(), clockAt(sp(2026, 9, 21)))
        assertEquals(16, slots.size)
        assertEquals(LocalTime.of(8, 0), slots.first().hora)
        assertEquals(LocalTime.of(11, 45), slots.last().hora)
    }

    @Test
    fun `exactly 48h is enabled and one second less is not`() {
        val slotStart = sp(2026, 9, 23, 8, 0)
        val dia = LocalDate.of(2026, 9, 23) // Wednesday
        val sched = listOf(ScheduleBlock(DiaSemana.QUARTA, "08:00", "08:15"))
        val exact = slotsDoDia(dia, sched, emptySet(), clockAt(slotStart.minusHours(48))).single()
        assertNull(exact.motivo)
        val late = slotsDoDia(dia, sched, emptySet(), clockAt(slotStart.minusHours(48).plusSeconds(1))).single()
        assertEquals(SlotMotivo.ANTECEDENCIA, late.motivo)
    }

    @Test
    fun `ocupado wins over antecedencia`() {
        val dia = LocalDate.of(2026, 9, 21)
        val slots = slotsDoDia(dia, block, setOf(sp(2026, 9, 21, 8, 0).toInstant()), clockAt(sp(2026, 9, 21, 7)))
        assertEquals(SlotMotivo.OCUPADO, slots.first().motivo)
        assertEquals(SlotMotivo.ANTECEDENCIA, slots[1].motivo)
        assertFalse(slots.first().habilitado)
    }

    @Test
    fun `carousel keeps only working weekdays`() {
        val sched = listOf(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA)
            .map { ScheduleBlock(it, "08:00", "09:00") }
        val dias = diasCarrossel(sched, clockAt(sp(2026, 9, 21, 10))) // Monday; window 21/09..30/09
        assertEquals(listOf(21, 23, 25, 28, 30).map { LocalDate.of(2026, 9, it) }, dias)
    }

    @Test
    fun `carousel shows at most 6 days and empty when doctor never works`() {
        val everyDay = DiaSemana.entries.map { ScheduleBlock(it, "08:00", "09:00") }
        assertEquals(6, diasCarrossel(everyDay, clockAt(sp(2026, 9, 21))).size)
        assertTrue(diasCarrossel(emptyList(), clockAt(sp(2026, 9, 21))).isEmpty())
    }

    @Test
    fun `window covers 10 days so the 11th day is excluded`() {
        val sched = listOf(ScheduleBlock(DiaSemana.QUARTA, "08:00", "09:00"))
        // From Mon 21/09: 21..30/09 -> Wed 23 and Wed 30 (day 10).
        assertEquals(
            listOf(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 30)),
            diasCarrossel(sched, clockAt(sp(2026, 9, 21))),
        )
        // From Thu 24/09: 24/09..03/10 -> only Wed 30; Wed 07/10 is out.
        assertEquals(listOf(LocalDate.of(2026, 9, 30)), diasCarrossel(sched, clockAt(sp(2026, 9, 24))))
    }

    @Test
    fun `today and instants follow Sao Paulo not the device zone`() {
        // 2026-09-22 01:30 UTC is still Monday 21/09 22:30 in Sao Paulo.
        val clock = Clock.fixed(Instant.parse("2026-09-22T01:30:00Z"), ZoneId.of("Asia/Tokyo"))
        assertEquals(LocalDate.of(2026, 9, 21), hojeNaAgenda(clock))
        assertEquals(DiaSemana.SEGUNDA, diaSemanaDe(LocalDate.of(2026, 9, 21)))
        assertEquals(DiaSemana.DOMINGO, diaSemanaDe(LocalDate.of(2026, 9, 20)))
        // 08:00 in Sao Paulo (UTC-3) is 11:00 UTC.
        val slot = slotsDoDia(LocalDate.of(2026, 10, 12), block, emptySet(), clock).first()
        assertEquals(Instant.parse("2026-10-12T11:00:00Z"), slot.start)
    }

    @Test
    fun `day without blocks has no slots`() {
        assertTrue(slotsDoDia(LocalDate.of(2026, 9, 22), block, emptySet(), clockAt(sp(2026, 9, 1))).isEmpty())
    }

    @Test
    fun `antecedencia constant is 48`() {
        assertEquals(48L, ANTECEDENCIA_MINIMA_HORAS)
    }

    @Test
    fun `change window is 24h with exactly 24h allowed`() {
        val now = Instant.parse("2026-09-21T13:00:00Z")
        assertEquals(24L, JANELA_ALTERACAO_HORAS)
        assertTrue(podeAlterarConsulta(now.plusSeconds(24 * 3600), now))
        assertTrue(podeAlterarConsulta(now.plusSeconds(24 * 3600 + 1), now))
        assertFalse(podeAlterarConsulta(now.plusSeconds(24 * 3600 - 1), now))
    }
}
