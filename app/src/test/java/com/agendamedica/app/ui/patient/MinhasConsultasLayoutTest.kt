package com.agendamedica.app.ui.patient

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.agendamedica.app.data.repository.MinhaConsulta
import com.agendamedica.app.ui.components.MSG_JANELA_24H
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Story 8.3: Minhas consultas follows the design prototype (bar with Sair, compact cards, "+ Nova consulta" always at the end). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h892dp-xxhdpi")
class MinhasConsultasLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private fun consulta(id: String, medico: String, esp: String, inicio: String, convenio: String = "Unimed") =
        MinhaConsulta(id, "d-$id", medico, esp, Instant.parse(inicio), convenio)

    private val dois = MinhasConsultasUiState(
        isLoading = false,
        consultas = listOf(
            ConsultaItem(consulta("a1", "Dra. Helena Duarte", "Dermatologia", "2026-09-29T13:00:00Z"), bloqueada = false),
            ConsultaItem(consulta("a2", "Dr. Ricardo Alves", "Cardiologia", "2026-09-30T17:30:00Z", "Amil"), bloqueada = true),
        ),
    )

    private class Chamadas {
        var voltar = 0
        var sair = 0
        var nova = 0
        var tentar = 0
        val cancelar = mutableListOf<String>()
        val reagendar = mutableListOf<Pair<String, String>>()
        var sim = 0
        var nao = 0
    }

    private val chamadas = Chamadas()

    private fun mostrar(estado: MinhasConsultasUiState) = rule.setContent {
        MinhasConsultasConteudo(
            state = estado,
            onBack = { chamadas.voltar++ },
            onSair = { chamadas.sair++ },
            onNovaConsulta = { chamadas.nova++ },
            onRetry = { chamadas.tentar++ },
            onCancelar = { chamadas.cancelar += it },
            onReagendar = { doctor, id -> chamadas.reagendar += doctor to id },
            onCancelarSim = { chamadas.sim++ },
            onCancelarNao = { chamadas.nao++ },
        )
    }

    @Test
    fun `the bar has the title, the back button and Sair on the right`() {
        mostrar(dois)
        rule.onNode(hasText("Minhas consultas") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Voltar").performClick()
        rule.onNodeWithText("Sair").performClick()
        assertEquals(1, chamadas.voltar)
        assertEquals(1, chamadas.sair)
        val titulo = rule.onNodeWithText("Minhas consultas").getUnclippedBoundsInRoot()
        val sair = rule.onNodeWithText("Sair").getUnclippedBoundsInRoot()
        assertTrue("Sair must sit to the right of the title", sair.left >= titulo.right)
        val minimoPx = with(rule.density) { 48.dp.toPx() }
        assertTrue(rule.onNodeWithText("Sair").fetchSemanticsNode().touchBoundsInRoot.height >= minimoPx - 1f)
    }

    @Test
    fun `a card shows doctor, specialty, badge and a one-line date, time and convenio`() {
        mostrar(dois)
        rule.onNodeWithText("Dra. Helena Duarte").assertIsDisplayed()
        rule.onNodeWithText("Dermatologia").assertIsDisplayed()
        rule.onNodeWithText("Confirmada").assertIsDisplayed()
        rule.onNodeWithText("29/09 às 10:00 · Unimed").assertIsDisplayed()
        rule.onNodeWithText("30/09 às 14:30 · Amil").assertIsDisplayed()
    }

    @Test
    fun `buttons are Cancelar then Reagendar and each names its appointment`() {
        mostrar(dois)
        val cancelar = rule.onNodeWithContentDescription("Cancelar a consulta com Dra. Helena Duarte, 29/09/2026 às 10:00").getUnclippedBoundsInRoot()
        val reagendar = rule.onNodeWithContentDescription("Reagendar a consulta com Dra. Helena Duarte, 29/09/2026 às 10:00").getUnclippedBoundsInRoot()
        assertTrue("Cancelar comes first (left)", cancelar.right <= reagendar.left)
        rule.onNodeWithContentDescription("Cancelar a consulta com Dra. Helena Duarte, 29/09/2026 às 10:00").performClick()
        rule.onNodeWithContentDescription("Reagendar a consulta com Dra. Helena Duarte, 29/09/2026 às 10:00").performClick()
        assertEquals(listOf("a1"), chamadas.cancelar)
        assertEquals(listOf("d-a1" to "a1"), chamadas.reagendar)
    }

    @Test
    fun `Cancelar and Reagendar keep a 48dp touch target`() {
        mostrar(dois)
        val minimoPx = with(rule.density) { 48.dp.toPx() }
        listOf("Cancelar", "Reagendar").forEach { acao ->
            val botao = rule.onNodeWithContentDescription("$acao a consulta com Dra. Helena Duarte, 29/09/2026 às 10:00")
            assertTrue("$acao touch target", botao.fetchSemanticsNode().touchBoundsInRoot.height >= minimoPx - 1f)
        }
    }

    @Test
    fun `a blocked card says so in a tinted box and disables both buttons`() {
        mostrar(dois)
        rule.onNodeWithText("Bloqueada").assertIsDisplayed()
        rule.onAllNodesWithText(MSG_JANELA_24H).assertCountEquals(1) // only the blocked card carries it
        rule.onNodeWithContentDescription("Cancelar a consulta com Dr. Ricardo Alves, 30/09/2026 às 14:30").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Reagendar a consulta com Dr. Ricardo Alves, 30/09/2026 às 14:30").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Cancelar a consulta com Dra. Helena Duarte, 29/09/2026 às 10:00").assertIsEnabled()
    }

    @Test
    fun `Nova consulta is always at the end of the list`() {
        mostrar(dois)
        rule.onNodeWithText("+ Nova consulta").performClick()
        assertEquals(1, chamadas.nova)
        val ultimo = rule.onNodeWithText("30/09 às 14:30 · Amil").getUnclippedBoundsInRoot()
        val nova = rule.onNodeWithText("+ Nova consulta").getUnclippedBoundsInRoot()
        assertTrue(nova.top >= ultimo.bottom)
    }

    @Test
    fun `with many appointments Nova consulta is still reachable by scrolling`() {
        val muitas = (1..12).map {
            ConsultaItem(consulta("m$it", "Dr. Fulano $it", "Clínica geral", "2026-10-01T13:00:00Z"), bloqueada = false)
        }
        mostrar(MinhasConsultasUiState(isLoading = false, consultas = muitas))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("+ Nova consulta"))
        rule.onNodeWithText("+ Nova consulta").assertIsDisplayed()
    }

    @Test
    fun `without appointments the message and Nova consulta are shown`() {
        mostrar(MinhasConsultasUiState(isLoading = false))
        rule.onNodeWithText(MSG_SEM_CONSULTAS).assertIsDisplayed()
        rule.onNodeWithText("+ Nova consulta").assertIsDisplayed().performClick()
        assertEquals(1, chamadas.nova)
    }

    @Test
    fun `an action failure shows above the cards`() {
        mostrar(dois.copy(actionMessage = "Não foi possível cancelar."))
        rule.onNodeWithText("Não foi possível cancelar.").assertIsDisplayed()
        val aviso = rule.onNodeWithText("Não foi possível cancelar.").getUnclippedBoundsInRoot()
        val primeiro = rule.onNodeWithText("Dra. Helena Duarte").getUnclippedBoundsInRoot()
        assertTrue(aviso.bottom <= primeiro.top)
    }

    @Test
    fun `an error shows its message and Tentar novamente retries, with no cards`() {
        mostrar(MinhasConsultasUiState(isLoading = false, errorMessage = "Algo deu errado."))
        rule.onNodeWithText("Algo deu errado.").assertIsDisplayed()
        rule.onNodeWithText("Tentar novamente").performClick()
        assertEquals(1, chamadas.tentar)
        rule.onNodeWithText("+ Nova consulta").assertDoesNotExist()
    }

    @Test
    fun `while loading only a progress indicator is shown`() {
        mostrar(MinhasConsultasUiState(isLoading = true))
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
        rule.onNodeWithText(MSG_SEM_CONSULTAS).assertDoesNotExist()
        rule.onNodeWithText("+ Nova consulta").assertDoesNotExist()
    }

    @Test
    fun `the confirmation dialog appears for the chosen appointment and Sim and Nao are wired`() {
        mostrar(dois.copy(confirmandoId = "a1"))
        rule.onNodeWithText("Cancelar esta consulta?").assertIsDisplayed()
        rule.onNodeWithText("Dra. Helena Duarte, 29/09/2026 às 10:00").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sim, cancelar a consulta com Dra. Helena Duarte, 29/09/2026 às 10:00").performClick()
        rule.onNodeWithContentDescription("Não cancelar a consulta com Dra. Helena Duarte, 29/09/2026 às 10:00").performClick()
        assertEquals(1, chamadas.sim)
        assertEquals(1, chamadas.nao)
        rule.onAllNodesWithContentDescription("Sim, cancelar a consulta com Dr. Ricardo Alves, 30/09/2026 às 14:30").assertCountEquals(0)
    }
}
