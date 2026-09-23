package com.agendamedica.app.ui.components

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.SemanticsNodeInteraction
import com.agendamedica.app.ui.auth.LoginRole
import com.agendamedica.app.ui.auth.LoginScreen
import com.agendamedica.app.ui.auth.LoginViewModel
import com.agendamedica.app.ui.auth.RecuperarSenhaScreen
import com.agendamedica.app.ui.auth.RecuperarSenhaViewModel
import com.agendamedica.app.ui.doctor.CadastroMedicoScreen
import com.agendamedica.app.ui.doctor.CadastroMedicoViewModel
import com.agendamedica.app.ui.patient.CadastroPacienteScreen
import com.agendamedica.app.ui.patient.CadastroPacienteViewModel
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Story 6.5 requires the e-mail mask on every e-mail field of the app. Each screen is rendered with
 * its real ViewModel (mocked repositories); typing "a b" must reach the ViewModel as "ab" (spec-7-3).
 */
@RunWith(RobolectricTestRunner::class)
class EmailFieldWiringTest {

    @get:Rule
    val rule = createComposeRule()

    // Cadastro/Recuperar keep the floating label inside the field node; the Login form has a separate label
    // above the field, where the e-mail input is the first text field on the screen.
    private fun emailField(): SemanticsNodeInteraction {
        val comRotulo = rule.onAllNodes(hasSetTextAction() and hasText("E-mail"))
        return if (comRotulo.fetchSemanticsNodes().isNotEmpty()) comRotulo[0] else rule.onAllNodes(hasSetTextAction())[0]
    }

    private fun digitar() {
        emailField().performTextInput("a b")
        rule.waitForIdle()
    }

    @Test
    fun `Login e-mail field applies the mask`() {
        val vm = LoginViewModel(mockk(relaxed = true), mockk(relaxed = true))
        rule.setContent {
            LoginScreen({}, {}, {}, {}, viewModel = vm)
        }
        digitar()
        assertEquals("ab", vm.uiState.value.activeFields.email)
    }

    @Test
    fun `Recuperar Senha e-mail field applies the mask`() {
        val vm = RecuperarSenhaViewModel(mockk(relaxed = true))
        rule.setContent { RecuperarSenhaScreen(linkExpired = false, onBackToLogin = {}, viewModel = vm) }
        digitar()
        assertEquals("ab", vm.uiState.value.email)
    }

    @Test
    fun `Cadastro de Medico e-mail field applies the mask`() {
        val vm = CadastroMedicoViewModel(mockk(relaxed = true), mockk(relaxed = true))
        rule.setContent { CadastroMedicoScreen(onBack = {}, onRegistered = {}, viewModel = vm) }
        digitar()
        assertEquals("ab", vm.uiState.value.email)
    }

    @Test
    fun `Cadastro de Paciente e-mail field applies the mask`() {
        val vm = CadastroPacienteViewModel(mockk(relaxed = true), mockk(relaxed = true))
        rule.setContent { CadastroPacienteScreen(onBack = {}, onRegistered = {}, viewModel = vm) }
        digitar()
        assertEquals("ab", vm.uiState.value.email)
    }

    private fun selecao(): TextRange =
        emailField().fetchSemanticsNode().config.getOrNull(SemanticsProperties.TextSelectionRange)!!

    @Test
    fun `typing a valid key in the middle through a real ViewModel keeps the cursor after it`() {
        val vm = LoginViewModel(mockk(relaxed = true), mockk(relaxed = true))
        vm.onEmailChanged("joao@x.com")
        rule.setContent { LoginScreen({}, {}, {}, {}, viewModel = vm) }
        rule.waitForIdle()
        emailField().performTextInputSelection(TextRange(2))
        rule.waitForIdle()
        emailField().performTextInput("z")
        rule.waitForIdle()
        assertEquals("jozao@x.com", vm.uiState.value.activeFields.email)
        assertEquals(TextRange(3), selecao())
    }

    @Test
    fun `rejecting a key in the middle through a real ViewModel keeps the cursor where it was`() {
        val vm = LoginViewModel(mockk(relaxed = true), mockk(relaxed = true))
        vm.onEmailChanged("joao@x.com")
        rule.setContent { LoginScreen({}, {}, {}, {}, viewModel = vm) }
        rule.waitForIdle()
        emailField().performTextInputSelection(TextRange(2))
        rule.waitForIdle()
        emailField().performTextInput(" ")
        rule.waitForIdle()
        assertEquals("joao@x.com", vm.uiState.value.activeFields.email)
        assertEquals(TextRange(2), selecao())
    }

    @Test
    fun `switching the Login role tab replaces the e-mail shown and typing continues from the new text`() {
        val vm = LoginViewModel(mockk(relaxed = true), mockk(relaxed = true))
        vm.onRoleSelected(LoginRole.MEDICO)
        vm.onEmailChanged("medico@x.com")
        vm.onRoleSelected(LoginRole.PACIENTE)
        vm.onEmailChanged("paciente@x.com")
        vm.onRoleSelected(LoginRole.MEDICO)
        rule.setContent { LoginScreen({}, {}, {}, {}, viewModel = vm) }
        rule.waitForIdle()
        emailField().assertTextEquals("medico@x.com")

        vm.onRoleSelected(LoginRole.PACIENTE)
        rule.waitForIdle()
        emailField().assertTextEquals("paciente@x.com")

        emailField().performTextInput("!")
        rule.waitForIdle()
        assertEquals("paciente@x.com", vm.uiState.value.activeFields.email)
    }
}
