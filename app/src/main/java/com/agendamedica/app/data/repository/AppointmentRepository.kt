package com.agendamedica.app.data.repository

import com.agendamedica.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/** Outcome of [AppointmentRepository.bookAppointment], derived from the AD-9 error vocabulary. */
sealed interface BookingResult {
    data class Success(val appointmentId: String?) : BookingResult

    /** `CONFLICT: slot_taken` — someone else got the slot first. */
    data object SlotTaken : BookingResult

    /** `CONFLICT: lead_time` — the slot starts in less than 48h. */
    data object LeadTime : BookingResult

    /** Everything else (INVALID/FORBIDDEN/UNEXPECTED/network): rendered as a generic message. */
    data class Failure(val error: AppError) : BookingResult
}

/** Maps a throwable from the `book_appointment` call onto a [BookingResult]. Never throws. */
fun Throwable.toBookingResult(): BookingResult {
    val error = toAppError()
    if (error is AppError.Conflict) {
        if (error.rawMessage.contains("slot_taken")) return BookingResult.SlotTaken
        if (error.rawMessage.contains("lead_time")) return BookingResult.LeadTime
    }
    return BookingResult.Failure(error)
}

/** Outcome of [AppointmentRepository.cancelAppointment]. */
sealed interface CancelResult {
    data object Success : CancelResult

    /** `CONFLICT: cancel_window` — the appointment starts in less than 24h. */
    data object WindowClosed : CancelResult

    data class Failure(val error: AppError) : CancelResult
}

/** Maps a throwable from the `cancel_appointment` call onto a [CancelResult]. Never throws. */
fun Throwable.toCancelResult(): CancelResult {
    val error = toAppError()
    if (error is AppError.Conflict && error.rawMessage.contains("cancel_window")) return CancelResult.WindowClosed
    return CancelResult.Failure(error)
}

/** Outcome of [AppointmentRepository.rescheduleAppointment]. */
sealed interface RescheduleResult {
    data object Success : RescheduleResult

    /** `CONFLICT: slot_taken` — someone else got the new slot first; the appointment is unchanged. */
    data object SlotTaken : RescheduleResult

    /** `CONFLICT: lead_time` — the new slot starts in less than 48h. */
    data object LeadTime : RescheduleResult

    /** `CONFLICT: cancel_window` — the current appointment starts in less than 24h. */
    data object WindowClosed : RescheduleResult

    data class Failure(val error: AppError) : RescheduleResult
}

/** Maps a throwable from the `reschedule_appointment` call onto a [RescheduleResult]. Never throws. */
fun Throwable.toRescheduleResult(): RescheduleResult {
    val error = toAppError()
    if (error is AppError.Conflict) {
        if (error.rawMessage.contains("slot_taken")) return RescheduleResult.SlotTaken
        if (error.rawMessage.contains("lead_time")) return RescheduleResult.LeadTime
        if (error.rawMessage.contains("cancel_window")) return RescheduleResult.WindowClosed
    }
    return RescheduleResult.Failure(error)
}

/** One upcoming confirmed appointment of the patient, with the doctor's public data. */
data class MinhaConsulta(
    val id: String,
    val doctorId: String,
    val doctorName: String,
    val especialidade: String,
    val start: Instant,
    val convenio: String,
)

@Serializable
private data class AppointmentDoctorRow(val name: String, val specialty: String)

@Serializable
private data class AppointmentRow(
    val id: String,
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("start_time") val startTime: String,
    val insurance: String,
    val doctors: AppointmentDoctorRow,
)

/** One upcoming confirmed appointment of the doctor; only the patient's name is known (AD-10). */
data class ConsultaDoMedico(
    val id: String,
    val patientName: String,
    val start: Instant,
    val convenio: String,
)

@Serializable
internal data class DoctorAppointmentRow(
    val id: String,
    @SerialName("start_time") val startTime: String,
    val insurance: String,
    @SerialName("patient_name") val patientName: String,
)

internal fun DoctorAppointmentRow.toConsulta() = ConsultaDoMedico(id, patientName, parseTimestamptz(startTime), insurance)

/** A change of `booked_slots` for one doctor, pushed by Realtime. */
sealed interface BookedSlotChange {
    data class Taken(val start: Instant) : BookedSlotChange
    data class Freed(val start: Instant) : BookedSlotChange

    /** The payload could not be understood: the caller should re-read `booked_slots`. */
    data object Resync : BookedSlotChange
}

private val HOURS_ONLY_OFFSET = Regex("[+-][0-9]{2}$")

/** Postgres `timestamptz` text as sent by Realtime ("2026-09-29 13:00:00+00") or PostgREST (ISO). */
internal fun parseTimestamptz(text: String): Instant {
    var value = text.trim().replace(' ', 'T')
    // Offsets like "+00" / "-03" (hours only) are not ISO-8601 offsets for java.time.
    if (HOURS_ONLY_OFFSET.containsMatchIn(value)) value += ":00"
    return OffsetDateTime.parse(value).toInstant()
}

