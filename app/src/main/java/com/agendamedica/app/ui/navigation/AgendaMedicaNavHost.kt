package com.agendamedica.app.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.agendamedica.app.data.repository.AuthRepository
import com.agendamedica.app.data.repository.RecoveryLinkKind
import com.agendamedica.app.ui.auth.NovaSenhaScreen
import com.agendamedica.app.ui.auth.RecuperarSenhaScreen
import kotlinx.coroutines.flow.Flow
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
import com.agendamedica.app.ui.patient.DetalheMedicoScreen

/** Route names for this story's screens (Information Architecture, EXPERIENCE.md). */
private object Routes {
    const val LOGIN = "login"
    const val LOGIN_PATTERN = "login?senhaRedefinida={senhaRedefinida}"
    const val RECUPERAR_SENHA = "recuperar_senha"
    const val RECUPERAR_SENHA_PATTERN = "recuperar_senha?expirado={expirado}"
    const val NOVA_SENHA = "nova_senha"
    const val ESCOLHA = "escolha"
    const val CADASTRO_MEDICO = "cadastro_medico"
    const val MINHA_AGENDA = "minha_agenda"
    const val CADASTRO_PACIENTE = "cadastro_paciente"
    const val BUSCA = "busca"
    const val DETALHE_MEDICO = "medico"
    const val DETALHE_MEDICO_PATTERN = "medico/{doctorId}"
}

/**
 * Linear navigation for this story: Login <-> Escolha -> Cadastro de Médico -> Minha Agenda.
 * No side menu, no persistent tab bar (EXPERIENCE.md, Information Architecture) — navigation
 * is purely action-driven. Reaching Minha Agenda (from either login or a fresh registration)
 * clears the back stack up to Login, since there is no "back" affordance from Minha Agenda in
 * this story (only future stories add a way out of it, e.g. logout).
 */
@Composable
fun AgendaMedicaNavHost(
    navController: NavHostController = rememberNavController(),
    deepLinks: Flow<Intent>? = null,
) {
    // Password-recovery deep link: classified before any session import (see AuthRepository).
    LaunchedEffect(deepLinks) {
        val authRepository = AuthRepository()
        deepLinks?.collect { intent ->
            val destination = when (authRepository.handleRecoveryLink(intent)) {
                RecoveryLinkKind.READY -> Routes.NOVA_SENHA
                RecoveryLinkKind.EXPIRED -> "${Routes.RECUPERAR_SENHA}?expirado=true"
                RecoveryLinkKind.IGNORE -> null
            }
            if (destination != null) {
                navController.navigate(destination) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.LOGIN_PATTERN) {
        composable(
            Routes.LOGIN_PATTERN,
            arguments = listOf(navArgument("senhaRedefinida") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            LoginScreen(
                passwordResetNotice = entry.arguments?.getBoolean("senhaRedefinida") ?: false,
                onNavigateToRecuperarSenha = { navController.navigate(Routes.RECUPERAR_SENHA) },
                onNavigateToMinhaAgenda = {
                    navController.navigate(Routes.MINHA_AGENDA) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onNavigateToBusca = {
                    navController.navigate(Routes.BUSCA) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onNavigateToEscolha = { navController.navigate(Routes.ESCOLHA) },
            )
        }
        composable(
            Routes.RECUPERAR_SENHA_PATTERN,
            arguments = listOf(navArgument("expirado") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            RecuperarSenhaScreen(
                linkExpired = entry.arguments?.getBoolean("expirado") ?: false,
                onBackToLogin = { navController.goToLogin(passwordChanged = false) },
            )
        }
        composable(Routes.NOVA_SENHA) {
            NovaSenhaScreen(onGoToLogin = { changed -> navController.goToLogin(passwordChanged = changed) })
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
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.BUSCA) {
            BuscaScreen(onDoctorClick = { id -> navController.navigate("${Routes.DETALHE_MEDICO}/$id") })
        }
        composable(
            Routes.DETALHE_MEDICO_PATTERN,
            arguments = listOf(navArgument("doctorId") { type = NavType.StringType }),
        ) { entry ->
            DetalheMedicoScreen(
                doctorId = entry.arguments?.getString("doctorId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CADASTRO_MEDICO) {
            CadastroMedicoScreen(
                onBack = { navController.popBackStack() },
                onRegistered = {
                    navController.navigate(Routes.MINHA_AGENDA) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MINHA_AGENDA) {
            MinhaAgendaScreen()
        }
    }
}

/** Login as the only entry in the back stack, optionally with the "senha redefinida" notice. */
private fun NavHostController.goToLogin(passwordChanged: Boolean) {
    navigate("${Routes.LOGIN}?senhaRedefinida=$passwordChanged") {
        popUpTo(graph.id) { inclusive = true }
    }
}
