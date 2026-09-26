package com.agendamedica.app.ui.auth

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agendamedica.app.ui.components.CartaoEscolha
import com.agendamedica.app.ui.components.TelaPadrao
import com.agendamedica.app.ui.theme.AgendaMedicaColors

/**
 * "Criar conta" choice screen — Login -> "Criar conta" -> here. "Sou paciente" and "Sou médico" lead to their
 * respective registration screens (two tappable cards, as in the design prototype).
 */
@Composable
fun EscolhaScreen(
    onBack: () -> Unit,
    onSouMedico: () -> Unit,
    onSouPaciente: () -> Unit,
) {
    TelaPadrao(title = "Criar conta", onBack = onBack, espacamento = 12.dp) {
        Text(
            text = "Como você quer usar o app?",
            color = AgendaMedicaColors.inkSecondary,
            fontSize = 13.sp,
        )
        CartaoEscolha(
            titulo = "Sou paciente",
            descricao = "Buscar médicos e agendar consultas",
            onClick = onSouPaciente,
        )
        CartaoEscolha(
            titulo = "Sou médico",
            descricao = "Cadastrar meu perfil e atender pacientes",
            onClick = onSouMedico,
        )
    }
}
