package com.agendamedica.app.ui.doctor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.agendamedica.app.data.repository.ConsultaDoMedico
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.DiaSemana
import com.agendamedica.app.domain.model.DoctorProfile
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.model.ScheduleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalTime

/** Story 8.6: Minha agenda follows the design prototype (bar with Sair, profile card, blue notice, "Próximas consultas"). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h892dp-xxhdpi")
class MinhaAgendaLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private val perfil = DoctorProfile(
        name = "Dr. Ricardo Alves",
        especialidade = Especialidade.CARDIOLOGIA,
        convenios = listOf(Convenio.UNIMED, Convenio.AMIL),
        schedule = listOf(ScheduleBlock(DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0))),
    )

    private val pronto = MinhaAgendaUiState(
        isLoading = false,
        profile = perfil,
        doctorId = "d1",
        consultasLoading = false,
        consultas = listOf(
            ConsultaMedicoItem(ConsultaDoMedico("a1", "Ana Lima", Instant.parse("2026-09-29T13:00:00Z"), "Unimed"), bloqueada = false),
            ConsultaMedicoItem(ConsultaDoMedico("a2", "Mariana Costa", Instant.parse("2026-09-30T17:30:00Z"), "Amil"), bloqueada = true),
        ),
    )

    private var sim = 0
    private var nao = 0
    private var sair = 0
    private var tentar = 0
    private val cancelar = mutableListOf<String>()
    private val reagendar = mutableListOf<Pair<String, String>>()

    private fun mostrar(estado: MinhaAgendaUiState) = rule.setContent {
        MinhaAgendaConteudo(
            uiState = estado,
            onSair = { sair++ },
            onRetryConsultas = { tentar++ },
            onCancelar = { cancelar += it },
            onReagendar = { doctor, id -> reagendar += doctor to id },
            onCancelarSim = { sim++ },
            onCancelarNao = { nao++ },
        )
    }

    @Test
    fun `the bar says Minha agenda with Sair on the right`() {
        mostrar(pronto)
        rule.onNode(hasText("Minha agenda") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        rule.onNodeWithText("Sair").performClick()
        assertEquals(1, sair)
        val titulo = rule.onNodeWithText("Minha agenda").getUnclippedBoundsInRoot()
        assertTrue(rule.onNodeWithText("Sair").getUnclippedBoundsInRoot().left >= titulo.right)
        rule.onNodeWithContentDescription("Voltar").assertDoesNotExist()
    }

    @Test
    fun `the profile card shows name, specialty, convenios and keeps the schedule lines`() {
        mostrar(pronto)
        rule.onNodeWithText("Dr. Ricardo Alves").assertIsDisplayed()
        rule.onNodeWithText("Cardiologia").assertIsDisplayed()
        rule.onNodeWithText("Unimed").assertIsDisplayed()
        rule.onNodeWithText("Segunda: 08:00 - 12:00").assertIsDisplayed()
    }

    @Test
    fun `the blue notice about double booking is under the card and is not a live region`() {
        mostrar(pronto)
        val aviso = rule.onNodeWithText("Conflitos de horário", substring = true)
        aviso.assertIsDisplayed()
        assertNull(aviso.fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
        val cartao = rule.onNodeWithText("Segunda: 08:00 - 12:00").getUnclippedBoundsInRoot()
        assertTrue(aviso.getUnclippedBoundsInRoot().top >= cartao.bottom)
    }

    @Test
    fun `Proximas consultas is a section title above the appointment cards`() {
        mostrar(pronto)
        rule.onNode(hasText("Próximas consultas") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        rule.onNodeWithText("Ana Lima").assertIsDisplayed()
        rule.onNodeWithText("29/09 às 10:00 · Unimed").assertIsDisplayed()
        assertTrue(
            rule.onNodeWithText("Próximas consultas").getUnclippedBoundsInRoot().bottom <=
                rule.onNodeWithText("Ana Lima").getUnclippedBoundsInRoot().top,
        )
    }

    @Test
    fun `cards keep Cancelar and Reagendar and the 24h block`() {
        mostrar(pronto)
        rule.onNodeWithContentDescription("Cancelar a consulta com Ana Lima, 29/09/2026 às 10:00").performClick()
        rule.onNodeWithContentDescription("Reagendar a consulta com Ana Lima, 29/09/2026 às 10:00").performClick()
        assertEquals(listOf("a1"), cancelar)
        assertEquals(listOf("d1" to "a1"), reagendar)
        rule.onNodeWithText("Bloqueada").assertExists()
        rule.onNodeWithContentDescription("Cancelar a consulta com Mariana Costa, 30/09/2026 às 14:30").assertIsNotEnabled()
    }

    @Test
    fun `the confirmation dialog names the patient and Sim and Nao are wired`() {
        mostrar(pronto.copy(confirmandoId = "a1"))
        rule.onNodeWithText("Cancelar esta consulta?").assertIsDisplayed()
        rule.onNodeWithText("Ana Lima, 29/09/2026 às 10:00").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sim, cancelar a consulta com Ana Lima, 29/09/2026 às 10:00").performClick()
        rule.onNodeWithContentDescription("Não cancelar a consulta com Ana Lima, 29/09/2026 às 10:00").performClick()
        assertEquals(1, sim)
        assertEquals(1, nao)
    }

    @Test
    fun `an empty agenda says so`() {
        mostrar(pronto.copy(consultas = emptyList()))
        rule.onNodeWithText(MSG_AGENDA_VAZIA).assertIsDisplayed()
    }

    @Test
    fun `an action failure shows in a live region box`() {
        mostrar(pronto.copy(actionMessage = "Não foi possível cancelar."))
        val caixa = rule.onNodeWithText("Não foi possível cancelar.")
        caixa.assertIsDisplayed()
        assertEquals(LiveRegionMode.Polite, caixa.fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `an appointments error keeps the profile and retries`() {
        mostrar(pronto.copy(consultas = emptyList(), consultasErro = "Não foi possível carregar."))
        rule.onNodeWithText("Dr. Ricardo Alves").assertIsDisplayed()
        rule.onNodeWithText("Não foi possível carregar.").assertIsDisplayed()
        rule.onNodeWithText("Tentar novamente").performClick()
        assertEquals(1, tentar)
    }

    @Test
    fun `loading shows a progress indicator`() {
        mostrar(MinhaAgendaUiState(isLoading = true))
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
    }

    @Test
    fun `a profile error shows its message`() {
        mostrar(MinhaAgendaUiState(isLoading = false, errorMessage = "Algo deu errado."))
        rule.onNodeWithText("Algo deu errado.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-xxhdpi")
    fun `on a small screen the list scrolls to the last appointment`() {
        val muitas = (1..10).map {
            ConsultaMedicoItem(ConsultaDoMedico("m$it", "Paciente $it", Instant.parse("2026-10-01T13:00:00Z"), "Unimed"), false)
        }
        mostrar(pronto.copy(consultas = muitas))
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("Paciente 10"))
        rule.onNodeWithText("Paciente 10").assertIsDisplayed()
    }
}
