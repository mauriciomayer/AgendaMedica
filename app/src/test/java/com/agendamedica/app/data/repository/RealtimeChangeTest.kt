package com.agendamedica.app.data.repository

import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** The Realtime payload -> [BookedSlotChange] mapping behind the live "Ocupado" updates. */
class RealtimeChangeTest {
    private val serializer = KotlinXSerializer()
    private val now = Clock.System.now()
    private val doctor = "doc-1"
    private val start = Instant.parse("2026-09-29T13:00:00Z")

    private fun row(doctorId: String?, startTime: String?) = buildJsonObject {
        doctorId?.let { put("doctor_id", it) }
        startTime?.let { put("start_time", it) }
    }

    private fun insert(r: kotlinx.serialization.json.JsonObject) = PostgresAction.Insert(r, emptyList(), now, serializer)
    private fun delete(r: kotlinx.serialization.json.JsonObject) = PostgresAction.Delete(r, emptyList(), now, serializer)

    @Test
    fun `insert of this doctor's slot is Taken`() {
        val change = insert(row(doctor, "2026-09-29 13:00:00+00")).toChange(doctor)
        assertEquals(BookedSlotChange.Taken(start), change)
    }

    @Test
    fun `delete of this doctor's slot is Freed`() {
        val change = delete(row(doctor, "2026-09-29T13:00:00+00:00")).toChange(doctor)
        assertEquals(BookedSlotChange.Freed(start), change)
    }

    @Test
    fun `events of another doctor are ignored, including deletes that Realtime cannot filter`() {
        assertNull(insert(row("outro", "2026-09-29 13:00:00+00")).toChange(doctor))
        assertNull(delete(row("outro", "2026-09-29 13:00:00+00")).toChange(doctor))
    }

    @Test
    fun `unreadable payload asks for a re-read`() {
        assertEquals(BookedSlotChange.Resync, insert(row(doctor, "not a date")).toChange(doctor))
        assertEquals(BookedSlotChange.Resync, delete(row(doctor, null)).toChange(doctor))
    }
}
