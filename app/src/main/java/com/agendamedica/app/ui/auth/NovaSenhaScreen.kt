package com.agendamedica.app.ui.auth

import androidx.activity.compose.BackHandler
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

/**
 * Nova Senha — opened by the recovery deep link. Saving (or leaving) ends the recovery session. Not in the design
 * prototype; it uses the same screen shell and labelled field as the other access screens (Story 8.1).
 */
@Composable
fun NovaSenhaScreen(
    onGoToLogin: (passwordChanged: Boolean) -> Unit,
    viewModel: NovaSenhaViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToLogin.collect { onGoToLogin(it) }
    }
    BackHandler { viewModel.onBack() }

    TelaPadrao(title = "Nova senha", onBack = viewModel::onBack) {
        Column {
            LabeledTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = "Nova senha",
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
            text = if (uiState.isLoading) "Salvando..." else "Salvar nova senha",
            onClick = viewModel::submit,
            enabled = uiState.isSubmitEnabled,
        )
    }
}
