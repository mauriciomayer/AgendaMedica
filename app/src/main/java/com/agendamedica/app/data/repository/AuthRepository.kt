package com.agendamedica.app.data.repository

import android.content.Intent
import com.agendamedica.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import java.net.URLDecoder

/** Outcome of asking for a password-reset e-mail; never reveals whether the account exists (FR11). */
sealed interface PasswordResetOutcome {
    /** Request accepted, or rejected by the server for any reason other than rate limiting. */
    data object Neutral : PasswordResetOutcome

    data object RateLimited : PasswordResetOutcome

    /** Network/unexpected failure. */
    data object Failed : PasswordResetOutcome
}

/** What an `agendamedica://reset-password` link turned out to be. */
enum class RecoveryLinkKind { READY, EXPIRED, IGNORE }

const val PASSWORD_RESET_REDIRECT_URL = "agendamedica://reset-password"

/**
 * Pure classification of the deep link's URL fragment (implicit flow). An `error`/`error_code`
 * means an expired/already-used link; only `type=recovery` with an `access_token` is usable.
 */
fun classifyRecoveryFragment(fragment: String?): RecoveryLinkKind {
    val params = parseFragment(fragment)
    if (params.containsKey("error") || params.containsKey("error_code")) return RecoveryLinkKind.EXPIRED
    if (params["type"] == "recovery" && !params["access_token"].isNullOrBlank()) return RecoveryLinkKind.READY
    return RecoveryLinkKind.IGNORE
}

/**
 * Full decision for an incoming URI, pure so it can be unit-tested: wrong scheme/host is
 * ignored; an error in the fragment (implicit flow) or in the query string means an expired or
 * already-used link; only a `type=recovery` fragment with an access token is usable.
 */
fun classifyRecoveryLink(scheme: String?, host: String?, encodedFragment: String?, encodedQuery: String?): RecoveryLinkKind {
    if (scheme != "agendamedica" || host != "reset-password") return RecoveryLinkKind.IGNORE
    val fromFragment = classifyRecoveryFragment(encodedFragment)
    if (fromFragment != RecoveryLinkKind.IGNORE) return fromFragment
    return if (classifyRecoveryFragment(encodedQuery) == RecoveryLinkKind.EXPIRED) RecoveryLinkKind.EXPIRED else RecoveryLinkKind.IGNORE
}

private fun parseFragment(fragment: String?): Map<String, String> {
    if (fragment.isNullOrBlank()) return emptyMap()
    return fragment.split("&").mapNotNull { part ->
        val idx = part.indexOf('=')
        if (idx <= 0) {
            null
        } else {
            val key = runCatching { URLDecoder.decode(part.substring(0, idx), "UTF-8") }.getOrNull()
            val value = runCatching { URLDecoder.decode(part.substring(idx + 1), "UTF-8") }.getOrNull()
            if (key == null || value == null) null else key to value
        }
    }.toMap()
}

/**
 * Maps a failed password-reset request to an outcome. Any server rejection other than rate
 * limiting is deliberately indistinguishable from success (FR11).
 */
fun passwordResetOutcomeFor(error: Throwable): PasswordResetOutcome {
    if (error !is RestException || error.statusCode >= 500) return PasswordResetOutcome.Failed
    val rateLimited = error.statusCode == 429 ||
        (error is AuthRestException && (
            error.errorCode == AuthErrorCode.OverEmailSendRateLimit ||
                error.errorCode == AuthErrorCode.OverRequestRateLimit
            ))
    return if (rateLimited) PasswordResetOutcome.RateLimited else PasswordResetOutcome.Neutral
}

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

    /** Sends the recovery e-mail. Server rejections are deliberately indistinguishable (FR11). */
    suspend fun requestPasswordReset(email: String): PasswordResetOutcome =
        try {
            client.auth.resetPasswordForEmail(email, redirectUrl = PASSWORD_RESET_REDIRECT_URL)
            PasswordResetOutcome.Neutral
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            passwordResetOutcomeFor(e)
        }

    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        client.auth.updateUser { password = newPassword }
    }.map { }

    /**
     * Classifies the incoming deep link BEFORE importing any session: an error fragment never
     * reaches `handleDeeplinks` (it throws). Returns [RecoveryLinkKind.READY] only when the
     * recovery session was actually imported.
     */
    suspend fun handleRecoveryLink(intent: Intent): RecoveryLinkKind {
        val data = intent.data ?: return RecoveryLinkKind.IGNORE
        val kind = classifyRecoveryLink(data.scheme, data.host, data.encodedFragment, data.encodedQuery)
        if (kind != RecoveryLinkKind.READY) return kind
        val params = parseFragment(data.encodedFragment)
        return try {
            // Awaited import (unlike handleDeeplinks, which imports on a detached scope): Nova
            // Senha only opens once the recovery session is really in place, and failures are catchable.
            client.auth.importAuthToken(
                accessToken = params.getValue("access_token"),
                refreshToken = params["refresh_token"].orEmpty(),
                retrieveUser = true,
            )
            RecoveryLinkKind.READY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RecoveryLinkKind.EXPIRED
        }
    }
}
