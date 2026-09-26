package com.agendamedica.app.ui.patient

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.agendamedica.app.domain.agenda.ANTECEDENCIA_MINIMA_HORAS
import com.agendamedica.app.domain.agenda.AgendaSlot
import com.agendamedica.app.domain.agenda.DoctorDetail
import com.agendamedica.app.domain.agenda.SlotMotivo
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.Especialidade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Story 8.4: Detalhe do médico follows the design prototype (bar, doctor card, day cards, slot grid, fixed confirm bar). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h892dp-xxhdpi")
class DetalheLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private val medico = DoctorDetail(
        "d1", "Dra. Ana Ferreira", Especialidade.CARDIOLOGIA, "São Paulo", "Centro",
        listOf(Convenio.UNIMED, Convenio.AMIL), emptyList(),
    )
    private val segunda = LocalDate.of(2026, 9, 28)
    private val terca = LocalDate.of(2026, 9, 29)

    private fun slot(hora: String, motivo: SlotMotivo? = null): AgendaSlot {
        val h = LocalTime.parse(hora)
        return AgendaSlot(Instant.parse("2026-09-28T${h}:00Z").plusSeconds(3 * 3600), h, motivo)
    }

    private val pronto = DetalheMedicoUiState(
        isLoading = false,
        doctor = medico,
        dias = listOf(segunda, terca),
        selectedDia = segunda,
        slots = listOf(slot("08:00"), slot("08:45", SlotMotivo.OCUPADO), slot("09:30"), slot("10:15")),
    )

    private class Chamadas {
        var voltar = 0
        var tentar = 0
        val dias = mutableListOf<LocalDate>()
        val slots = mutableListOf<AgendaSlot>()
        val convenios = mutableListOf<Convenio>()
        var confirmar = 0
    }

    private val chamadas = Chamadas()

    private fun mostrar(estado: DetalheMedicoUiState) = rule.setContent {
        DetalheConteudo(
            state = estado,
            onBack = { chamadas.voltar++ },
            onRetry = { chamadas.tentar++ },
            onDiaSelected = { chamadas.dias += it },
            onSlotSelected = { chamadas.slots += it },
            onConvenioSelected = { chamadas.convenios += it },
            onConfirmar = { chamadas.confirmar++ },
        )
    }

    @Test
    fun `the bar says Escolher horario and goes back`() {
        mostrar(pronto)
        rule.onNode(hasText("Escolher horário") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Voltar").performClick()
        assertEquals(1, chamadas.voltar)
    }

    @Test
    fun `when rescheduling the bar says Reagendar consulta and there is no convenio choice`() {
        mostrar(pronto.copy(reagendando = true))
        rule.onNode(hasText("Reagendar consulta") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        rule.onNodeWithText("Confirmar novo horário").assertExists()
        rule.onNodeWithText("Convênio").assertDoesNotExist()
        rule.onNodeWithText("Unimed").assertIsDisplayed() // informative chips instead
    }

    @Test
    fun `the doctor card shows avatar initials hidden from screen readers, name and specialty and city`() {
        mostrar(pronto)
        rule.onNodeWithText("Dra. Ana Ferreira").assertIsDisplayed()
        rule.onNodeWithText("Cardiologia · São Paulo").assertIsDisplayed()
        rule.onNodeWithText("AF", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("AF").assertDoesNotExist()
    }

    @Test
    fun `day cards show weekday over day and mark the selected one`() {
        mostrar(pronto)
        rule.onNodeWithText("Escolha o dia").assertIsDisplayed()
        rule.onNodeWithContentDescription("Segunda, 28/09").assertIsSelected().assertIsDisplayed()
        rule.onNodeWithContentDescription("Terça, 29/09").performClick()
        assertEquals(listOf(terca), chamadas.dias)
        val minimoPx = with(rule.density) { 48.dp.toPx() }
        assertTrue(rule.onNodeWithContentDescription("Terça, 29/09").fetchSemanticsNode().touchBoundsInRoot.height >= minimoPx - 1f)
    }

    @Test
    fun `slots are in a three column grid, disabled ones say why and only enabled ones are tappable`() {
        mostrar(pronto)
        rule.onNodeWithText("Horários disponíveis").assertIsDisplayed()
        rule.onNodeWithContentDescription("08:00").assertIsEnabled().performClick()
        assertEquals(1, chamadas.slots.size)
        rule.onNodeWithContentDescription("08:45, ${SlotMotivo.OCUPADO.texto}").assertIsNotEnabled()
        val a = rule.onNodeWithContentDescription("08:00").getUnclippedBoundsInRoot()
        val c = rule.onNodeWithContentDescription("09:30").getUnclippedBoundsInRoot()
        val d = rule.onNodeWithContentDescription("10:15").getUnclippedBoundsInRoot()
        assertTrue("09:30 is in the same row as 08:00", a.top == c.top)
        assertTrue("10:15 wraps to the next row", d.top > a.top)
    }

    @Test
    fun `the minimum notice note and the section titles are shown`() {
        mostrar(pronto)
        rule.onNodeWithText("Agendamento exige mínimo de ${ANTECEDENCIA_MINIMA_HORAS}h de antecedência.").assertIsDisplayed()
        rule.onNode(hasText("Escolha o dia") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
    }

    @Test
    fun `the convenio selector is kept when booking and reports the choice`() {
        mostrar(pronto.copy(selectedConvenio = Convenio.UNIMED))
        rule.onNodeWithText("Convênio").assertIsDisplayed()
        rule.onNodeWithText("Amil").performClick()
        assertEquals(listOf(Convenio.AMIL), chamadas.convenios)
    }

    @Test
    fun `the confirm bar is fixed under the content, disabled until a slot and convenio are chosen`() {
        mostrar(pronto)
        rule.onNodeWithText("Confirmar agendamento").assertIsNotEnabled()
        rule.onNodeWithText("Confirmar agendamento").performClick()
        assertEquals(0, chamadas.confirmar)
    }

    @Test
    fun `with everything chosen Confirmar agendamento works and stays visible while the content scrolls`() {
        val completo = pronto.copy(selectedSlot = slot("08:00").start, selectedConvenio = Convenio.UNIMED)
        mostrar(completo)
        rule.onNodeWithText("Confirmar agendamento").assertIsEnabled().performClick()
        assertEquals(1, chamadas.confirmar)
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Convênio"))
        rule.onNodeWithText("Confirmar agendamento").assertIsDisplayed()
    }

    @Test
    fun `while submitting the button says Agendando`() {
        mostrar(pronto.copy(isSubmitting = true))
        rule.onNodeWithText("Agendando...").assertIsNotEnabled()
    }

    @Test
    fun `a booking message shows in the fixed bar, right above the confirm button`() {
        mostrar(pronto.copy(bookingMessage = MSG_ANTECEDENCIA))
        rule.onNodeWithText(MSG_ANTECEDENCIA).assertIsDisplayed()
        val mensagem = rule.onNodeWithText(MSG_ANTECEDENCIA).getUnclippedBoundsInRoot()
        val botao = rule.onNodeWithText("Confirmar agendamento").getUnclippedBoundsInRoot()
        assertTrue("message sits above the button", mensagem.bottom <= botao.top)
    }

    @Test
    @Config(qualifiers = "w320dp-h400dp-xxhdpi")
    fun `on a small screen the confirm bar stays pinned below the scrolling content`() {
        mostrar(pronto.copy(selectedSlot = slot("08:00").start, selectedConvenio = Convenio.UNIMED))
        rule.onNodeWithText("Confirmar agendamento").assertIsDisplayed()
        rule.onNodeWithText("Convênio").assertIsNotDisplayed() // does not fit: the body scrolls, the bar does not
        val barra = rule.onNodeWithText("Confirmar agendamento").getUnclippedBoundsInRoot()
        val sobreposto = rule.onNodeWithText("Horários disponíveis").getUnclippedBoundsInRoot()
        assertTrue(barra.top >= sobreposto.bottom)
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Convênio"))
        rule.onNodeWithText("Convênio").assertIsDisplayed()
        rule.onNodeWithText("Confirmar agendamento").assertIsDisplayed()
    }

    @Test
    fun `no days shows the no-days message`() {
        mostrar(pronto.copy(dias = emptyList(), selectedDia = null, slots = emptyList()))
        rule.onNodeWithText(MSG_SEM_DIAS).assertIsDisplayed()
    }

    @Test
    fun `a day without slots says so`() {
        mostrar(pronto.copy(slots = emptyList()))
        rule.onNodeWithText(MSG_SEM_HORARIOS).assertIsDisplayed()
    }

    @Test
    fun `loading shows a progress indicator and no confirm bar`() {
        mostrar(DetalheMedicoUiState(isLoading = true))
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
        rule.onNodeWithText("Confirmar agendamento").assertDoesNotExist()
    }

    @Test
    fun `an error shows its message, retries and has no confirm bar`() {
        mostrar(DetalheMedicoUiState(isLoading = false, errorMessage = "Algo deu errado."))
        rule.onNodeWithText("Algo deu errado.").assertIsDisplayed()
        rule.onNodeWithText("Tentar novamente").performClick()
        assertEquals(1, chamadas.tentar)
        rule.onNodeWithText("Confirmar agendamento").assertDoesNotExist()
    }
}
