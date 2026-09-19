package com.agendamedica.app.domain.model

/**
 * Fixed list of 6 medical specialties (PRD Glossário §3). Not a database table by design
 * (Architecture Spine, Consistency Conventions) — this enum is the single source of truth
 * shared by the app; the `register-doctor` Edge Function keeps its own copy of the same
 * Portuguese labels (see supabase/functions/register-doctor/index.ts) since Deno code can't
 * import Kotlin. If this list ever changes, both places must change together.
 *
 * [label] is the exact value stored in `doctors.specialty` and shown to users — Portuguese,
 * identical to the PRD Glossary (AD-7: code identifiers in English, user-facing content in
 * Portuguese).
 */
enum class Especialidade(val label: String) {
    CARDIOLOGIA("Cardiologia"),
    DERMATOLOGIA("Dermatologia"),
    PEDIATRIA("Pediatria"),
    ORTOPEDIA("Ortopedia"),
    CLINICO_GERAL("Clínico Geral"),
    GINECOLOGIA("Ginecologia"),
    ;

    companion object {
        fun fromLabel(label: String): Especialidade? = entries.find { it.label == label }
    }
}
