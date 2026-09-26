package com.agendamedica.app.ui.auth

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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.ui.components.InfoBox
import com.agendamedica.app.ui.components.LabeledTextField
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.components.TelaPadrao
import com.agendamedica.app.ui.components.TomInfoBox
import com.agendamedica.app.ui.theme.AgendaMedicaColors

/**
 * Recuperar Senha — Login -> "Esqueci minha senha" -> here (FR11). Design prototype layout (Story 8.1): once the
 * link is sent, the form gives way to a green box and "Voltar ao login".
 */
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

    TelaPadrao(title = "Recuperar senha", onBack = onBackToLogin) {
        if (uiState.expiredLinkBanner) {
            InfoBox(text = MSG_LINK_EXPIRADO, tom = TomInfoBox.AVISO)
        }

        if (uiState.sent) {
            InfoBox(text = MSG_RESET_NEUTRA, tom = TomInfoBox.SUCESSO, fontSize = 13.5.sp)
            PrimaryButton(text = "Voltar ao login", onClick = onBackToLogin)
        } else {
            Column {
                Text(
                    text = "Informe seu e-mail para receber um link de redefinição de senha.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = AgendaMedicaColors.inkSecondary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LabeledTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChanged,
                    label = "E-mail",
                    isEmail = true,
                    labelAbove = true,
                    placeholder = "voce@email.com",
                )
            }

            uiState.errorMessage?.let { message ->
                InfoBox(text = message, tom = TomInfoBox.AVISO)
            }

            PrimaryButton(
                text = if (uiState.isLoading) "Enviando..." else "Enviar link de recuperação",
                onClick = viewModel::submit,
                enabled = uiState.isSubmitEnabled,
            )
        }
    }
}
