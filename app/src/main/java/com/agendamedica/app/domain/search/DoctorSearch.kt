package com.agendamedica.app.domain.search

import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.Especialidade
import java.text.Normalizer
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A doctor as shown in a Busca result card. */
data class DoctorSummary(
    val id: String,
    val name: String,
    val especialidade: Especialidade,
    val city: String,
    val neighborhood: String,
    val latitude: Double,
    val longitude: Double,
    val convenios: List<Convenio>,
)

/** A position on Earth in decimal degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double)

private const val EARTH_RADIUS_KM = 6371.0088

/** Great-circle distance in kilometers (Haversine). */
fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

/** Lowercases and strips accents so "Cambuí" matches "cambui" / "CAMBUI". */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()

/** Region filter: accent/case-insensitive "contains" on city or neighborhood. Blank matches all. */
fun matchesRegion(doctor: DoctorSummary, query: String): Boolean {
    val q = normalizeForSearch(query)
    if (q.isEmpty()) return true
    return normalizeForSearch(doctor.city).contains(q) || normalizeForSearch(doctor.neighborhood).contains(q)
}

/** Alphabetical by name (accent/case-insensitive), ties broken by id for stability. */
fun sortByName(doctors: List<DoctorSummary>): List<DoctorSummary> =
    doctors.sortedWith(compareBy({ normalizeForSearch(it.name) }, { it.id }))

/** Ascending distance from [origin]; ties broken by name. */
fun sortByDistance(doctors: List<DoctorSummary>, origin: GeoPoint): List<DoctorSummary> =
    doctors.sortedWith(
        compareBy<DoctorSummary> { haversineKm(origin, GeoPoint(it.latitude, it.longitude)) }
            .thenBy { normalizeForSearch(it.name) }
            .thenBy { it.id },
    )

/**
 * Applies the region filter (manual mode only) and ordering. With [origin] (GPS) results are
 * ordered by distance; otherwise the region text filters and results are ordered by name.
 */
fun searchDoctors(doctors: List<DoctorSummary>, regionQuery: String, origin: GeoPoint?): List<DoctorSummary> =
    if (origin != null) {
        sortByDistance(doctors, origin)
    } else {
        sortByName(doctors.filter { matchesRegion(it, regionQuery) })
    }
