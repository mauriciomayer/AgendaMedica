package com.agendamedica.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Story 8.7: the app text uses the bundled Inter (Regular, Medium, SemiBold, Bold), not the platform sans-serif. */
@RunWith(RobolectricTestRunner::class)
class InterFontTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the family bundles the four weights of the design`() {
        assertNotEquals(FontFamily.SansSerif, InterFontFamily)
        val fontes = (InterFontFamily as FontListFontFamily).fonts
        assertEquals(
            listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold),
            fontes.map { it.weight },
        )
    }

    @Test
    fun `every text style of the theme uses Inter`() {
        val estilos = listOf(
            AgendaMedicaTypography.displayLarge, AgendaMedicaTypography.titleLarge, AgendaMedicaTypography.titleMedium,
            AgendaMedicaTypography.bodyLarge, AgendaMedicaTypography.bodyMedium, AgendaMedicaTypography.labelLarge,
            AgendaMedicaTypography.labelSmall,
        )
        estilos.forEach { assertEquals(InterFontFamily, it.fontFamily) }
    }

    @Test
    fun `the four TrueType files are in the app, distinct and of a sane size`() {
        val pasta = java.io.File("src/main/res/font")
        val arquivos = listOf("inter_regular", "inter_medium", "inter_semibold", "inter_bold").map { java.io.File(pasta, "$it.ttf") }
        arquivos.forEach { assertTrue("${it.name} missing", it.isFile) }
        arquivos.forEach { assertTrue("${it.name} size", it.length() in 20_000..300_000) }
        // TrueType outlines start with the sfnt version 0x00010000.
        arquivos.forEach { assertEquals(listOf(0, 1, 0, 0), it.readBytes().take(4).map { b -> b.toInt() }) }
        assertEquals("each weight is a different file", 4, arquivos.map { it.readBytes().contentHashCode() }.toSet().size)
    }

    @Test
    fun `text with Portuguese accents renders under the theme in every weight`() {
        rule.setContent {
            AgendaMedicaTheme {
                Text("Regular ação é ç ã õ", style = MaterialTheme.typography.bodyLarge)
                Text("Semibold médico às 10:00 · Convênio", style = MaterialTheme.typography.titleMedium)
                Text("Bold Consulta agendada!", style = MaterialTheme.typography.titleLarge)
            }
        }
        rule.onNodeWithText("Regular ação é ç ã õ").assertIsDisplayed()
        rule.onNodeWithText("Semibold médico às 10:00 · Convênio").assertIsDisplayed()
        rule.onNodeWithText("Bold Consulta agendada!").assertIsDisplayed()
    }
}
