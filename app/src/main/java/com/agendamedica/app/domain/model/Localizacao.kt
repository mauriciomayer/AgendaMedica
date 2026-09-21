package com.agendamedica.app.domain.model

/**
 * Fixed list of doctor locations (city/neighborhood with approximate coordinates). Same
 * pattern as [Especialidade]/[Convenio]: the `register-doctor` Edge Function (and migration
 * 0003) keep their own copy — if this list changes, change all of them together. Coordinates
 * are approximate (neighborhood center) and used only to sort by distance.
 *
 * [label] ("Bairro, Cidade - UF") is the only thing the client sends at registration; the
 * server resolves city/neighborhood/coordinates itself (AD-1).
 */
enum class Localizacao(
    val neighborhood: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
) {
    SP_CENTRO("Centro", "São Paulo", -23.5505, -46.6333),
    SP_PINHEIROS("Pinheiros", "São Paulo", -23.5613, -46.7008),
    SP_MOEMA("Moema", "São Paulo", -23.6001, -46.6658),
    SP_VILA_MARIANA("Vila Mariana", "São Paulo", -23.5893, -46.6345),
    SP_ITAIM_BIBI("Itaim Bibi", "São Paulo", -23.5845, -46.6784),
    CAMPINAS_CENTRO("Centro", "Campinas", -22.9056, -47.0608),
    CAMPINAS_CAMBUI("Cambuí", "Campinas", -22.8990, -47.0500),
    SANTOS_GONZAGA("Gonzaga", "Santos", -23.9680, -46.3350),
    SANTOS_BOQUEIRAO("Boqueirão", "Santos", -23.9700, -46.3200),
    ;

    val label: String get() = "$neighborhood, $city - SP"

    companion object {
        fun fromLabel(label: String): Localizacao? = entries.find { it.label == label }
    }
}
