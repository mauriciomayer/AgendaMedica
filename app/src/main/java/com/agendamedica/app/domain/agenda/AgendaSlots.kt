package com.agendamedica.app.domain.agenda

import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.DiaSemana
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.model.ScheduleBlock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Minimum notice between "now" and a bookable slot. The single definition in the domain. */
const val ANTECEDENCIA_MINIMA_HORAS = 48L

/** An appointment can only be cancelled/rescheduled while it starts at least this far ahead. */
const val JANELA_ALTERACAO_HORAS = 24L

/**
 * Whether an appointment starting at [start] can still be cancelled or rescheduled: it must start
 * 24h or more after [now] (exactly 24h is allowed). Mirrors the database rule, which is the
 * authority; the app only uses it to disable buttons.
 */
fun podeAlterarConsulta(start: Instant, now: Instant): Boolean =
    !start.isBefore(now.plus(Duration.ofHours(JANELA_ALTERACAO_HORAS)))

/** Duration of a single appointment. */
const val DURACAO_CONSULTA_MINUTOS = 30L

/** Mandatory rest between the end of one appointment and the start of the next. */
const val INTERVALO_ENTRE_CONSULTAS_MINUTOS = 15L

/** Spacing between consecutive bookable start positions (30 + 15). */
const val ESPACAMENTO_SLOTS_MINUTOS = DURACAO_CONSULTA_MINUTOS + INTERVALO_ENTRE_CONSULTAS_MINUTOS

const val JANELA_DIAS = 10
const val MAX_DIAS_CARROSSEL = 6

const val MOTIVO_OCUPADO = "Ocupado"
const val MOTIVO_ANTECEDENCIA = "Antecedência mín. ${ANTECEDENCIA_MINIMA_HORAS}h"

/** All schedule rules are evaluated in this zone, whatever the device zone is. */
val FUSO_AGENDA: ZoneId = ZoneId.of("America/Sao_Paulo")

/** A doctor as shown in Detalhe do Médico: public profile plus weekly schedule. */
data class DoctorDetail(
    val id: String,
    val name: String,
    val especialidade: Especialidade,
    val city: String,
    val neighborhood: String,
    val convenios: List<Convenio>,
    val schedule: List<ScheduleBlock>,
)

enum class SlotMotivo(val texto: String) {
    OCUPADO(MOTIVO_OCUPADO),
    ANTECEDENCIA(MOTIVO_ANTECEDENCIA),
}

/** One 30-minute appointment slot; [motivo] is null when the slot can be selected. */
data class AgendaSlot(val start: Instant, val hora: LocalTime, val motivo: SlotMotivo?) {
    val habilitado: Boolean get() = motivo == null
}

/** Weekday of [date] (DiaSemana.isoValue: 0 = Sunday, like Postgres EXTRACT(DOW)). */
fun diaSemanaDe(date: LocalDate): DiaSemana = DiaSemana.entries.first { it.isoValue == date.dayOfWeek.value % 7 }

/** Today's date in [FUSO_AGENDA]. */
fun hojeNaAgenda(clock: Clock): LocalDate = LocalDate.now(clock.withZone(FUSO_AGENDA))

/** Start of the range covered by the carousel, in [FUSO_AGENDA]. */
fun inicioJanela(clock: Clock): Instant = hojeNaAgenda(clock).atStartOfDay(FUSO_AGENDA).toInstant()

/** Exclusive end of the range covered by the carousel. */
fun fimJanela(clock: Clock): Instant =
    hojeNaAgenda(clock).plusDays(JANELA_DIAS.toLong()).atStartOfDay(FUSO_AGENDA).toInstant()

/** Next [JANELA_DIAS] days from today, only weekdays the doctor works, at most [MAX_DIAS_CARROSSEL]. */
fun diasCarrossel(schedule: List<ScheduleBlock>, clock: Clock): List<LocalDate> {
    val atende = schedule.map { it.dia }.toSet()
    val hoje = hojeNaAgenda(clock)
    return (0 until JANELA_DIAS)
        .map { hoje.plusDays(it.toLong()) }
        .filter { diaSemanaDe(it) in atende }
        .take(MAX_DIAS_CARROSSEL)
}

/**
 * Derives the 45-minute grid of [dia] from the weekly [schedule] (AD-4, never stored): each
 * position is a 30-minute appointment, spaced 45 minutes apart (30 + 15 min mandatory rest),
 * starting from the block's `start_time`. A position is only offered if the whole 30-minute
 * appointment fits before the block's `end_time`. "Ocupado" wins over the notice rule; all
 * comparisons are instant against instant (AD-8).
 */
fun slotsDoDia(
    dia: LocalDate,
    schedule: List<ScheduleBlock>,
    ocupados: Set<Instant>,
    clock: Clock,
): List<AgendaSlot> {
    val limite = clock.instant().plus(Duration.ofHours(ANTECEDENCIA_MINIMA_HORAS))
    val diaSemana = diaSemanaDe(dia)
    val horas = sortedSetOf<LocalTime>()
    schedule.filter { it.dia == diaSemana }.forEach { block ->
        val fim = block.endTime
        var t = block.startTime
        while (!t.plusMinutes(DURACAO_CONSULTA_MINUTOS).isAfter(fim)) {
            horas.add(t)
            val next = t.plusMinutes(ESPACAMENTO_SLOTS_MINUTOS)
            if (next <= t) break // wrapped past midnight
            t = next
        }
    }
    return horas.map { hora ->
        val start = ZonedDateTime.of(dia, hora, FUSO_AGENDA).toInstant()
        val motivo = when {
            start in ocupados -> SlotMotivo.OCUPADO
            start < limite -> SlotMotivo.ANTECEDENCIA
            else -> null
        }
        AgendaSlot(start, hora, motivo)
    }
}
