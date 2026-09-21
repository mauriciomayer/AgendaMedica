package com.agendamedica.app.data.repository

import com.agendamedica.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
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
