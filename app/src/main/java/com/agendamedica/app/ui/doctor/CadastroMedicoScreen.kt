package com.agendamedica.app.ui.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.DiaSemana
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.model.Localizacao
import com.agendamedica.app.ui.components.CampoSelecao
import com.agendamedica.app.ui.components.InfoBox
import com.agendamedica.app.ui.components.LabeledTextField
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.components.RotuloCampo
import com.agendamedica.app.ui.components.TelaPadrao
import com.agendamedica.app.ui.components.ToggleChip
import com.agendamedica.app.ui.components.TomInfoBox
import com.agendamedica.app.ui.theme.AgendaMedicaColors

/**
 * Cadastro de Médico — Escolha -> "Sou médico" -> here. Full form: nome/e-mail/senha,
 * Especialidade + Localização (dropdowns), Convênios + Dias de atendimento (multi-select
 * chips), horário início/fim (dropdowns). Prototype layout (Story 8.5).
 */
@Composable
fun CadastroMedicoScreen(
    onBack: () -> Unit,
    onRegistered: () -> Unit,
    viewModel: CadastroMedicoViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToMinhaAgenda.collect { onRegistered() }
    }

    TelaPadrao(
        title = "Cadastro de médico",
        subtitle = "Autocadastro, sem validação de CRM",
        onBack = onBack,
        espacamento = 16.dp,
    ) {
        InfoBox(
            text = "Sem validação de CRM — seu perfil fica visível na busca assim que você concluir o cadastro.",
            tom = TomInfoBox.INFO,
            fontSize = 12.5.sp,
        )

        LabeledTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChanged,
            label = "Nome completo",
            labelAbove = true,
            placeholder = "Dr(a). Nome Sobrenome",
        )
        LabeledTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = "E-mail",
            isEmail = true,
            labelAbove = true,
            placeholder = "voce@email.com",
        )
        Column {
            LabeledTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = "Senha",
                isPassword = true,
                labelAbove = true,
                placeholder = "••••••••",
            )
            Text(
                text = "Mínimo de 6 caracteres.",
                style = MaterialTheme.typography.bodySmall,
                color = AgendaMedicaColors.inkSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        CampoSelecao(
            label = "Especialidade",
            selecionado = uiState.especialidade,
            opcoes = Especialidade.entries,
            rotulo = { it.label },
            onSelecionado = viewModel::onEspecialidadeSelected,
        )
        CampoSelecao(
            label = "Localização",
            selecionado = uiState.localizacao,
            opcoes = Localizacao.entries,
            rotulo = { it.label },
            onSelecionado = viewModel::onLocalizacaoSelected,
        )

        Column {
            RotuloCampo("Convênios atendidos")
            ChipFlow {
                Convenio.entries.forEach { convenio ->
                    ToggleChip(
                        text = convenio.label,
                        selected = uiState.conveniosSelecionados.contains(convenio),
                        onClick = { viewModel.onConvenioToggled(convenio) },
                    )
                }
            }
        }
        Column {
            RotuloCampo("Dias de atendimento")
            ChipFlow {
                DiaSemana.ordered.forEach { dia ->
                    ToggleChip(
                        text = dia.label,
                        selected = uiState.diasSelecionados.contains(dia),
                        onClick = { viewModel.onDiaToggled(dia) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoSelecao(
                label = "Início",
                selecionado = uiState.startTime,
                opcoes = HORARIOS_DISPONIVEIS,
                rotulo = { it },
                onSelecionado = viewModel::onStartTimeSelected,
                modifier = Modifier.weight(1f),
            )
            CampoSelecao(
                label = "Fim",
                selecionado = uiState.endTime,
                opcoes = HORARIOS_DISPONIVEIS,
                rotulo = { it },
                onSelecionado = viewModel::onEndTimeSelected,
                modifier = Modifier.weight(1f),
            )
        }

        uiState.errorMessage?.let { message -> InfoBox(text = message, tom = TomInfoBox.AVISO) }

        PrimaryButton(
            text = if (uiState.isLoading) "Criando perfil..." else "Criar perfil e começar a atender",
            onClick = viewModel::submit,
            enabled = uiState.isSubmitEnabled,
        )
    }
}

/** Wrapping row of chips — this story's lists are short (4, 6, 7 items) but wrap on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}
