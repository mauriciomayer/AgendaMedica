package com.agendamedica.app.ui.navigation

import android.content.Intent
import android.net.Uri
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
import com.agendamedica.app.ui.patient.ConfirmacaoScreen
import com.agendamedica.app.ui.patient.DetalheMedicoScreen
import com.agendamedica.app.ui.patient.MinhasConsultasScreen
import com.agendamedica.app.ui.splash.SplashScreen

/** Route names for this story's screens (Information Architecture, EXPERIENCE.md). */
private object Routes {
    const val SPLASH = "splash"
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
    const val DETALHE_MEDICO_PATTERN = "medico/{doctorId}?consultaId={consultaId}"
    const val MINHAS_CONSULTAS = "minhas_consultas"
    const val CONFIRMACAO = "confirmacao"
    const val CONFIRMACAO_PATTERN = "confirmacao?medico={medico}&especialidade={especialidade}&inicio={inicio}&convenio={convenio}"
}

/**
 * Linear navigation for this story: Login <-> Escolha -> Cadastro de Médico -> Minha Agenda.
 * No side menu, no persistent tab bar (EXPERIENCE.md, Information Architecture) — navigation
 * is purely action-driven. Reaching Minha Agenda (from either login or a fresh registration)
 * clears the back stack up to Login, since there is no "back" affordance from Minha Agenda in
 * this story (only future stories add a way out of it, e.g. logout).
 *
 * The graph's actual `startDestination` is the splash screen (spec-4-1-splash-icone.md), which
 * pops itself off the stack as soon as it hands off to Login, so the existing routing above is
 * otherwise unchanged: it's exactly what ran before the splash existed.
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

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onFinished = { navController.goToLogin(passwordChanged = false) })
        }
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
            BuscaScreen(
                onDoctorClick = { id -> navController.navigate("${Routes.DETALHE_MEDICO}/$id") },
                onMinhasConsultas = { navController.navigate(Routes.MINHAS_CONSULTAS) },
            )
        }
        composable(
            Routes.DETALHE_MEDICO_PATTERN,
            arguments = listOf(
                navArgument("doctorId") { type = NavType.StringType },
                navArgument("consultaId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            DetalheMedicoScreen(
                doctorId = entry.arguments?.getString("doctorId").orEmpty(),
                appointmentId = entry.arguments?.getString("consultaId"),
                onRescheduled = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onBooked = { c ->
                    val route = "${Routes.CONFIRMACAO}?medico=${Uri.encode(c.doctorName)}" +
                        "&especialidade=${Uri.encode(c.especialidade)}" +
                        "&inicio=${c.start.toEpochMilli()}&convenio=${Uri.encode(c.convenio)}"
                    // Detalhe leaves the back stack so "voltar" from Confirmação never returns to it.
                    navController.navigate(route) {
                        popUpTo(Routes.DETALHE_MEDICO_PATTERN) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Routes.CONFIRMACAO_PATTERN,
            arguments = listOf(
                navArgument("medico") { type = NavType.StringType; defaultValue = "" },
                navArgument("especialidade") { type = NavType.StringType; defaultValue = "" },
                navArgument("inicio") { type = NavType.LongType; defaultValue = 0L },
                navArgument("convenio") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val args = entry.arguments
            ConfirmacaoScreen(
                doctorName = args?.getString("medico").orEmpty(),
                especialidade = args?.getString("especialidade").orEmpty(),
                startMillis = args?.getLong("inicio") ?: 0L,
                convenio = args?.getString("convenio").orEmpty(),
                onVerConsultas = { navController.navigate(Routes.MINHAS_CONSULTAS) },
                onVoltarBusca = {
                    navController.navigate(Routes.BUSCA) {
                        popUpTo(Routes.BUSCA) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MINHAS_CONSULTAS) { entry ->
            MinhasConsultasScreen(
                lifecycleOwner = entry,
                onBack = { navController.popBackStack() },
                onNovaConsulta = {
                    navController.navigate(Routes.BUSCA) {
                        popUpTo(Routes.BUSCA) { inclusive = true }
                    }
                },
                onReagendar = { doctorId, consultaId ->
                    // launchSingleTop: a double tap must not stack two copies of the reschedule screen.
                    navController.navigate("${Routes.DETALHE_MEDICO}/$doctorId?consultaId=$consultaId") { launchSingleTop = true }
                },
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
        composable(Routes.MINHA_AGENDA) { entry ->
            MinhaAgendaScreen(
                lifecycleOwner = entry,
                onReagendar = { doctorId, consultaId ->
                    // launchSingleTop: a double tap must not stack two copies of the reschedule screen.
                    navController.navigate("${Routes.DETALHE_MEDICO}/$doctorId?consultaId=$consultaId") { launchSingleTop = true }
                },
            )
        }
    }
}

/** Login as the only entry in the back stack, optionally with the "senha redefinida" notice. */
private fun NavHostController.goToLogin(passwordChanged: Boolean) {
    navigate("${Routes.LOGIN}?senhaRedefinida=$passwordChanged") {
        popUpTo(graph.id) { inclusive = true }
    }
}
