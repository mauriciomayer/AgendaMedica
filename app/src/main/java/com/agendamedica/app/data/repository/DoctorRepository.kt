package com.agendamedica.app.data.repository

import com.agendamedica.app.data.remote.SupabaseClientProvider
import com.agendamedica.app.domain.agenda.DoctorDetail
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.DiaSemana
import com.agendamedica.app.domain.model.DoctorProfile
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.model.ScheduleBlock
import com.agendamedica.app.domain.search.DoctorSummary
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime

@Serializable
data class ScheduleInput(
    val weekday: Int,
    val startTime: String,
    val endTime: String,
)

@Serializable
data class RegisterDoctorRequest(
    val name: String,
    val email: String,
    val password: String,
    val specialty: String,
    val insurances: List<String>,
    val schedules: List<ScheduleInput>,
    val location: String,
)

@Serializable
private data class RegisterDoctorErrorBody(val error: String? = null)

@Serializable
private data class DoctorRow(
    val name: String,
    val specialty: String,
    val insurances: List<String>,
)

@Serializable
private data class DoctorSearchRow(
    val id: String,
    val name: String,
    val specialty: String,
    val insurances: List<String>,
    val city: String,
    val neighborhood: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class DoctorScheduleRow(
    val weekday: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
)

@Serializable
private data class BookedSlotRow(
    @SerialName("start_time") val startTime: String,
)

@Serializable
private data class ProfileRoleRow(val role: String)

/**
 * Decoded manually from raw response text (rather than Ktor's `.body<T>()` content
 * negotiation) so this doesn't depend on assumptions about how supabase-kt's internal
 * HttpClient is configured for the Functions plugin.
 */
private val errorBodyJson = Json { ignoreUnknownKeys = true }

/**
 * Only point of access to the doctor's server-side data (AD-2). Two responsibilities:
 * - [registerDoctor]: calls the `register-doctor` Edge Function, which is the only place the
 *   `doctor` role is ever assigned (AD-6) — this repository never writes `profiles`/`doctors`
 *   directly for creation.
 * - [getMyProfile]: reads the caller's own profile back via RLS-scoped Postgrest queries.
 */
class DoctorRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    suspend fun registerDoctor(request: RegisterDoctorRequest): Result<Unit> = runCatching {
        // functions-kt does not set a Content-Type by default when serializing `body`
        // (see the plugin's own kdoc) — set it explicitly rather than rely on the Edge
        // Function's req.json() ignoring the header, so the request is correct regardless
        // of runtime.
        val response: HttpResponse = client.functions.invoke(
            function = "register-doctor",
            body = request,
            headers = Headers.build { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) },
        )
        if (!response.status.isSuccess()) {
            val errorMessage = runCatching {
                errorBodyJson.decodeFromString<RegisterDoctorErrorBody>(response.bodyAsText()).error
            }.getOrNull() ?: "UNEXPECTED: register-doctor retornou ${response.status.value}"
            throw IllegalStateException(errorMessage)
        }
    }

    /**
     * Lists doctors for Busca, optionally filtered by [especialidade] on the server. Region and
     * ordering are applied by the caller (domain/search). Readable by any authenticated user
     * via RLS (`doctors_select_authenticated`).
     */
    suspend fun searchDoctors(especialidade: Especialidade?): Result<List<DoctorSummary>> = runCatching {
        client.postgrest.from("doctors")
            .select {
                if (especialidade != null) filter { eq("specialty", especialidade.label) }
            }
            .decodeList<DoctorSearchRow>()
            .mapNotNull { row ->
                DoctorSummary(
                    id = row.id,
                    name = row.name,
                    especialidade = Especialidade.fromLabel(row.specialty) ?: return@mapNotNull null,
                    city = row.city,
                    neighborhood = row.neighborhood,
                    latitude = row.latitude,
                    longitude = row.longitude,
                    convenios = row.insurances.mapNotNull(Convenio::fromLabel),
                )
            }
    }

    /** Public profile + weekly schedule of one doctor, for Detalhe do Medico. */
    suspend fun getDoctorDetail(id: String): Result<DoctorDetail> = runCatching {
        val row = client.postgrest.from("doctors")
            .select { filter { eq("id", id) } }
            .decodeSingle<DoctorSearchRow>()
        val scheduleRows = client.postgrest.from("doctor_schedules")
            .select { filter { eq("doctor_id", id) } }
            .decodeList<DoctorScheduleRow>()
        DoctorDetail(
            id = row.id,
            name = row.name,
            especialidade = Especialidade.fromLabel(row.specialty)
                ?: throw IllegalStateException("UNEXPECTED: especialidade desconhecida"),
            city = row.city,
            neighborhood = row.neighborhood,
            convenios = row.insurances.mapNotNull(Convenio::fromLabel),
            schedule = scheduleRows.mapNotNull { r ->
                ScheduleBlock(
                    dia = DiaSemana.entries.firstOrNull { it.isoValue == r.weekday } ?: return@mapNotNull null,
                    startTime = r.startTime,
                    endTime = r.endTime,
                )
            },
        )
    }

    /** Occupied slot start times of [doctorId] in [de, ate). `booked_slots` carries no patient data. */
    suspend fun getBookedSlots(doctorId: String, de: Instant, ate: Instant): Result<Set<Instant>> = runCatching {
        client.postgrest.from("booked_slots")
            .select {
                filter {
                    eq("doctor_id", doctorId)
                    gte("start_time", de.toString())
                    lt("start_time", ate.toString())
                }
            }
            .decodeList<BookedSlotRow>()
            .map { OffsetDateTime.parse(it.startTime).toInstant() }
            .toSet()
    }

    suspend fun getMyProfile(): Result<DoctorProfile> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("UNEXPECTED: sessão não encontrada")

        val doctorRow = client.postgrest.from("doctors")
            .select {
                filter { eq("id", userId) }
            }
            .decodeSingle<DoctorRow>()

        val scheduleRows = client.postgrest.from("doctor_schedules")
            .select {
                filter { eq("doctor_id", userId) }
            }
            .decodeList<DoctorScheduleRow>()

        DoctorProfile(
            name = doctorRow.name,
            especialidade = Especialidade.fromLabel(doctorRow.specialty)
                ?: throw IllegalStateException("UNEXPECTED: especialidade desconhecida"),
            convenios = doctorRow.insurances.mapNotNull(Convenio::fromLabel),
            schedule = scheduleRows.map { row ->
                ScheduleBlock(
                    dia = DiaSemana.entries.first { it.isoValue == row.weekday },
                    startTime = row.startTime,
                    endTime = row.endTime,
                )
                // Sort by DiaSemana.ordered's index (Monday-first), matching the order the
                // médico picked days in during Cadastro (CadastroMedicoScreen) — sorting by
                // isoValue (Sunday-first) would show e.g. "Sábado, Domingo" reversed here.
            }.sortedBy { DiaSemana.ordered.indexOf(it.dia) },
        )
    }

    /**
     * Reads the caller's role from `profiles` (never trusted from a cached claim — AD-6),
     * used right after [AuthRepository.signIn] to decide whether to route into Minha Agenda.
     * Story 1.2/1.3 will extend this once patient accounts exist.
     */
    suspend fun isCurrentUserDoctor(): Result<Boolean> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("UNEXPECTED: sessão não encontrada")

        val role = client.postgrest.from("profiles")
            .select {
                filter { eq("id", userId) }
            }
            .decodeSingle<ProfileRoleRow>()
            .role

        role == "doctor"
    }
}
