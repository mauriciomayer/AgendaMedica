package com.agendamedica.app.ui.auth

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.agendamedica.app.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Stories 6.6 and 6.7: the Login screen follows the design prototype — white header ("Entrar" + role
 * subtitle), logo (88dp, centered, decorative), centered pill role selector, labelled fields with an
 * example inside, message boxes, "Entrar" and the centered links. Phone-sized screen, like the prototype.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h892dp-xxhdpi")
class LoginScreenLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private val auth: AuthRepository = mockk(relaxed = true)

    private fun novoVm() = LoginViewModel(auth, mockk(relaxed = true))

    private fun mostrarLogin(vm: LoginViewModel = novoVm()): LoginViewModel {
        rule.setContent { LoginScreen({}, {}, {}, {}, viewModel = vm) }
        return vm
    }

    // Labels sit above the fields as plain text, so the fields are found by order: e-mail first, password second.
    private fun campoEmail(): SemanticsNodeInteraction = rule.onAllNodes(hasSetTextAction())[0]

    private fun campoSenha(): SemanticsNodeInteraction = rule.onAllNodes(hasSetTextAction())[1]

    // The header title and the submit button are both "Entrar"; the button is the clickable one.
    private fun botaoEntrar() = rule.onNode(hasText("Entrar") and hasClickAction())

    @Test
    fun `header shows the title Entrar as a heading and a subtitle for the selected role`() {
        val vm = novoVm().also { it.onRoleSelected(LoginRole.PACIENTE) }
        mostrarLogin(vm)
        rule.onAllNodesWithText("Entrar").assertCountEquals(2)
        rule.onNode(hasText("Entrar") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        rule.onNodeWithText("Acesse sua conta de paciente").assertIsDisplayed()

        rule.onNodeWithText("Médico").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Acesse sua conta de médico").assertIsDisplayed()
        rule.onNodeWithText("Acesse sua conta de paciente").assertDoesNotExist()
    }

    @Test
    fun `role selector is a pill with the active option selected and switching updates the view model`() {
        val vm = novoVm().also { it.onRoleSelected(LoginRole.MEDICO) }
        mostrarLogin(vm)
        rule.onNodeWithText("Médico").assertIsSelected()
        rule.onNodeWithText("Paciente").assertIsNotSelected()

        rule.onNodeWithText("Paciente").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Paciente").assertIsSelected()
        rule.onNodeWithText("Médico").assertIsNotSelected()
        assertEquals(LoginRole.PACIENTE, vm.uiState.value.selectedRole)
    }

    @Test
    fun `role options and links meet the 48dp touch target`() {
        mostrarLogin()
        // The buttons' visible box can be 40dp while Material extends the touch target to 48dp, so the
        // touch bounds (not the drawn bounds) are what must reach 48dp.
        val minimoPx = with(rule.density) { 48.dp.toPx() }
        for (texto in listOf("Paciente", "Médico", "Esqueci minha senha", "Criar conta")) {
            val toque = rule.onNodeWithText(texto).fetchSemanticsNode().touchBoundsInRoot
            assertTrue("$texto touch target is ${toque.height}px, expected >= $minimoPx", toque.height >= minimoPx - 1f)
        }
    }

    @Test
    fun `elements follow the prototype order from top to bottom`() {
        mostrarLogin()
        val titulo = rule.onAllNodesWithText("Entrar")[0].getUnclippedBoundsInRoot()
        val logo = rule.onNodeWithTag(TAG_LOGO_LOGIN).getUnclippedBoundsInRoot()
        val abas = rule.onNodeWithText("Paciente").getUnclippedBoundsInRoot()
        val email = campoEmail().getUnclippedBoundsInRoot()
        val senha = campoSenha().getUnclippedBoundsInRoot()
        val entrar = botaoEntrar().getUnclippedBoundsInRoot()
        val esqueci = rule.onNodeWithText("Esqueci minha senha").getUnclippedBoundsInRoot()
        val criar = rule.onNodeWithText("Criar conta").getUnclippedBoundsInRoot()
        val ordem = listOf(titulo.bottom, logo.bottom, abas.bottom, email.bottom, senha.bottom, entrar.bottom, esqueci.bottom, criar.bottom)
        assertTrue("expected increasing bottoms, got $ordem", ordem.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `labels sit above their fields`() {
        mostrarLogin()
        val rotuloEmail = rule.onNodeWithText("E-mail").getUnclippedBoundsInRoot()
        val rotuloSenha = rule.onNodeWithText("Senha").getUnclippedBoundsInRoot()
        assertTrue("E-mail label must be above its field", rotuloEmail.bottom <= campoEmail().getUnclippedBoundsInRoot().top)
        assertTrue("Senha label must be above its field", rotuloSenha.bottom <= campoSenha().getUnclippedBoundsInRoot().top)
    }

    @Test
    fun `logo has 88dp, is centered and is invisible to screen readers`() {
        mostrarLogin()
        rule.onNodeWithTag(TAG_LOGO_LOGIN)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(88.dp)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        val logo = rule.onNodeWithTag(TAG_LOGO_LOGIN).getUnclippedBoundsInRoot()
        val raiz = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(abs((logo.left + logo.right).value / 2 - (raiz.left + raiz.right).value / 2) < 1f)
    }

    @Test
    fun `the role pill and both link rows are centered`() {
        mostrarLogin()
        val raiz = rule.onRoot().getUnclippedBoundsInRoot()
        val centroTela = (raiz.left + raiz.right).value / 2
        val esqueci = rule.onNodeWithText("Esqueci minha senha").getUnclippedBoundsInRoot()
        assertTrue("Esqueci minha senha not centered", abs((esqueci.left + esqueci.right).value / 2 - centroTela) < 4f)
        val paciente = rule.onNodeWithText("Paciente").getUnclippedBoundsInRoot()
        val medico = rule.onNodeWithText("Médico").getUnclippedBoundsInRoot()
        assertTrue("role pill not centered", abs((paciente.left + medico.right).value / 2 - centroTela) < 4f)
        val pergunta = rule.onNodeWithText("Não tem conta?").getUnclippedBoundsInRoot()
        val criar = rule.onNodeWithText("Criar conta").getUnclippedBoundsInRoot()
        assertTrue("Não tem conta? / Criar conta not centered", abs((pergunta.left + criar.right).value / 2 - centroTela) < 4f)
    }

    @Test
    fun `fields show their example text and the password eye still works`() {
        mostrarLogin()
        rule.onNodeWithText("voce@email.com").assertIsDisplayed()
        // The password example is hidden from screen readers (it would be read out as bullets).
        rule.onNodeWithText("••••••••").assertDoesNotExist()
        rule.onNodeWithContentDescription("Mostrar senha").performClick()
        rule.onNodeWithContentDescription("Ocultar senha").assertIsDisplayed()
    }

    @Test
    fun `Entrar stays disabled until the form is valid and Criar conta plus Esqueci senha are there`() {
        val vm = mostrarLogin()
        botaoEntrar().assertIsNotEnabled()
        rule.onNodeWithText("Não tem conta?").assertIsDisplayed()
        rule.onNodeWithText("Criar conta").assertIsDisplayed()
        rule.onNodeWithText("Esqueci minha senha").assertIsDisplayed()

        vm.onEmailChanged("joao@x.com")
        vm.onPasswordChanged("senha123")
        rule.waitForIdle()
        botaoEntrar().assertIsEnabled()
    }

    @Test
    fun `the password reset notice appears in a message box announced to screen readers`() {
        val vm = mostrarLogin()
        vm.showPasswordResetNotice()
        rule.waitForIdle()
        rule.onNodeWithText(MSG_SENHA_REDEFINIDA)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `a failed login shows the error in a message box`() {
        coEvery { auth.signIn(any(), any()) } returns Result.failure(IllegalStateException("Invalid login credentials"))
        val vm = mostrarLogin()
        vm.onEmailChanged("joao@x.com")
        vm.onPasswordChanged("errada1")
        rule.waitForIdle()
        botaoEntrar().performClick()
        rule.waitForIdle()
        val erro = vm.uiState.value.errorMessage
        assertNotNull(erro)
        rule.onNodeWithText(erro!!).assertIsDisplayed().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    @Config(qualifiers = "w320dp-h470dp-xxhdpi")
    fun `on a small screen the form scrolls and every element is reachable`() {
        mostrarLogin()
        rule.onNodeWithText("Criar conta").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Não tem conta?").assertIsDisplayed()
    }
}
