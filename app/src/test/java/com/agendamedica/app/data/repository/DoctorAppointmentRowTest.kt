package com.agendamedica.app.data.repository

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DoctorAppointmentRowTest {

    @Test
    fun `list_doctor_appointments payload maps to the doctor's appointment model`() {
        val json = """[{"id":"a1","start_time":"2026-09-29T13:00:00+00:00","insurance":"Unimed","patient_name":"Maria Souza"}]"""
        val rows = Json.decodeFromString<List<DoctorAppointmentRow>>(json)
        assertEquals(
            listOf(ConsultaDoMedico("a1", "Maria Souza", Instant.parse("2026-09-29T13:00:00Z"), "Unimed")),
            rows.map { it.toConsulta() },
        )
    }

    @Test
    fun `hours-only offset timestamps are parsed`() {
        val row = DoctorAppointmentRow("a2", "2026-09-29 10:00:00-03", "Amil", "João")
        assertEquals(Instant.parse("2026-09-29T13:00:00Z"), row.toConsulta().start)
    }
}
