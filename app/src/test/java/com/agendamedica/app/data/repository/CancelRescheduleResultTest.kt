package com.agendamedica.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CancelRescheduleResultTest {
    @Test
    fun `cancel_window maps to WindowClosed for cancel`() {
        assertEquals(CancelResult.WindowClosed, IllegalStateException("CONFLICT: cancel_window").toCancelResult())
    }

    @Test
    fun `other cancel failures are generic`() {
        val forbidden = IllegalStateException("FORBIDDEN: consulta não pertence ao usuário").toCancelResult()
        assertTrue((forbidden as CancelResult.Failure).error is AppError.Forbidden)
        val invalid = IllegalStateException("INVALID: consulta não está confirmada").toCancelResult()
        assertTrue((invalid as CancelResult.Failure).error is AppError.Invalid)
        val network = IOException("timeout").toCancelResult() as CancelResult.Failure
        assertTrue(network.error is AppError.Unexpected)
    }

    @Test
    fun `reschedule conflicts map to typed results`() {
        assertEquals(RescheduleResult.SlotTaken, IllegalStateException("CONFLICT: slot_taken").toRescheduleResult())
        assertEquals(RescheduleResult.LeadTime, IllegalStateException("CONFLICT: lead_time").toRescheduleResult())
        assertEquals(RescheduleResult.WindowClosed, IllegalStateException("CONFLICT: cancel_window").toRescheduleResult())
    }

    @Test
    fun `other reschedule failures are generic`() {
        assertTrue(IllegalStateException("INVALID: o novo horário é igual ao atual").toRescheduleResult() is RescheduleResult.Failure)
        assertTrue(IllegalStateException("CONFLICT: outra coisa").toRescheduleResult() is RescheduleResult.Failure)
        assertTrue(IOException("x").toRescheduleResult() is RescheduleResult.Failure)
    }
}
