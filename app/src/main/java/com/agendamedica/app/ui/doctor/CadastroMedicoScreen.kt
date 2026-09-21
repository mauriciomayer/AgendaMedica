package com.agendamedica.app.ui.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.domain.model.Convenio
import com.agendamedica.app.domain.model.DiaSemana
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.model.Localizacao
import com.agendamedica.app.ui.components.AccessibleIconButton
import com.agendamedica.app.ui.components.LabeledTextField
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.components.ToggleChip
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.ShapeMd
import androidx.compose.runtime.LaunchedEffect

/**
 * Cadastro de Médico — Escolha -> "Sou médico" -> here. Full form: nome/e-mail/senha,
 * Especialidade (single-select dropdown), Convênios + Dias de atendimento (multi-select
 * chips), horário início/fim (dropdowns) — DESIGN.md Components, EXPERIENCE.md Flow 2.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            AccessibleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Cadastro de Médico",
                style = MaterialTheme.typography.titleLarge,
                color = AgendaMedicaColors.inkPrimary,
            )
            Text(
                text = "Sem validação de CRM — seu perfil fica visível na busca assim que você concluir o cadastro.",
                style = MaterialTheme.typography.bodyMedium,
                color = AgendaMedicaColors.inkSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            LabeledTextField(value = uiState.name, onValueChange = viewModel::onNameChanged, label = "Nome")
            Spacer(Modifier.height(12.dp))
            LabeledTextField(value = uiState.email, onValueChange = viewModel::onEmailChanged, label = "E-mail")
            Spacer(Modifier.height(12.dp))
            LabeledTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = "Senha",
                isPassword = true,
            )
            Text(
                text = "Mínimo de 6 caracteres.",
                style = MaterialTheme.typography.bodySmall,
                color = AgendaMedicaColors.inkSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(20.dp))

            SectionLabel("Especialidade")
            EspecialidadeDropdown(
                selected = uiState.especialidade,
                onSelected = viewModel::onEspecialidadeSelected,
            )
            Spacer(Modifier.height(20.dp))

            SectionLabel("Localização")
            LocalizacaoDropdown(
                selected = uiState.localizacao,
                onSelected = viewModel::onLocalizacaoSelected,
            )
            Spacer(Modifier.height(20.dp))

            SectionLabel("Convênios atendidos")
            ChipFlow {
                Convenio.entries.forEach { convenio ->
                    ToggleChip(
                        text = convenio.label,
                        selected = uiState.conveniosSelecionados.contains(convenio),
                        onClick = { viewModel.onConvenioToggled(convenio) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel("Dias de atendimento")
            ChipFlow {
                DiaSemana.ordered.forEach { dia ->
                    ToggleChip(
                        text = dia.label,
                        selected = uiState.diasSelecionados.contains(dia),
                        onClick = { viewModel.onDiaToggled(dia) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel("Horário de atendimento")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeDropdown(
                    modifier = Modifier.weight(1f),
                    label = "Início",
                    selected = uiState.startTime,
                    onSelected = viewModel::onStartTimeSelected,
                )
                TimeDropdown(
                    modifier = Modifier.weight(1f),
                    label = "Fim",
                    selected = uiState.endTime,
                    onSelected = viewModel::onEndTimeSelected,
                )
            }

            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    color = AgendaMedicaColors.dangerInk,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                )
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = if (uiState.isLoading) "Criando perfil..." else "Criar perfil e começar a atender",
                onClick = viewModel::submit,
                enabled = uiState.isSubmitEnabled,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = AgendaMedicaColors.inkSecondary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EspecialidadeDropdown(
    selected: Especialidade?,
    onSelected: (Especialidade) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Selecione") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = ShapeMd,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Especialidade.entries.forEach { especialidade ->
                DropdownMenuItem(
                    text = { Text(especialidade.label) },
                    onClick = {
                        onSelected(especialidade)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalizacaoDropdown(
    selected: Localizacao?,
    onSelected: (Localizacao) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Selecione") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = ShapeMd,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Localizacao.entries.forEach { localizacao ->
                DropdownMenuItem(
                    text = { Text(localizacao.label) },
                    onClick = {
                        onSelected(localizacao)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDropdown(
    label: String,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = ShapeMd,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HORARIOS_DISPONIVEIS.forEach { horario ->
                DropdownMenuItem(
                    text = { Text(horario) },
                    onClick = {
                        onSelected(horario)
                        expanded = false
                    },
                )
            }
        }
    }
}
