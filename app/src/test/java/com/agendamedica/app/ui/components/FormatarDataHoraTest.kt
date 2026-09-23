package com.agendamedica.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** `formatarDataHora` (shared by Confirmação, Minhas Consultas and Minha Agenda) shows the date in São Paulo time, whatever the device zone is (AD-8). */
class FormatarDataHoraTest {
    @Test
    fun `formats the instant in Sao Paulo time`() {
        val text = formatarDataHora(Instant.parse("2026-09-29T13:00:00Z"))
        assertEquals(true, text.contains("29/09/2026"))
        assertEquals(true, text.contains("10:00"))
    }
}
