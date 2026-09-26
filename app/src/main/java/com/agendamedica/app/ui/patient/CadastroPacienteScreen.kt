package com.agendamedica.app.ui.patient

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.ui.components.InfoBox
import com.agendamedica.app.ui.components.LabeledTextField
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.components.TelaPadrao
import com.agendamedica.app.ui.components.TomInfoBox
import com.agendamedica.app.ui.theme.AgendaMedicaColors

/** Cadastro de Paciente — Escolha -> "Sou paciente" -> here. Nome/e-mail/senha only (FR3). Prototype layout (Story 8.1). */
@Composable
fun CadastroPacienteScreen(
    onBack: () -> Unit,
    onRegistered: () -> Unit,
    viewModel: CadastroPacienteViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToBusca.collect { onRegistered() }
    }

    TelaPadrao(title = "Cadastro de paciente", onBack = onBack, espacamento = 16.dp) {
        LabeledTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChanged,
            label = "Nome completo",
            labelAbove = true,
            placeholder = "Seu nome",
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

        uiState.errorMessage?.let { message ->
            InfoBox(text = message, tom = TomInfoBox.AVISO)
        }

        PrimaryButton(
            text = if (uiState.isLoading) "Criando conta..." else "Criar conta",
            onClick = viewModel::submit,
            enabled = uiState.isSubmitEnabled,
        )
    }
}
