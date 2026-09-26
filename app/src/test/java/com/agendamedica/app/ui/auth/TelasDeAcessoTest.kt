package com.agendamedica.app.ui.auth

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.PasswordResetOutcome
import com.agendamedica.app.ui.patient.CadastroPacienteScreen
import com.agendamedica.app.ui.patient.CadastroPacienteViewModel
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Story 8.1: Criar conta, Recuperar senha, Nova senha and Cadastro de paciente follow the design prototype. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h892dp-xxhdpi")
class TelasDeAcessoTest {

    @get:Rule
    val rule = createComposeRule()

    private val auth: AuthRepository = mockk(relaxed = true)

    @Test
    fun `Criar conta shows the bar, the question and the two choice cards, paciente first`() {
        var paciente = 0
        var medico = 0
        rule.setContent { EscolhaScreen(onBack = {}, onSouMedico = { medico++ }, onSouPaciente = { paciente++ }) }
        rule.onNodeWithText("Criar conta").assertIsDisplayed()
        rule.onNodeWithText("Como você quer usar o app?").assertIsDisplayed()
        rule.onNodeWithText("Buscar médicos e agendar consultas").assertIsDisplayed()
        rule.onNodeWithText("Cadastrar meu perfil e atender pacientes").assertIsDisplayed()

        val pac = rule.onNodeWithText("Sou paciente").getUnclippedBoundsInRoot()
        val med = rule.onNodeWithText("Sou médico").getUnclippedBoundsInRoot()
        assertTrue("paciente card must come first", pac.top < med.top)

        rule.onNodeWithText("Sou paciente").performClick()
        rule.onNodeWithText("Cadastrar meu perfil e atender pacientes").performClick()
        assertEquals(1, paciente)
        assertEquals(1, medico)
    }

    @Test
    fun `Criar conta back button leads back`() {
        var voltou = 0
        rule.setContent { EscolhaScreen(onBack = { voltou++ }, onSouMedico = {}, onSouPaciente = {}) }
        rule.onNodeWithContentDescription("Voltar").performClick()
        assertEquals(1, voltou)
    }

    @Test
    fun `Recuperar senha shows the intro, the labelled e-mail field and the send button`() {
        rule.setContent { RecuperarSenhaScreen(linkExpired = false, onBackToLogin = {}, viewModel = RecuperarSenhaViewModel(auth)) }
        rule.onNodeWithText("Recuperar senha").assertIsDisplayed()
        rule.onNodeWithText("Informe seu e-mail para receber um link de redefinição de senha.").assertIsDisplayed()
        rule.onNodeWithText("E-mail").assertIsDisplayed()
        rule.onNodeWithText("voce@email.com").assertIsDisplayed()
        rule.onNodeWithText("Enviar link de recuperação").assertIsDisplayed()
    }

    @Test
    fun `Recuperar senha replaces the form by a green box and Voltar ao login after sending`() {
        coEvery { auth.requestPasswordReset(any()) } returns PasswordResetOutcome.Neutral
        var voltou = 0
        val vm = RecuperarSenhaViewModel(auth)
        rule.setContent { RecuperarSenhaScreen(linkExpired = false, onBackToLogin = { voltou++ }, viewModel = vm) }
        vm.onEmailChanged("ana@x.com")
        rule.waitForIdle()
        rule.onNodeWithText("Enviar link de recuperação").performClick()
        rule.waitForIdle()

        rule.onNodeWithText(MSG_RESET_NEUTRA).assertIsDisplayed()
        rule.onNodeWithText("Voltar ao login").assertIsDisplayed().performClick()
        assertEquals(1, voltou)
        rule.onNodeWithText("Enviar link de recuperação").assertDoesNotExist()
        rule.onNodeWithText("Informe seu e-mail para receber um link de redefinição de senha.").assertDoesNotExist()
    }

    @Test
    fun `Recuperar senha still shows the expired link warning`() {
        rule.setContent { RecuperarSenhaScreen(linkExpired = true, onBackToLogin = {}, viewModel = RecuperarSenhaViewModel(auth)) }
        rule.waitForIdle()
        rule.onNodeWithText(MSG_LINK_EXPIRADO).assertIsDisplayed()
    }

    @Test
    fun `Nova senha keeps the bar title, the labelled password field, the hint and the eye`() {
        rule.setContent { NovaSenhaScreen(onGoToLogin = {}, viewModel = NovaSenhaViewModel(auth)) }
        rule.onNode(hasText("Nova senha") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        assertEquals(2, rule.onAllNodesWithText("Nova senha").fetchSemanticsNodes().size) // bar title + label above the field
        rule.onNodeWithText("Mínimo de 6 caracteres.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Mostrar senha").assertIsDisplayed()
        rule.onNodeWithText("Salvar nova senha").assertIsDisplayed()
    }

    @Test
    fun `Nova senha bar back button and system back both leave without changing the password`() {
        val saidas = mutableListOf<Boolean>()
        rule.setContent { NovaSenhaScreen(onGoToLogin = { saidas += it }, viewModel = NovaSenhaViewModel(auth)) }
        rule.onNodeWithContentDescription("Voltar").performClick()
        rule.waitForIdle()
        assertEquals(listOf(false), saidas)

        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(listOf(false, false), saidas)
    }

    @Test
    fun `Recuperar senha shows a server failure in a warning box`() {
        coEvery { auth.requestPasswordReset(any()) } returns PasswordResetOutcome.RateLimited
        val vm = RecuperarSenhaViewModel(auth)
        rule.setContent { RecuperarSenhaScreen(linkExpired = false, onBackToLogin = {}, viewModel = vm) }
        vm.onEmailChanged("ana@x.com")
        rule.waitForIdle()
        rule.onNodeWithText("Enviar link de recuperação").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(MSG_RESET_LIMITE).assertIsDisplayed()
    }

    @Test
    fun `Cadastro de paciente has labels above, examples inside and keeps the password hint`() {
        rule.setContent {
            CadastroPacienteScreen(
                onBack = {},
                onRegistered = {},
                viewModel = CadastroPacienteViewModel(mockk(relaxed = true), mockk(relaxed = true)),
            )
        }
        rule.onNodeWithText("Cadastro de paciente").assertIsDisplayed()
        rule.onNodeWithText("Nome completo").assertIsDisplayed()
        rule.onNodeWithText("Seu nome").assertIsDisplayed()
        rule.onNodeWithText("voce@email.com").assertIsDisplayed()
        rule.onNodeWithText("Mínimo de 6 caracteres.").assertIsDisplayed()
        rule.onNodeWithText("Criar conta").assertIsDisplayed()
        assertEquals(3, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }
}
