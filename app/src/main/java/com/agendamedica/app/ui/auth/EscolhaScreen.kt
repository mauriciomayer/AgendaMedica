package com.agendamedica.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agendamedica.app.ui.components.AccessibleIconButton
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.PrimaryButton
import com.agendamedica.app.ui.theme.AgendaMedicaColors

/**
 * "Criar conta" choice screen — Login -> "Criar conta" -> here. "Sou médico" and
 * "Sou paciente" lead to their respective registration screens.
 */
@Composable
fun EscolhaScreen(
    onBack: () -> Unit,
    onSouMedico: () -> Unit,
    onSouPaciente: () -> Unit,
) {
    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(18.dp)) {
            AccessibleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Como você quer usar o Agenda Médica?",
                style = MaterialTheme.typography.titleLarge,
                color = AgendaMedicaColors.inkPrimary,
            )
            Spacer(Modifier.height(24.dp))

            PrimaryButton(text = "Sou médico", onClick = onSouMedico)
            Spacer(Modifier.height(12.dp))
            OutlineButton(
                text = "Sou paciente",
                onClick = onSouPaciente,
            )
        }
    }
}
