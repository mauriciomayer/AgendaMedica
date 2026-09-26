package com.agendamedica.app.ui.patient

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.search.DoctorSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Story 8.2: Busca follows the design prototype (bar "Agende", filters in one card, count line, doctor cards with avatar). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h892dp-xxhdpi")
class BuscaLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private fun medico(id: String, nome: String, esp: Especialidade = Especialidade.CARDIOLOGIA, cidade: String = "São Paulo") =
        DoctorSummary(id, nome, esp, cidade, "Centro", 0.0, 0.0, listOf(Convenio.UNIMED, Convenio.AMIL))

    private fun mostrar(
        estado: BuscaUiState,
        aoSelecionarEspecialidade: (Especialidade?) -> Unit = {},
        aoIrParaMinhasConsultas: () -> Unit = {},
        aoTocarGps: () -> Unit = {},
        aoTentarNovamente: () -> Unit = {},
        aoTocarMedico: (String) -> Unit = {},
    ) = rule.setContent {
        BuscaConteudo(
            uiState = estado,
            onEspecialidadeSelected = aoSelecionarEspecialidade,
            onRegionChanged = {},
            onGpsClick = aoTocarGps,
            onRetry = aoTentarNovamente,
            onDoctorClick = aoTocarMedico,
            onMinhasConsultas = aoIrParaMinhasConsultas,
        )
    }

    private val doisMedicos = BuscaUiState(
        isLoading = false,
        doctors = listOf(medico("1", "Dr. Ricardo Alves"), medico("2", "Dra. Helena Duarte", Especialidade.DERMATOLOGIA, "Campinas")),
    )

    @Test
    fun `the bar says Agende with its subtitle and the Minhas consultas pill`() {
        var indo = 0
        mostrar(doisMedicos, aoIrParaMinhasConsultas = { indo++ })
        rule.onNode(hasText("Agende") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        rule.onNodeWithText("Encontre um médico e agende").assertIsDisplayed()
        rule.onNodeWithText("Minhas consultas").performClick()
        assertEquals(1, indo)
        val minimoPx = with(rule.density) { 48.dp.toPx() }
        assertTrue(rule.onNodeWithText("Minhas consultas").fetchSemanticsNode().touchBoundsInRoot.height >= minimoPx - 1f)
    }

    @Test
    fun `filters sit together in one card with the example text and the GPS button`() {
        var gps = 0
        mostrar(doisMedicos, aoTocarGps = { gps++ })
        rule.onNodeWithText("Todas as especialidades").assertIsDisplayed()
        rule.onNodeWithText("Cidade (ex.: São Paulo, SP)").assertIsDisplayed()
        rule.onNodeWithContentDescription("Usar minha localização").assertIsDisplayed().performClick()
        assertEquals(1, gps)
        val filtro = rule.onNodeWithText("Todas as especialidades").getUnclippedBoundsInRoot()
        val cidade = rule.onNodeWithText("Cidade (ex.: São Paulo, SP)").getUnclippedBoundsInRoot()
        assertTrue("city field must be below the specialty filter", cidade.top >= filtro.bottom)
    }

    @Test
    fun `choosing a specialty reports it`() {
        val escolhidas = mutableListOf<Especialidade?>()
        mostrar(doisMedicos, aoSelecionarEspecialidade = { escolhidas += it })
        rule.onNodeWithText("Todas as especialidades").performClick()
        rule.onNodeWithText("Cardiologia").performClick()
        assertEquals(listOf<Especialidade?>(Especialidade.CARDIOLOGIA), escolhidas)
    }

    @Test
    fun `Todas as especialidades in the menu clears the filter`() {
        val escolhidas = mutableListOf<Especialidade?>()
        mostrar(doisMedicos.copy(especialidade = Especialidade.CARDIOLOGIA), aoSelecionarEspecialidade = { escolhidas += it })
        rule.onNodeWithText("Cardiologia").assertIsDisplayed() // the field shows the chosen specialty
        rule.onNodeWithText("Cardiologia").performClick() // opens the menu
        rule.onNodeWithText("Todas as especialidades").performClick()
        assertEquals(listOf<Especialidade?>(null), escolhidas)
    }

    @Test
    fun `the count line and the doctor cards show name, specialty and city on one line, and the convenios`() {
        mostrar(doisMedicos)
        rule.onNodeWithText("2 médico(s) encontrado(s)").assertIsDisplayed()
        rule.onNodeWithText("Dr. Ricardo Alves").assertIsDisplayed()
        rule.onNodeWithText("Cardiologia · São Paulo").assertIsDisplayed()
        rule.onNodeWithText("Dermatologia · Campinas").assertIsDisplayed()
        rule.onAllNodesWithText("Unimed").assertCountEquals(2) // one chip per card
        rule.onAllNodesWithText("Amil").assertCountEquals(2)
    }

    @Test
    fun `the whole doctor card is tappable`() {
        val tocados = mutableListOf<String>()
        mostrar(doisMedicos, aoTocarMedico = { tocados += it })
        rule.onNodeWithText("Dr. Ricardo Alves").performClick()
        rule.onNodeWithText("Dermatologia · Campinas").performClick()
        assertEquals(listOf("1", "2"), tocados)
    }

    @Test
    fun `the avatar shows the initials but is hidden from screen readers`() {
        mostrar(doisMedicos)
        rule.onNodeWithText("RA", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("RA").assertDoesNotExist()
    }

    @Test
    fun `no results shows zero in the count and the empty message`() {
        mostrar(BuscaUiState(isLoading = false, doctors = emptyList()))
        rule.onNodeWithText("0 médico(s) encontrado(s)").assertIsDisplayed()
        rule.onNodeWithText(MSG_SEM_RESULTADOS).assertIsDisplayed()
    }

    @Test
    fun `while loading there is no count and no list`() {
        mostrar(BuscaUiState(isLoading = true))
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
        rule.onNodeWithText("0 médico(s) encontrado(s)").assertDoesNotExist()
        rule.onNodeWithText(MSG_SEM_RESULTADOS).assertDoesNotExist()
    }

    @Test
    fun `an error shows its message and Tentar novamente retries`() {
        var tentou = 0
        mostrar(BuscaUiState(isLoading = false, errorMessage = "Algo deu errado."), aoTentarNovamente = { tentou++ })
        rule.onNodeWithText("Algo deu errado.").assertIsDisplayed()
        rule.onNodeWithText("Tentar novamente").performClick()
        assertEquals(1, tentou)
        rule.onNodeWithText("Tentar novamente").assertIsDisplayed()
        rule.onNodeWithText("0 médico(s) encontrado(s)").assertDoesNotExist()
    }

    @Test
    fun `location notices stay inside the filter card`() {
        mostrar(doisMedicos.copy(locationNotice = "Permissão de localização negada.", isLocating = true))
        rule.onNodeWithText("Permissão de localização negada.").assertIsDisplayed()
        rule.onNodeWithText("Obtendo sua localização...").assertIsDisplayed()
        val cidade = rule.onNodeWithText("Cidade (ex.: São Paulo, SP)").getUnclippedBoundsInRoot()
        val aviso = rule.onNodeWithText("Permissão de localização negada.").getUnclippedBoundsInRoot()
        val contagem = rule.onNodeWithText("2 médico(s) encontrado(s)").getUnclippedBoundsInRoot()
        assertTrue(aviso.top >= cidade.bottom && aviso.bottom <= contagem.top)
    }

    @Test
    fun `a long result list scrolls`() {
        val muitos = (1..25).map { medico("id$it", "Dr. Fulano $it") }
        mostrar(BuscaUiState(isLoading = false, doctors = muitos))
        rule.onNodeWithText("Dr. Fulano 1").assertIsDisplayed()
        rule.onNodeWithText("Dr. Fulano 25").assertDoesNotExist()
        rule.onNodeWithText("25 médico(s) encontrado(s)").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Dr. Fulano 25"))
        rule.onNodeWithText("Dr. Fulano 25").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h470dp-xxhdpi")
    fun `on a small screen the filter card scrolls away and the results stay reachable`() {
        mostrar(doisMedicos)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Dra. Helena Duarte"))
        rule.onNodeWithText("Dra. Helena Duarte").assertIsDisplayed()
    }
}
