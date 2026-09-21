package com.agendamedica.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class BookingResultTest {
    @Test
    fun `slot_taken conflict maps to SlotTaken`() {
        assertEquals(BookingResult.SlotTaken, IllegalStateException("CONFLICT: slot_taken").toBookingResult())
    }

    @Test
    fun `lead_time conflict maps to LeadTime`() {
        assertEquals(BookingResult.LeadTime, IllegalStateException("CONFLICT: lead_time").toBookingResult())
    }

    @Test
    fun `invalid and forbidden are generic failures`() {
        val invalid = IllegalStateException("INVALID: convênio não aceito pelo médico").toBookingResult()
        assertTrue((invalid as BookingResult.Failure).error is AppError.Invalid)
        val forbidden = IllegalStateException("FORBIDDEN: apenas pacientes podem agendar").toBookingResult()
        assertTrue((forbidden as BookingResult.Failure).error is AppError.Forbidden)
    }

    @Test
    fun `network error is an unexpected failure`() {
        val r = IOException("timeout").toBookingResult() as BookingResult.Failure
        assertTrue(r.error is AppError.Unexpected)
    }

    @Test
    fun `other conflicts stay generic failures`() {
        assertTrue(IllegalStateException("CONFLICT: outra coisa").toBookingResult() is BookingResult.Failure)
    }

    @Test
    fun `parses realtime and ISO timestamptz`() {
        val expected = Instant.parse("2026-09-29T13:00:00Z")
        assertEquals(expected, parseTimestamptz("2026-09-29 13:00:00+00"))
        assertEquals(expected, parseTimestamptz("2026-09-29T10:00:00-03:00"))
        assertEquals(expected, parseTimestamptz("2026-09-29T13:00:00+00:00"))
    }
}
