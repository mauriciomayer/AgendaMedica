package com.agendamedica.app.ui.doctor

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

/** Covers Story 6.3: the schedule card must show `HH:mm`, never seconds. */
class MinhaAgendaScreenKtTest {

    @Test
    fun `formatHora strips seconds`() {
        assertEquals("08:00", formatHora(LocalTime.of(8, 0, 0)))
        assertEquals("18:30", formatHora(LocalTime.of(18, 30, 45)))
    }

    @Test
    fun `formatHora keeps hours and minutes zero-padded`() {
        assertEquals("08:05", formatHora(LocalTime.of(8, 5)))
    }
}
