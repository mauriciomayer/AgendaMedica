package com.agendamedica.app.domain.model

/**
 * The 7 weekdays used to build a doctor's weekly schedule (RF-2). Not part of the Code Map's
 * explicit domain model list (only Especialidade/Convenio are), but the same fixed-list
 * pattern applies: it's a closed, code-versioned set, never user-entered text.
 *
 * [isoValue] matches Postgres's `EXTRACT(DOW)` convention (0 = Sunday .. 6 = Saturday, NOT
 * ISO 8601, which numbers Monday=1..Sunday=7) — used by
 * `doctor_schedules.weekday` — see supabase/migrations/0001_init.sql.
 */
enum class DiaSemana(val isoValue: Int, val label: String) {
    DOMINGO(0, "Domingo"),
    SEGUNDA(1, "Segunda"),
    TERCA(2, "Terça"),
    QUARTA(3, "Quarta"),
    QUINTA(4, "Quinta"),
    SEXTA(5, "Sexta"),
    SABADO(6, "Sábado"),
    ;

    companion object {
        val ordered: List<DiaSemana> = listOf(SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO, DOMINGO)
    }
}
