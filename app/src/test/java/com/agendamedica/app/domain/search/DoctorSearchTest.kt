package com.agendamedica.app.domain.search

import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.model.Localizacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorSearchTest {

    private fun doctor(id: String, name: String, loc: Localizacao) = DoctorSummary(
        id = id,
        name = name,
        especialidade = Especialidade.CARDIOLOGIA,
        city = loc.city,
        neighborhood = loc.neighborhood,
        latitude = loc.latitude,
        longitude = loc.longitude,
        convenios = emptyList(),
    )

    private val pinheiros = doctor("1", "Zélia", Localizacao.SP_PINHEIROS)
    private val cambui = doctor("2", "Ana", Localizacao.CAMPINAS_CAMBUI)
    private val gonzaga = doctor("3", "Bruno", Localizacao.SANTOS_GONZAGA)
    private val pinheiros2 = doctor("4", "Ana", Localizacao.SP_PINHEIROS)

    @Test
    fun `haversine of same point is zero`() {
        val p = GeoPoint(-23.5, -46.6)
        assertEquals(0.0, haversineKm(p, p), 1e-9)
    }

    @Test
    fun `haversine Sao Paulo to Campinas is about 84 km`() {
        val sp = GeoPoint(Localizacao.SP_CENTRO.latitude, Localizacao.SP_CENTRO.longitude)
        val camp = GeoPoint(Localizacao.CAMPINAS_CENTRO.latitude, Localizacao.CAMPINAS_CENTRO.longitude)
        val km = haversineKm(sp, camp)
        assertTrue("was $km", km in 80.0..90.0)
        assertEquals(km, haversineKm(camp, sp), 1e-9)
    }

    @Test
    fun `haversine one degree of latitude is about 111 km`() {
        assertEquals(111.19, haversineKm(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 0.1)
    }

    @Test
    fun `sorts by distance ascending with name as tie-break`() {
        val origin = GeoPoint(Localizacao.SP_PINHEIROS.latitude, Localizacao.SP_PINHEIROS.longitude)
        val sorted = sortByDistance(listOf(gonzaga, cambui, pinheiros, pinheiros2), origin)
        assertEquals(listOf("4", "1", "3", "2"), sorted.map { it.id })
    }

    @Test
    fun `sorts by name ignoring accents and case`() {
        val sorted = sortByName(listOf(pinheiros, doctor("5", "álvaro", Localizacao.SP_CENTRO), gonzaga))
        assertEquals(listOf("álvaro", "Bruno", "Zélia"), sorted.map { it.name })
    }

    @Test
    fun `region filter ignores accents and case, matches city or neighborhood`() {
        assertTrue(matchesRegion(cambui, "CAMBUI"))
        assertTrue(matchesRegion(cambui, "campinas"))
        assertTrue(matchesRegion(pinheiros, "pinheiros"))
        assertTrue(matchesRegion(pinheiros, "são paulo"))
        assertTrue(matchesRegion(pinheiros, "sao paulo"))
        assertTrue(matchesRegion(pinheiros, "  "))
        assertFalse(matchesRegion(gonzaga, "campinas"))
    }

    @Test
    fun `manual search filters by region and orders by name`() {
        val result = searchDoctors(listOf(pinheiros, cambui, gonzaga, pinheiros2), "pinheiros", null)
        assertEquals(listOf("4", "1"), result.map { it.id })
    }

    @Test
    fun `gps search orders by distance and ignores region text`() {
        val origin = GeoPoint(Localizacao.SANTOS_GONZAGA.latitude, Localizacao.SANTOS_GONZAGA.longitude)
        val result = searchDoctors(listOf(pinheiros, cambui, gonzaga), "campinas", origin)
        assertEquals(listOf("3", "1", "2"), result.map { it.id })
    }
}
