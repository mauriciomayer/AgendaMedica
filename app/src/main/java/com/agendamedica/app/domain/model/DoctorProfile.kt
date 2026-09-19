package com.agendamedica.app.domain.model

/** A single weekly availability block, as stored in `doctor_schedules`. */
data class ScheduleBlock(
    val dia: DiaSemana,
    val startTime: String, // "HH:mm", kept as text for display; no arithmetic needed in this story
    val endTime: String,
)

/** The doctor's own profile, as read back from `doctors` + `doctor_schedules` (AD-11). */
data class DoctorProfile(
    val name: String,
    val especialidade: Especialidade,
    val convenios: List<Convenio>,
    val schedule: List<ScheduleBlock>,
)
