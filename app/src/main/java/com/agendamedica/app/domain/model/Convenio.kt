package com.agendamedica.app.domain.model

/**
 * Fixed list of 4 insurances, including "Particular" (PRD Glossário §3). Same rationale as
 * [Especialidade]: a versioned code constant, not a database table, mirrored in the
 * `register-doctor` Edge Function.
 */
enum class Convenio(val label: String) {
    UNIMED("Unimed"),
    AMIL("Amil"),
    BRADESCO("Bradesco"),
    PARTICULAR("Particular"),
    ;

    companion object {
        fun fromLabel(label: String): Convenio? = entries.find { it.label == label }
    }
}
