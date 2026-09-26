package com.agendamedica.app.ui.patient

import com.agendamedica.app.ui.components.initialsOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure part of the "Card do médico" matrix row: the initials avatar. */
class BuscaCardTest {
    @Test
    fun `initials skip a leading title`() {
        assertEquals("RA", initialsOf("Dr. Ricardo Alves"))
        assertEquals("AF", initialsOf("Dra. Ana Ferreira"))
    }

    @Test
    fun `initials of a single word or a name without title`() {
        assertEquals("M", initialsOf("Maria"))
        assertEquals("JS", initialsOf("João Silva Santos"))
    }
}
