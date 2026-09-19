package com.agendamedica.app.data.repository

import com.agendamedica.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.StateFlow

/**
 * The only piece of the app that talks to Supabase Auth (`auth-kt`) — AD-2. Wraps sign-in/
 * sign-out/session for every screen; nothing else touches [SupabaseClient] directly.
 *
 * Account *creation* for a doctor is NOT here — it goes through the `register-doctor` Edge
 * Function (see [DoctorRepository.registerDoctor]) so the server, not the client, fixes the
 * role (AD-6). This repository only establishes the session afterwards, and handles the
 * plain login flow for an already-registered account.
 */
class AuthRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    /** Live session state (authenticated / not authenticated / loading), for routing on app start. */
    val sessionStatus: StateFlow<SessionStatus> = client.auth.sessionStatus

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        client.auth.signOut()
    }
}
