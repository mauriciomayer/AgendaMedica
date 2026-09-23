package com.agendamedica.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The e-mail typing mask (Story 6.5), driven through the real `LabeledTextField` (spec-7-3 spike). */
@RunWith(RobolectricTestRunner::class)
class LabeledTextFieldEmailTest {

    @get:Rule
    val rule = createComposeRule()

    private var texto by mutableStateOf("")

    private fun show(isEmail: Boolean, inicial: String = "") {
        texto = inicial
        rule.setContent {
            LabeledTextField(value = texto, onValueChange = { texto = it }, label = "E-mail", isEmail = isEmail)
        }
    }

    private fun campo() = rule.onNode(hasSetTextAction())

    private fun selecao(): TextRange =
        campo().fetchSemanticsNode().config.getOrNull(SemanticsProperties.TextSelectionRange)!!

    @Test
    fun `space and accents typed into an e-mail field do not enter`() {
        show(isEmail = true)
        campo().performTextInput("joao silva")
        rule.waitForIdle()
        assertEquals("joaosilva", texto)
        campo().performTextInput("ç@x.com")
        rule.waitForIdle()
        assertEquals("joaosilva@x.com", texto)
    }

    @Test
    fun `a second at sign is not accepted`() {
        show(isEmail = true, inicial = "a@b")
        campo().performTextInput("@")
        rule.waitForIdle()
        assertEquals("a@b", texto)
    }

    @Test
    fun `a field that is not an e-mail field leaves the text alone`() {
        show(isEmail = false)
        campo().performTextInput("joao silva ç")
        rule.waitForIdle()
        assertEquals("joao silva ç", texto)
    }

    @Test
    fun `rejecting a key in the middle of the text keeps the cursor where it was`() {
        show(isEmail = true, inicial = "joao@x.com")
        campo().performTextInputSelection(TextRange(2))
        rule.waitForIdle()
        campo().performTextInput(" ")
        rule.waitForIdle()
        assertEquals("joao@x.com", texto)
        assertEquals(TextRange(2), selecao())
    }
}
