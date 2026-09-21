package com.agendamedica.app.domain.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixed location list lives in three hand-kept copies (this enum, the `register-doctor` Edge
 * Function and the SQL validation in migration 0003). A drift would offer a location in the
 * dropdown that the server then rejects, so this test compares all three.
 */
class LocalizacaoConsistencyTest {

    private data class Entry(val city: String, val neighborhood: String, val latitude: Double, val longitude: Double)

    private fun repoFile(relative: String): File {
        // Unit tests run with the module directory (app/) as the working directory.
        val candidates = listOf(File("../$relative"), File(relative))
        return candidates.firstOrNull { it.exists() } ?: error("Arquivo não encontrado: $relative")
    }

    private val expected: Set<Entry> = Localizacao.entries
        .map { Entry(it.city, it.neighborhood, it.latitude, it.longitude) }
        .toSet()

    @Test
    fun `edge function list matches the enum`() {
        val regex = Regex(
            """label:\s*"([^"]+)",\s*city:\s*"([^"]+)",\s*neighborhood:\s*"([^"]+)",\s*latitude:\s*(-?[\d.]+),\s*longitude:\s*(-?[\d.]+)""",
        )
        val text = repoFile("supabase/functions/register-doctor/index.ts").readText()
        val found = regex.findAll(text).map { m ->
            val (label, city, neighborhood, lat, lng) = m.destructured
            assertTrue("rótulo diferente para $label", Localizacao.fromLabel(label) != null)
            Entry(city, neighborhood, lat.toDouble(), lng.toDouble())
        }.toList()

        assertEquals(Localizacao.entries.size, found.size)
        assertEquals(expected, found.toSet())
    }

    @Test
    fun `migration validation list matches the enum`() {
        val regex = Regex("""\(\s*'([^']+)',\s*'([^']+)',\s*(-?[\d.]+)(?:::double precision)?,\s*(-?[\d.]+)(?:::double precision)?\s*\)""")
        val text = repoFile("supabase/migrations/0003_doctor_location.sql").readText()
        val found = regex.findAll(text).map { m ->
            val (city, neighborhood, lat, lng) = m.destructured
            Entry(city, neighborhood, lat.toDouble(), lng.toDouble())
        }.toList()

        assertEquals(Localizacao.entries.size, found.size)
        assertEquals(expected, found.toSet())
    }
}
