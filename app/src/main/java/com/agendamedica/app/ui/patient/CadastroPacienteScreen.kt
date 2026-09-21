package com.agendamedica.app.ui.patient

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.ui.components.AccessibleIconButton
import com.agendamedica.app.ui.components.LabeledTextField
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.theme.AgendaMedicaColors

/** Cadastro de Paciente — Escolha -> "Sou paciente" -> here. Nome/e-mail/senha only (FR3). */
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
                text = "Cadastro de Paciente",
                style = MaterialTheme.typography.titleLarge,
                color = AgendaMedicaColors.inkPrimary,
                modifier = Modifier.padding(bottom = 16.dp),
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

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = AgendaMedicaColors.dangerInk,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            PrimaryButton(
                text = if (uiState.isLoading) "Criando conta..." else "Criar conta",
                onClick = viewModel::submit,
                enabled = uiState.isSubmitEnabled,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
