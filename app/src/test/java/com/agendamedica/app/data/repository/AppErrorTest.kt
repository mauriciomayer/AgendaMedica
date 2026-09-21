package com.agendamedica.app.data.repository

import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.UnknownRestException
import io.ktor.client.statement.HttpResponse
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the error-vocabulary classification (AD-9) behind these I/O-matrix rows from
 * spec-1-1-medico-cadastro-perfil.md:
 * - "Especialidade fora da lista fixa" -> INVALID:
 * - "Tentativa de editar especialidade/convênio pós-criação" -> CONFLICT:
 * - "Falha de rede durante cadastro/login" -> UNEXPECTED bucket, generic message
 * - "Login com credenciais erradas" -> generic message, no technical detail leaked
 */
class AppErrorTest {

    @Test
    fun `CONFLICT prefix maps to Conflict bucket`() {
        val error = IllegalStateException("CONFLICT: specialty não pode ser alterada após a criação do perfil")
            .toAppError()
        assertTrue(error is AppError.Conflict)
    }

    @Test
    fun `INVALID prefix maps to Invalid bucket`() {
        val error = IllegalStateException("INVALID: especialidade inválida").toAppError()
        assertTrue(error is AppError.Invalid)
    }

    @Test
    fun `FORBIDDEN prefix maps to Forbidden bucket`() {
        val error = IllegalStateException("FORBIDDEN: papel incompatível com a ação").toAppError()
        assertTrue(error is AppError.Forbidden)
    }

    @Test
    fun `Edge Function RestException reads the prefix from its JSON error body`() {
        val response = mockk<HttpResponse>(relaxed = true)
        val conflict = BadRequestRestException("""{"error":"CONFLICT: já existe uma conta com este e-mail"}""", response)
            .toAppError()
        val invalid = BadRequestRestException("""{"error":"INVALID: nome é obrigatório"}""", response).toAppError()
        val nonJson = UnknownRestException("Bad Gateway", response).toAppError()

        assertTrue(conflict is AppError.Conflict)
        assertTrue(invalid is AppError.Invalid)
        assertTrue(nonJson is AppError.Unexpected)
    }

    @Test
    fun `unrecognized message falls into Unexpected bucket`() {
        val networkError = IllegalStateException("Unable to resolve host \"10.0.2.2\"").toAppError()
        assertTrue(networkError is AppError.Unexpected)
    }

    @Test
    fun `null message falls into Unexpected bucket`() {
        val error = RuntimeException().toAppError()
        assertTrue(error is AppError.Unexpected)
    }

    @Test
    fun `every bucket renders a generic non-technical user message`() {
        val rawTechnicalDetail = "SocketTimeoutException: connect timed out after 30000ms"
        val messages = listOf(
            AppError.Conflict("CONFLICT: x"),
            AppError.Invalid("INVALID: x"),
            AppError.Forbidden("FORBIDDEN: x"),
            AppError.Unexpected(rawTechnicalDetail),
        ).map { it.toUserMessage() }

        messages.forEach { message ->
            assertTrue(
                "user message must never leak raw technical detail: $message",
                !message.contains(rawTechnicalDetail) && !message.contains("Exception"),
            )
        }
        // Sanity: Conflict and Forbidden intentionally share copy (AD-9 says "erro genérico"
        // for both) — this pins that as an explicit decision, not an accidental duplication.
        assertEquals(AppError.Conflict("CONFLICT: x").toUserMessage(), AppError.Forbidden("FORBIDDEN: x").toUserMessage())
    }
}
