package com.agendamedica.app.data.repository

import io.github.jan.supabase.exceptions.RestException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Error vocabulary shared by every Repository (AD-9). Postgres functions raise exceptions
 * prefixed `CONFLICT:` / `INVALID:` / `FORBIDDEN:`; the Repository layer parses that prefix
 * here so ViewModels never see raw exception text. Anything that doesn't match — network
 * failure, a PostgREST error not originated by a `RAISE EXCEPTION`, a timeout — falls into
 * [Unexpected], which the UI always renders as one generic "try again" message (never the
 * raw infra error).
 */
sealed class AppError(open val rawMessage: String) {
    data class Conflict(override val rawMessage: String) : AppError(rawMessage)
    data class Invalid(override val rawMessage: String) : AppError(rawMessage)
    data class Forbidden(override val rawMessage: String) : AppError(rawMessage)
    data class Unexpected(override val rawMessage: String) : AppError(rawMessage)
}

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * supabase-kt throws [RestException] for every non-2xx response, and its own `message` starts
 * with the raw body followed by URL/headers, so the AD-9 prefix must be read from
 * [RestException.error] instead. For an Edge Function that body is `{"error":"CONFLICT: ..."}`.
 */
private fun errorTextOf(body: String): String =
    runCatching { errorJson.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }
        .getOrNull() ?: body

private val PREFIX_PATTERN =Regex("^(CONFLICT|INVALID|FORBIDDEN):\\s*(.*)$", RegexOption.DOT_MATCHES_ALL)

/**
 * Maps any [Throwable] surfaced by supabase-kt (RPC error, Edge Function error, network
 * exception, ...) onto the AD-9 vocabulary. Never throws.
 */
fun Throwable.toAppError(): AppError {
    val message = if (this is RestException) errorTextOf(this.error) else this.message.orEmpty()
    val match = PREFIX_PATTERN.find(message)
    return when (match?.groupValues?.get(1)) {
        "CONFLICT" -> AppError.Conflict(message)
        "INVALID" -> AppError.Invalid(message)
        "FORBIDDEN" -> AppError.Forbidden(message)
        else -> AppError.Unexpected(message)
    }
}

/**
 * The one and only user-facing error copy for this story's flows (Login/Cadastro de Médico):
 * generic and non-technical regardless of bucket, per AD-9 and the I/O matrix ("mensagem
 * genérica", "tratado como erro genérico"). Kept as a single function (rather than inlined at
 * each call site) so a future story can special-case a bucket without touching every screen.
 */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Conflict -> "Não foi possível concluir a operação. Tente novamente."
    is AppError.Invalid -> "Verifique os dados informados e tente novamente."
    is AppError.Forbidden -> "Não foi possível concluir a operação. Tente novamente."
    is AppError.Unexpected -> "Algo deu errado. Tente novamente."
}
