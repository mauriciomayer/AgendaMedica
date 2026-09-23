package com.agendamedica.app.ui.auth

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

/** Story 6.6: the Login screen shows the app logo (88dp, centered, decorative, above the form), as in the design prototype. */
@RunWith(RobolectricTestRunner::class)
class LoginScreenLogoTest {

    @get:Rule
    val rule = createComposeRule()

    private fun mostrarLogin() = rule.setContent {
        LoginScreen({}, {}, {}, {}, viewModel = LoginViewModel(mockk(relaxed = true), mockk(relaxed = true)))
    }

    @Test
    fun `login shows the logo with 88dp and no screen-reader description`() {
        mostrarLogin()
        rule.onNodeWithTag(TAG_LOGO_LOGIN)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(88.dp)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun `logo is above the title and above the role tabs`() {
        mostrarLogin()
        val logo = rule.onNodeWithTag(TAG_LOGO_LOGIN).getUnclippedBoundsInRoot()
        val titulo = rule.onNodeWithText("Agenda Médica").getUnclippedBoundsInRoot()
        val abas = rule.onNodeWithText("Paciente").getUnclippedBoundsInRoot()
        assertTrue("logo bottom=${logo.bottom} must be above the title top=${titulo.top}", logo.bottom <= titulo.top)
        assertTrue("logo bottom=${logo.bottom} must be above the tabs top=${abas.top}", logo.bottom <= abas.top)
    }

    @Test
    fun `logo is horizontally centered`() {
        mostrarLogin()
        val logo = rule.onNodeWithTag(TAG_LOGO_LOGIN).getUnclippedBoundsInRoot()
        val raiz = rule.onRoot().getUnclippedBoundsInRoot()
        val centroLogo = (logo.left + logo.right).value / 2
        val centroTela = (raiz.left + raiz.right).value / 2
        assertTrue("logo center=$centroLogo, screen center=$centroTela", abs(centroLogo - centroTela) < 1f)
    }

    @Test
    fun `the rest of the login form is still there`() {
        mostrarLogin()
        // assertExists, not assertIsDisplayed: the Robolectric default screen is small (320x470dp) and the form scrolls.
        rule.onNodeWithText("Paciente").assertExists()
        rule.onNodeWithText("Médico").assertExists()
        rule.onNodeWithText("Esqueci minha senha").assertExists()
        rule.onNodeWithText("Entrar").assertExists()
        rule.onNodeWithText("Criar conta").assertExists()
    }
}
