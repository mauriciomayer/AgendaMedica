package com.agendamedica.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers Story 6.5: the e-mail typing mask (`filtrarEmail`). */
class EmailInputFilterTest {

    @Test
    fun `space typed at the end is dropped`() {
        assertEquals("joao", filtrarEmail("joao "))
    }

    @Test
    fun `spaces in the middle and at the edges are dropped`() {
        assertEquals("joaosilva", filtrarEmail("joao silva"))
        assertEquals("joao", filtrarEmail("  joao  "))
    }

    @Test
    fun `accented letters are dropped`() {
        assertEquals("joo", filtrarEmail("joão"))
        assertEquals("aeiou", filtrarEmail("açeéiou"))
    }

    @Test
    fun `emoji is dropped`() {
        assertEquals("joao", filtrarEmail("jo😀ao"))
    }

    @Test
    fun `second at sign is blocked`() {
        assertEquals("a@b", filtrarEmail("a@b@"))
        assertEquals("a@bc", filtrarEmail("a@b@c"))
    }

    @Test
    fun `at sign at the start is kept`() {
        assertEquals("@abc", filtrarEmail("@abc"))
    }

    @Test
    fun `pasted text with junk is cleaned instead of rejected`() {
        assertEquals("joaosilva@gmail.com", filtrarEmail(" joao silva@gmail.com "))
    }

    @Test
    fun `all allowed characters pass through unchanged and case is preserved`() {
        assertEquals("Joao.Silva+x_y-z%1@Mail.com", filtrarEmail("Joao.Silva+x_y-z%1@Mail.com"))
    }

    @Test
    fun `empty string stays empty`() {
        assertEquals("", filtrarEmail(""))
    }

    @Test
    fun `other symbols are dropped`() {
        assertEquals("ab", filtrarEmail("a!#\$&*()=,;:/\\'\"<>[]{}b"))
    }

    @Test
    fun `typing an at sign before the existing one keeps the original and rejects the new`() {
        assertEquals("a@b", filtrarEmail("@a@b", anterior = "a@b")) // at the start
        assertEquals("a@b", filtrarEmail("a@@b", anterior = "a@b")) // right next to it
        assertEquals("ab@c", filtrarEmail("a@b@c", anterior = "ab@c")) // in the middle, before it
        assertEquals("a@b", filtrarEmail("a@b@", anterior = "a@b")) // at the end
    }

    @Test
    fun `replacing the text that held the at sign with another at sign is allowed`() {
        assertEquals("x@y", filtrarEmail("x@y", anterior = "a@b"))
        assertEquals("z@b", filtrarEmail("z@b", anterior = "a@b"))
    }

    @Test
    fun `pasting two at signs into an empty field keeps only the first`() {
        assertEquals("a@bc", filtrarEmail("a@b@c", anterior = ""))
    }

    @Test
    fun `only at signs collapse to one, and only disallowed characters give an empty field`() {
        assertEquals("@", filtrarEmail("@@"))
        assertEquals("", filtrarEmail(" ç😀 "))
    }

    @Test
    fun `full-width lookalikes are dropped`() {
        assertEquals("ab", filtrarEmail("a＠b")) // full-width at sign
        assertEquals("a1", filtrarEmail("a1٢")) // Arabic-Indic digit
    }

    @Test
    fun `filter is idempotent`() {
        val sujo = " Joao S.ilva+x@@Mail.com ç"
        assertEquals(filtrarEmail(sujo), filtrarEmail(filtrarEmail(sujo)))
    }
}
