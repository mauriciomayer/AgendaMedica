package com.agendamedica.app.data.repository

import com.agendamedica.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable

@Serializable
data class RegisterPatientRequest(
    val name: String,
    val email: String,
    val password: String,
)

/**
 * Only point of access to the patient's server-side data (AD-2). [registerPatient] calls the
 * `register-patient` Edge Function, the only place the `patient` role is assigned (AD-6).
 * supabase-kt throws a RestException on any non-2xx response; [toAppError] reads the AD-9 prefix
 * from it, so failures are simply propagated through [runCatching].
 */
class PatientRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    suspend fun registerPatient(request: RegisterPatientRequest): Result<Unit> = runCatching {
        client.functions.invoke(
            function = "register-patient",
            body = request,
            headers = Headers.build { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) },
        )
        Unit
    }
}