/**
 * Only point of access to appointments (AD-2). Writes go exclusively through the
 * `book_appointment` Postgres function (AD-1); the unique index, not this class, decides who wins.
 */
class AppointmentRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    suspend fun bookAppointment(doctorId: String, start: Instant, insurance: String): BookingResult =
        try {
            val params = buildJsonObject {
                put("p_doctor_id", doctorId)
                put("p_start_time", start.toString())
                put("p_insurance", insurance)
            }
            val id = client.postgrest.rpc("book_appointment", params).data.trim().trim('"')
            BookingResult.Success(id.ifEmpty { null })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.toBookingResult()
        }

    /**
     * The caller's own upcoming confirmed appointments (RLS scopes the rows), soonest first.
     * Past and cancelled ones are not shown.
     */
    suspend fun getMyUpcomingAppointments(now: Instant): Result<List<MinhaConsulta>> = runCatching {
        client.postgrest.from("appointments")
            .select(Columns.raw("id, doctor_id, start_time, insurance, doctors(name, specialty)")) {
                filter {
                    eq("status", "confirmed")
                    gte("start_time", now.toString())
                }
                order("start_time", Order.ASCENDING)
            }
            .decodeList<AppointmentRow>()
            .map {
                MinhaConsulta(it.id, it.doctorId, it.doctors.name, it.doctors.specialty, parseTimestamptz(it.startTime), it.insurance)
            }
    }

    /**
     * The caller's (doctor) upcoming confirmed appointments with the patient's name, soonest first,
     * through `list_doctor_appointments` (the function scopes by `auth.uid()` and never returns e-mail).
     */
    suspend fun getDoctorUpcomingAppointments(): Result<List<ConsultaDoMedico>> = runCatching {
        client.postgrest.rpc("list_doctor_appointments")
            .decodeList<DoctorAppointmentRow>()
            .map { it.toConsulta() }
            .sortedBy { it.start }
    }

    suspend fun cancelAppointment(appointmentId: String): CancelResult =
        try {
            client.postgrest.rpc("cancel_appointment", buildJsonObject { put("p_appointment_id", appointmentId) })
            CancelResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.toCancelResult()
        }

    suspend fun rescheduleAppointment(appointmentId: String, newStart: Instant): RescheduleResult =
        try {
            client.postgrest.rpc(
                "reschedule_appointment",
                buildJsonObject {
                    put("p_appointment_id", appointmentId)
                    put("p_new_start_time", newStart.toString())
                },
            )
            RescheduleResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.toRescheduleResult()
        }

    /**
     * Live changes of `booked_slots` of [doctorId]. The channel is subscribed when collection starts
     * and unsubscribed/removed when the collector is cancelled.
     */
    fun observeBookedSlots(doctorId: String): Flow<BookedSlotChange> = channelFlow {
        val realtime = client.realtime
        // Unique topic per collection: a quick re-entry must not reuse the channel that the previous
        // collection is still tearing down (that would silently stop the live updates).
        val channel = realtime.channel("booked-slots-$doctorId-${UUID.randomUUID()}")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "booked_slots"
            filter("doctor_id", FilterOperator.EQ, doctorId)
        }
        launch {
            changes.collect { action -> action.toChange(doctorId)?.let { send(it) } }
        }
        // Every (re)subscription re-reads the table: events between the first load and the join, or
        // during a reconnect, would otherwise be lost.
        launch {
            channel.status.collect { status ->
                if (status == RealtimeChannel.Status.SUBSCRIBED) send(BookedSlotChange.Resync)
            }
        }
        try {
            channel.subscribe()
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                runCatching { channel.unsubscribe() }
                runCatching { realtime.removeChannel(channel) }
            }
        }
    }
}

private fun JsonObject.startInstant(): Instant? =
    runCatching { parseTimestamptz(getValue("start_time").jsonPrimitive.content) }.getOrNull()

private fun JsonObject.isFrom(doctorId: String): Boolean =
    runCatching { getValue("doctor_id").jsonPrimitive.content == doctorId }.getOrDefault(false)

/**
 * Maps a Realtime payload to a change of [doctorId]'s slots. Supabase does not filter DELETE events
 * by column, so a delete of another doctor's slot at the same instant must be ignored (null) rather
 * than freeing this doctor's occupied slot. An unreadable payload asks for a re-read.
 */
internal fun PostgresAction.toChange(doctorId: String): BookedSlotChange? = when (this) {
    is PostgresAction.Insert ->
        if (!record.isFrom(doctorId)) null else record.startInstant()?.let { BookedSlotChange.Taken(it) } ?: BookedSlotChange.Resync
    is PostgresAction.Delete ->
        if (!oldRecord.isFrom(doctorId)) null else oldRecord.startInstant()?.let { BookedSlotChange.Freed(it) } ?: BookedSlotChange.Resync
    else -> BookedSlotChange.Resync
}
