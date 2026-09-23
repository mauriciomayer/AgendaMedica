package com.agendamedica.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.ui.components.LabeledTextField
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.components.ToggleChip
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.AgendaMedicaLogo

/** Test tag of the decorative logo at the top of the Login screen (Story 6.6). */
internal const val TAG_LOGO_LOGIN = "login_logo"

/**
 * Login — the base screen reused by every future story (Code Map). This story only wires
 * up a real submit for the "Médico" tab's happy/unhappy paths; "Criar conta" leads to
 * Escolha, and "Esqueci minha senha" leads to Recuperar Senha (Story 1.3).
 */
@Composable
fun LoginScreen(
    onNavigateToMinhaAgenda: () -> Unit,
    onNavigateToBusca: () -> Unit,
    onNavigateToEscolha: () -> Unit,
    onNavigateToRecuperarSenha: () -> Unit,
    passwordResetNotice: Boolean = false,
    viewModel: LoginViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(passwordResetNotice) {
        if (passwordResetNotice) viewModel.showPasswordResetNotice()
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToMinhaAgenda.collect { onNavigateToMinhaAgenda() }
    }
    LaunchedEffect(Unit) {
        viewModel.navigateToBusca.collect { onNavigateToBusca() }
    }

    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                AgendaMedicaLogo(size = 88.dp, modifier = Modifier.testTag(TAG_LOGO_LOGIN))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Agenda Médica",
                style = MaterialTheme.typography.titleLarge,
                color = AgendaMedicaColors.inkPrimary,
            )
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(
                    text = "Paciente",
                    selected = uiState.selectedRole == LoginRole.PACIENTE,
                    onClick = { viewModel.onRoleSelected(LoginRole.PACIENTE) },
                )
                ToggleChip(
                    text = "Médico",
                    selected = uiState.selectedRole == LoginRole.MEDICO,
                    onClick = { viewModel.onRoleSelected(LoginRole.MEDICO) },
                )
            }
            Spacer(Modifier.height(20.dp))

            LabeledTextField(
                value = uiState.activeFields.email,
                onValueChange = viewModel::onEmailChanged,
                label = "E-mail",
                isEmail = true,
            )
            Spacer(Modifier.height(12.dp))
            LabeledTextField(
                value = uiState.activeFields.password,
                onValueChange = viewModel::onPasswordChanged,
                label = "Senha",
                isPassword = true,
            )
            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onNavigateToRecuperarSenha) {
                Text("Esqueci minha senha", color = AgendaMedicaColors.accentPrimary)
            }

            uiState.infoMessage?.let { message ->
                Text(
                    text = message,
                    color = AgendaMedicaColors.inkPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = AgendaMedicaColors.dangerInk,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = if (uiState.isLoading) "Entrando..." else "Entrar",
                onClick = viewModel::submit,
                enabled = uiState.isSubmitEnabled,
            )

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onNavigateToEscolha) {
                Text("Criar conta", color = AgendaMedicaColors.accentPrimary)
            }
        }
    }
}
