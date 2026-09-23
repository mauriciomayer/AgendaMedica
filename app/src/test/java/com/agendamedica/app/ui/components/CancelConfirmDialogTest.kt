package com.agendamedica.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Story 6.4's blocking dialog, exercised for real (spec-7-3 spike): only Sim/Não act, back does nothing. */
@RunWith(RobolectricTestRunner::class)
class CancelConfirmDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private var sim = 0
    private var nao = 0

    private fun show(cancelando: Boolean = false) = rule.setContent {
        CancelConfirmDialog(
            quem = "Dra. Helena Duarte, 28/09/2026 às 09:30",
            cancelando = cancelando,
            onSim = { sim++ },
            onNao = { nao++ },
        )
    }

    @Test
    fun `shows the question and names the appointment`() {
        show()
        rule.onNodeWithText("Cancelar esta consulta?").assertIsDisplayed()
        rule.onNodeWithText("Dra. Helena Duarte, 28/09/2026 às 09:30").assertIsDisplayed()
    }

    @Test
    fun `Sim and Nao call their own callbacks`() {
        show()
        rule.onNodeWithContentDescription("Sim, cancelar a consulta com Dra. Helena Duarte, 28/09/2026 às 09:30").performClick()
        assertEquals(1, sim)
        assertEquals(0, nao)
        rule.onNodeWithContentDescription("Não cancelar a consulta com Dra. Helena Duarte, 28/09/2026 às 09:30").performClick()
        assertEquals(1, nao)
    }

    @Test
    fun `system back neither closes the dialog nor calls a callback`() {
        show()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText("Cancelar esta consulta?").assertIsDisplayed()
        assertEquals(0, sim)
        assertEquals(0, nao)
    }

    @Test
    fun `while cancelling, Sim reads Cancelando and both buttons are disabled`() {
        show(cancelando = true)
        rule.onNodeWithText("Cancelando...").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sim, cancelar a consulta com Dra. Helena Duarte, 28/09/2026 às 09:30").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Não cancelar a consulta com Dra. Helena Duarte, 28/09/2026 às 09:30").assertIsNotEnabled()
    }
}
