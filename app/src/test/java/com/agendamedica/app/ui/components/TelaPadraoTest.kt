package com.agendamedica.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The shared screen shell of the design prototype (Story 8.1): white bar, title, subtitle, back button, actions. */
@RunWith(RobolectricTestRunner::class)
// NATIVE graphics: real text measurement, needed to see a long title wrap.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h892dp-xxhdpi")
class TelaPadraoTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the bar shows the title as a heading and the optional subtitle`() {
        rule.setContent { TelaPadrao(title = "Agende", subtitle = "Encontre um médico e agende") { Text("corpo") } }
        rule.onNode(hasText("Agende") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        rule.onNodeWithText("Encontre um médico e agende").assertIsDisplayed()
        rule.onNodeWithText("corpo").assertIsDisplayed()
    }

    @Test
    fun `without onBack there is no back button`() {
        rule.setContent { TelaPadrao(title = "Sem voltar") { Text("corpo") } }
        rule.onNodeWithContentDescription("Voltar").assertDoesNotExist()
    }

    @Test
    fun `with onBack the button is announced and calls back`() {
        var voltou = 0
        rule.setContent { TelaPadrao(title = "Com voltar", onBack = { voltou++ }) { Text("corpo") } }
        rule.onNodeWithContentDescription("Voltar").assertIsDisplayed().performClick()
        assertEquals(1, voltou)
    }

    @Test
    fun `the back button has a 48dp touch target`() {
        rule.setContent { TelaPadrao(title = "Com voltar", onBack = {}) { Text("corpo") } }
        val minimoPx = with(rule.density) { 48.dp.toPx() }
        val toque = rule.onNodeWithContentDescription("Voltar").fetchSemanticsNode().touchBoundsInRoot
        assertTrue("touch target is ${toque.height}px x ${toque.width}px", toque.height >= minimoPx - 1f && toque.width >= minimoPx - 1f)
    }

    @Test
    fun `actions appear on the right side of the bar`() {
        rule.setContent { TelaPadraoComAcao() }
        val sair = rule.onNodeWithText("Sair").getUnclippedBoundsInRoot()
        val titulo = rule.onNodeWithText("Minhas consultas").getUnclippedBoundsInRoot()
        val raiz = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("action must sit in the right half of the bar", (sair.left + sair.right).value / 2 > (raiz.left + raiz.right).value / 2)
        assertTrue("action must not overlap the title", sair.left >= titulo.right)
        assertTrue("action must share the title's vertical band", sair.top < titulo.bottom && sair.bottom > titulo.top)
    }

    @Composable
    private fun TelaPadraoComAcao() {
        TelaPadrao(title = "Minhas consultas", onBack = {}, actions = { Text("Sair") }) { Text("corpo") }
    }

    @Test
    fun `a long title wraps to two lines instead of being cut off`() {
        val longo = "Um título bem longo para uma tela de teste que precisa quebrar linha"
        rule.setContent { TelaPadrao(title = longo, onBack = {}) { Text("corpo") } }
        val altura = rule.onNodeWithText(longo).getUnclippedBoundsInRoot()
        assertTrue("title should wrap (taller than one 22sp line)", (altura.bottom - altura.top).value > 26f)
    }

    @Test
    fun `a body taller than the screen scrolls and keeps every child reachable`() {
        rule.setContent {
            TelaPadrao(title = "Longa") {
                repeat(40) { Text("linha $it") }
            }
        }
        rule.onNodeWithText("linha 39").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the info box is a live region for every tone`() {
        rule.setContent {
            Column {
                InfoBox(text = "sucesso", tom = TomInfoBox.SUCESSO)
                InfoBox(text = "aviso", tom = TomInfoBox.AVISO)
                InfoBox(text = "info", tom = TomInfoBox.INFO)
            }
        }
        for (texto in listOf("sucesso", "aviso", "info")) {
            rule.onNodeWithText(texto).assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        }
    }

    @Test
    fun `a choice card is one tappable card and a plain card is not clickable`() {
        var cliques = 0
        rule.setContent {
            Column {
                CartaoEscolha(titulo = "Sou paciente", descricao = "Buscar médicos e agendar consultas", onClick = { cliques++ })
                AppCard { Text("cartão simples") }
            }
        }
        rule.onNodeWithText("Sou paciente").performClick()
        rule.onNodeWithText("Buscar médicos e agendar consultas").performClick()
        assertEquals(2, cliques)
        rule.onNodeWithText("cartão simples").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }
}
