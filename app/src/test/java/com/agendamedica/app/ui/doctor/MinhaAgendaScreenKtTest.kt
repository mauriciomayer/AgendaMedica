package com.agendamedica.app.ui.doctor

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers Story 6.3: the schedule card must show `HH:mm`, never Postgres's raw `HH:mm:ss`. */
class MinhaAgendaScreenKtTest {

    @Test
    fun `formatHora strips seconds from a Postgres time string`() {
        assertEquals("08:00", formatHora("08:00:00"))
        assertEquals("18:30", formatHora("18:30:45"))
    }

    @Test
    fun `formatHora is a no-op when there are no seconds to strip`() {
        assertEquals("08:00", formatHora("08:00"))
    }
}
