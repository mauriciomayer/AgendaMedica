package com.agendamedica.app.domain.model

import java.time.LocalTime

/** A single weekly availability block, as stored in `doctor_schedules`. */
data class ScheduleBlock(
    val dia: DiaSemana,
    val startTime: LocalTime, // parsed once at the Repository boundary from Postgres `time` ("HH:mm:ss")
    val endTime: LocalTime,
)

/** The doctor's own profile, as read back from `doctors` + `doctor_schedules` (AD-11). */
data class DoctorProfile(
    val name: String,
    val especialidade: Especialidade,
    val convenios: List<Convenio>,
    val schedule: List<ScheduleBlock>,
)
