package com.agendamedica.app.ui.patient

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Story 8.4: Confirmação follows the design prototype (bar "Confirmado", check, title, one summary card, two buttons). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h892dp-xxhdpi")
class ConfirmacaoScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var voltarBusca = 0
    private var verConsultas = 0

    private fun mostrar() = rule.setContent {
        ConfirmacaoScreen(
            doctorName = "Dra. Ana Ferreira",
            especialidade = "Cardiologia",
            startMillis = Instant.parse("2026-09-29T13:00:00Z").toEpochMilli(),
            convenio = "Unimed",
            onVoltarBusca = { voltarBusca++ },
            onVerConsultas = { verConsultas++ },
        )
    }

    @Test
    fun `the bar says Confirmado and has no back button`() {
        mostrar()
        rule.onNode(hasText("Confirmado") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Voltar").assertDoesNotExist()
    }

    @Test
    fun `the title says the appointment is scheduled`() {
        mostrar()
        rule.onNode(hasText("Consulta agendada!") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
    }

    @Test
    fun `one summary card shows doctor, specialty, date and time, and convenio`() {
        mostrar()
        rule.onNodeWithText("Dra. Ana Ferreira").assertIsDisplayed()
        rule.onNodeWithText("Cardiologia").assertIsDisplayed()
        rule.onNodeWithText("29/09 às 10:00").assertIsDisplayed()
        rule.onNodeWithText("Convênio: Unimed").assertIsDisplayed()
    }

    @Test
    fun `the two buttons go to Minhas consultas and back to the search`() {
        mostrar()
        rule.onNodeWithText("Ver minhas consultas").performClick()
        rule.onNodeWithText("Buscar outro médico").performClick()
        assertEquals(1, verConsultas)
        assertEquals(1, voltarBusca)
    }
}
