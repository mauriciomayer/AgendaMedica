package com.agendamedica.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.ui.components.CampoLabelInk
import com.agendamedica.app.ui.components.LabeledTextField
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.AgendaMedicaLogo
import com.agendamedica.app.ui.theme.ShapeMd
import com.agendamedica.app.ui.theme.ShapePill

/** Test tag of the decorative logo at the top of the Login screen (Story 6.6). */
internal const val TAG_LOGO_LOGIN = "login_logo"

/**
 * Login — laid out like the design prototype (Story 6.7): white header with the title "Entrar" and
 * a subtitle for the selected role, then the logo, a centered pill role selector, the e-mail and
 * password fields (label above, example inside), message boxes, "Entrar", and the centered links.
 * "Criar conta" leads to Escolha, and "Esqueci minha senha" leads to Recuperar Senha (Story 1.3).
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
        val direcao = LocalLayoutDirection.current
        val ladoInicio = 18.dp + padding.calculateStartPadding(direcao)
        val ladoFim = 18.dp + padding.calculateEndPadding(direcao)
        Column(modifier = Modifier.fillMaxSize()) {
            // The white header extends behind the status bar; only its content is pushed below it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgendaMedicaColors.surfaceCard)
                    .padding(top = padding.calculateTopPadding())
                    .padding(start = ladoInicio, end = ladoFim, top = 18.dp, bottom = 14.dp),
            ) {
                Text(
                    text = "Entrar",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = AgendaMedicaColors.inkPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = if (uiState.selectedRole == LoginRole.PACIENTE) {
                        "Acesse sua conta de paciente"
                    } else {
                        "Acesse sua conta de médico"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = AgendaMedicaColors.inkTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            HorizontalDivider(color = AgendaMedicaColors.borderHairline)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = padding.calculateBottomPadding())
                    .padding(start = ladoInicio, end = ladoFim, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AgendaMedicaLogo(size = 88.dp, modifier = Modifier.testTag(TAG_LOGO_LOGIN))
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SeletorPapel(selected = uiState.selectedRole, onSelect = viewModel::onRoleSelected)
                }

                LabeledTextField(
                    value = uiState.activeFields.email,
                    onValueChange = viewModel::onEmailChanged,
                    label = "E-mail",
                    isEmail = true,
                    labelAbove = true,
                    placeholder = "voce@email.com",
                )
                LabeledTextField(
                    value = uiState.activeFields.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = "Senha",
                    isPassword = true,
                    labelAbove = true,
                    placeholder = "••••••••",
                )

                uiState.infoMessage?.let { message ->
                    CaixaMensagem(
                        text = message,
                        background = AgendaMedicaColors.successBg,
                        ink = AgendaMedicaColors.inkPrimary,
                        fontSize = 13.5.sp,
                    )
                }
                uiState.errorMessage?.let { message ->
                    CaixaMensagem(
                        text = message,
                        background = AgendaMedicaColors.warningBg,
                        ink = AgendaMedicaColors.dangerInk,
                        fontSize = 12.5.sp,
                    )
                }

                PrimaryButton(
                    text = if (uiState.isLoading) "Entrando..." else "Entrar",
                    onClick = viewModel::submit,
                    enabled = uiState.isSubmitEnabled,
                )

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onNavigateToRecuperarSenha) {
                        Text(
                            "Esqueci minha senha",
                            color = AgendaMedicaColors.accentPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Não tem conta?", color = AgendaMedicaColors.inkSecondary, fontSize = 13.sp)
                    TextButton(onClick = onNavigateToEscolha) {
                        Text(
                            "Criar conta",
                            color = AgendaMedicaColors.accentPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** Paciente/Médico as one white pill with the active option filled in `accent-primary` (prototype). */
@Composable
private fun SeletorPapel(selected: LoginRole, onSelect: (LoginRole) -> Unit) {
    Row(
        modifier = Modifier
            .selectableGroup()
            .background(AgendaMedicaColors.surfaceCard, ShapePill)
            .border(1.dp, AgendaMedicaColors.borderHairline, ShapePill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OpcaoPapel("Paciente", selected == LoginRole.PACIENTE) { onSelect(LoginRole.PACIENTE) }
        OpcaoPapel("Médico", selected == LoginRole.MEDICO) { onSelect(LoginRole.MEDICO) }
    }
}

@Composable
private fun OpcaoPapel(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .clip(ShapePill)
            .background(if (active) AgendaMedicaColors.accentPrimary else Color.Transparent)
            .selectable(selected = active, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) Color.White else CampoLabelInk,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CaixaMensagem(text: String, background: Color, ink: Color, fontSize: TextUnit) {
    Text(
        text = text,
        color = ink,
        fontSize = fontSize,
        modifier = Modifier
            .semantics { liveRegion = LiveRegionMode.Polite } // a failed login / reset notice is announced when it appears
            .fillMaxWidth()
            .background(background, ShapeMd)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
