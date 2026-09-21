package com.agendamedica.app.data.repository

import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.UnknownRestException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the repository-side mapping behind the "Servidor rejeita", "Limite de envio" and "Falha de rede" matrix rows. */
class PasswordResetOutcomeTest {

    private fun response(status: HttpStatusCode): HttpResponse =
        mockk<HttpResponse>(relaxed = true).also { every { it.status } returns status }

    @Test
    fun `server rejection is indistinguishable from success`() {
        val notAuthorized = AuthRestException("email_address_not_authorized", "not allowed", response(HttpStatusCode.BadRequest))
        val badRequest = BadRequestRestException("""{"msg":"x"}""", response(HttpStatusCode.BadRequest))

        assertEquals(PasswordResetOutcome.Neutral, passwordResetOutcomeFor(notAuthorized))
        assertEquals(PasswordResetOutcome.Neutral, passwordResetOutcomeFor(badRequest))
    }

    @Test
    fun `rate limit by code or by status is reported`() {
        val byCode = AuthRestException("over_email_send_rate_limit", "slow down", response(HttpStatusCode.BadRequest))
        val byStatus = UnknownRestException("too many", response(HttpStatusCode.TooManyRequests))

        assertEquals(PasswordResetOutcome.RateLimited, passwordResetOutcomeFor(byCode))
        assertEquals(PasswordResetOutcome.RateLimited, passwordResetOutcomeFor(byStatus))
    }

    @Test
    fun `server outage is a failure, not a neutral confirmation`() {
        val outage = UnknownRestException("bad gateway", response(HttpStatusCode.BadGateway))

        assertEquals(PasswordResetOutcome.Failed, passwordResetOutcomeFor(outage))
    }

    @Test
    fun `network or unexpected failure is a generic failure`() {
        assertEquals(PasswordResetOutcome.Failed, passwordResetOutcomeFor(IOException("Unable to resolve host")))
    }
}
