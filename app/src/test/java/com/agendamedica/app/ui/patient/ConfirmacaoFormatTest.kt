package com.agendamedica.app.ui.patient

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The Confirmação date is shown in São Paulo time, whatever the device zone is (AD-8). */
class ConfirmacaoFormatTest {
    @Test
    fun `formats the instant in Sao Paulo time`() {
        val text = formatarDataHora(Instant.parse("2026-09-29T13:00:00Z"))
        assertEquals(true, text.contains("29/09/2026"))
        assertEquals(true, text.contains("10:00"))
    }
}
