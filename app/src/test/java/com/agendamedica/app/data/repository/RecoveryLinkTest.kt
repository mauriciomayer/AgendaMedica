package com.agendamedica.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryLinkTest {
    @Test
    fun `valid recovery fragment is ready`() {
        assertEquals(
            RecoveryLinkKind.READY,
            classifyRecoveryFragment("access_token=abc&expires_in=3600&refresh_token=x&token_type=bearer&type=recovery"),
        )
    }

    @Test
    fun `error fragment is expired`() {
        assertEquals(
            RecoveryLinkKind.EXPIRED,
            classifyRecoveryFragment("error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired"),
        )
    }

    @Test
    fun `missing token, other type or empty fragment is ignored`() {
        assertEquals(RecoveryLinkKind.IGNORE, classifyRecoveryFragment("type=recovery"))
        assertEquals(RecoveryLinkKind.IGNORE, classifyRecoveryFragment("access_token=abc&type=signup"))
        assertEquals(RecoveryLinkKind.IGNORE, classifyRecoveryFragment(""))
        assertEquals(RecoveryLinkKind.IGNORE, classifyRecoveryFragment(null))
    }

    @Test
    fun `error wins even when a token is present`() {
        assertEquals(
            RecoveryLinkKind.EXPIRED,
            classifyRecoveryFragment("access_token=abc&type=recovery&error=access_denied"),
        )
    }
}

class RecoveryLinkDecisionTest {
    private val ready = "access_token=abc&refresh_token=r&type=recovery"
    private val expired = "error=access_denied&error_code=otp_expired"

    @Test
    fun `wrong scheme or host is ignored even with a valid fragment`() {
        assertEquals(RecoveryLinkKind.IGNORE, classifyRecoveryLink("https", "reset-password", ready, null))
        assertEquals(RecoveryLinkKind.IGNORE, classifyRecoveryLink("agendamedica", "outro", ready, null))
        assertEquals(RecoveryLinkKind.IGNORE, classifyRecoveryLink(null, null, ready, null))
    }

    @Test
    fun `valid fragment on the right link is ready`() {
        assertEquals(RecoveryLinkKind.READY, classifyRecoveryLink("agendamedica", "reset-password", ready, null))
    }

    @Test
    fun `error in the fragment or in the query string is an expired link`() {
        assertEquals(RecoveryLinkKind.EXPIRED, classifyRecoveryLink("agendamedica", "reset-password", expired, null))
        assertEquals(RecoveryLinkKind.EXPIRED, classifyRecoveryLink("agendamedica", "reset-password", null, expired))
    }

    @Test
    fun `plain launch of the link with no payload is ignored`() {
        assertEquals(RecoveryLinkKind.IGNORE, classifyRecoveryLink("agendamedica", "reset-password", null, null))
    }
}
