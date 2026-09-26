package com.agendamedica.app.ui.doctor

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.agendamedica.app.domain.model.DiaSemana
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.model.Localizacao
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Story 8.5: Cadastro de médico follows the design prototype (bar with subtitle, CRM box, labels above, dropdowns, chips). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h892dp-xxhdpi")
class CadastroMedicoLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private var voltar = 0
    private val vm = CadastroMedicoViewModel(mockk(relaxed = true), mockk(relaxed = true))

    private fun mostrar() = rule.setContent {
        CadastroMedicoScreen(onBack = { voltar++ }, onRegistered = {}, viewModel = vm)
    }

    private fun rolarPara(texto: String) {
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText(texto))
    }

    @Test
    fun `the bar says Cadastro de medico with its subtitle and goes back`() {
        mostrar()
        rule.onNode(hasText("Cadastro de médico") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        rule.onNodeWithText("Autocadastro, sem validação de CRM").assertIsDisplayed()
        rule.onNodeWithContentDescription("Voltar").performClick()
        assertEquals(1, voltar)
    }

    @Test
    fun `the CRM notice is in a box under the bar`() {
        mostrar()
        val aviso = rule.onNodeWithText("Sem validação de CRM — seu perfil fica visível na busca assim que você concluir o cadastro.")
        aviso.assertIsDisplayed()
        assertTrue(aviso.getUnclippedBoundsInRoot().top >= rule.onNodeWithText("Autocadastro, sem validação de CRM").getUnclippedBoundsInRoot().bottom)
    }

    @Test
    fun `text fields have their labels above and the prototype examples inside`() {
        mostrar()
        rule.onNodeWithText("Nome completo").assertIsDisplayed()
        rule.onNodeWithText("Dr(a). Nome Sobrenome").assertIsDisplayed()
        rule.onNodeWithText("voce@email.com").assertIsDisplayed()
        rule.onNodeWithText("Mínimo de 6 caracteres.").assertIsDisplayed()
        val rotulo = rule.onNodeWithText("Nome completo").getUnclippedBoundsInRoot()
        val campo = rule.onNodeWithText("Dr(a). Nome Sobrenome").getUnclippedBoundsInRoot()
        assertTrue("label sits above the field", rotulo.bottom <= campo.top)
    }

    @Test
    fun `dropdowns are announced with their label and value, and choosing updates the form`() {
        mostrar()
        rule.onNodeWithText("Especialidade").assertIsDisplayed()
        val campo = rule.onNodeWithContentDescription("Especialidade, nenhum selecionado")
        assertEquals(Role.DropdownList, campo.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        campo.performClick()
        rule.onNodeWithText("Cardiologia").performClick()
        assertEquals(Especialidade.CARDIOLOGIA, vm.uiState.value.especialidade)
        rule.onNodeWithContentDescription("Especialidade, Cardiologia").assertIsDisplayed()
    }

    @Test
    fun `Localizacao is kept and works`() {
        mostrar()
        rolarPara("Localização")
        rule.onNodeWithContentDescription("Localização, nenhum selecionado").performClick()
        val primeira = Localizacao.entries.first()
        rule.onNodeWithText(primeira.label).performClick()
        assertEquals(primeira, vm.uiState.value.localizacao)
    }

    @Test
    fun `Inicio and Fim sit side by side with their defaults`() {
        mostrar()
        rolarPara("Criar perfil e começar a atender")
        rule.onNodeWithContentDescription("Início, 08:00").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fim, 18:00").assertIsDisplayed()
        val inicio = rule.onNodeWithContentDescription("Início, 08:00").getUnclippedBoundsInRoot()
        val fim = rule.onNodeWithContentDescription("Fim, 18:00").getUnclippedBoundsInRoot()
        assertEquals(inicio.top, fim.top)
        assertTrue(inicio.right <= fim.left)
    }

    @Test
    fun `convenio and all seven weekday chips are there and toggle`() {
        mostrar()
        rolarPara("Dias de atendimento")
        rule.onNodeWithText("Convênios atendidos").assertExists()
        rule.onNodeWithText("Dias de atendimento").assertIsDisplayed()
        DiaSemana.ordered.forEach { rule.onAllNodesWithText(it.label).fetchSemanticsNodes().let { nos -> assertEquals(it.label, 1, nos.size) } }
        rolarPara("Segunda")
        rule.onNodeWithText("Segunda").assertIsNotSelected()
        rule.onNodeWithText("Segunda").performClick()
        assertTrue(DiaSemana.SEGUNDA in vm.uiState.value.diasSelecionados)
        rule.onNodeWithText("Segunda").assertIsSelected()
    }

    @Test
    fun `the submit button starts disabled`() {
        mostrar()
        rule.onNodeWithText("Criar perfil e começar a atender").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-xxhdpi")
    fun `on a small screen the form scrolls to the submit button`() {
        mostrar()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("Criar perfil e começar a atender"))
        rule.onNodeWithText("Criar perfil e começar a atender").assertIsDisplayed()
    }
}
