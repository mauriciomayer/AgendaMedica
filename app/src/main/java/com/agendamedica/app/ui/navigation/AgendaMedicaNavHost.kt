package com.agendamedica.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agendamedica.app.ui.auth.EscolhaScreen
import com.agendamedica.app.ui.auth.LoginScreen
import com.agendamedica.app.ui.doctor.CadastroMedicoScreen
import com.agendamedica.app.ui.doctor.MinhaAgendaScreen
import com.agendamedica.app.ui.patient.BuscaScreen
import com.agendamedica.app.ui.patient.CadastroPacienteScreen

/** Route names for this story's screens (Information Architecture, EXPERIENCE.md). */
private object Routes {
    const val LOGIN = "login"
    const val ESCOLHA = "escolha"
    const val CADASTRO_MEDICO = "cadastro_medico"
    const val MINHA_AGENDA = "minha_agenda"
    const val CADASTRO_PACIENTE = "cadastro_paciente"
    const val BUSCA = "busca"
}

/**
 * Linear navigation for this story: Login <-> Escolha -> Cadastro de Médico -> Minha Agenda.
 * No side menu, no persistent tab bar (EXPERIENCE.md, Information Architecture) — navigation
 * is purely action-driven. Reaching Minha Agenda (from either login or a fresh registration)
 * clears the back stack up to Login, since there is no "back" affordance from Minha Agenda in
 * this story (only future stories add a way out of it, e.g. logout).
 */
@Composable
fun AgendaMedicaNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToMinhaAgenda = {
                    navController.navigate(Routes.MINHA_AGENDA) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToBusca = {
                    navController.navigate(Routes.BUSCA) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToEscolha = { navController.navigate(Routes.ESCOLHA) },
            )
        }
        composable(Routes.ESCOLHA) {
            EscolhaScreen(
                onBack = { navController.popBackStack() },
                onSouMedico = { navController.navigate(Routes.CADASTRO_MEDICO) },
                onSouPaciente = { navController.navigate(Routes.CADASTRO_PACIENTE) },
            )
        }
        composable(Routes.CADASTRO_PACIENTE) {
            CadastroPacienteScreen(
                onBack = { navController.popBackStack() },
                onRegistered = {
                    navController.navigate(Routes.BUSCA) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.BUSCA) {
            BuscaScreen()
        }
        composable(Routes.CADASTRO_MEDICO) {
            CadastroMedicoScreen(
                onBack = { navController.popBackStack() },
                onRegistered = {
                    navController.navigate(Routes.MINHA_AGENDA) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MINHA_AGENDA) {
            MinhaAgendaScreen()
        }
    }
}
