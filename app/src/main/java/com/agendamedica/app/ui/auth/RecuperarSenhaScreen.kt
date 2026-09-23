package com.agendamedica.app.ui.auth

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
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.theme.AgendaMedicaColors

/** Recuperar Senha — Login -> "Esqueci minha senha" -> here (FR11). */
@Composable
fun RecuperarSenhaScreen(
    linkExpired: Boolean,
    onBackToLogin: () -> Unit,
    viewModel: RecuperarSenhaViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(linkExpired) {
        if (linkExpired) viewModel.showExpiredLinkNotice()
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
                onClick = onBackToLogin,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Recuperar senha",
                style = MaterialTheme.typography.titleLarge,
                color = AgendaMedicaColors.inkPrimary,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (uiState.expiredLinkBanner) {
                Text(
                    text = MSG_LINK_EXPIRADO,
                    color = AgendaMedicaColors.dangerInk,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            LabeledTextField(value = uiState.email, onValueChange = viewModel::onEmailChanged, label = "E-mail", isEmail = true)
            Spacer(Modifier.height(16.dp))

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = AgendaMedicaColors.dangerInk,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (uiState.sent) {
                Text(
                    text = MSG_RESET_NEUTRA,
                    color = AgendaMedicaColors.inkPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlineButton(text = "Voltar ao login", onClick = onBackToLogin)
                Spacer(Modifier.height(12.dp))
            }

            PrimaryButton(
                text = if (uiState.isLoading) "Enviando..." else "Enviar link de recuperação",
                onClick = viewModel::submit,
                enabled = uiState.isSubmitEnabled,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
