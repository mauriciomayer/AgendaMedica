package com.agendamedica.app.ui.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agendamedica.app.R
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.AgendaMedicaLogo
import kotlinx.coroutines.delay

/** Fixed splash duration per spec-4-1-splash-icone.md ("Always": duração fixa de 1600ms). */
private const val SPLASH_DURATION_MS = 1600L

/**
 * First screen the app shows (NavHost's `startDestination`): the app logo and "Agenda Médica"
 * centered over `SurfaceCanvas`, no user interaction (no tap-to-skip). After a fixed 1600ms of
 * real wall-clock time, [onFinished] fires so the caller can move on to the normal flow — see
 * [com.agendamedica.app.ui.navigation.AgendaMedicaNavHost], which also pops this destination off
 * the back stack so "voltar" from the next screen exits the app instead of returning here.
 *
 * A config change (e.g. rotation) with no `android:configChanges` declared recreates the Activity
 * and this composable from scratch — a bare `LaunchedEffect(Unit) { delay(1600) }` would then
 * restart the count from zero, doubling the perceived splash time. [startTimeMillis] is saved
 * across that recreation (`rememberSaveable`, backed by the recreated Activity's saved-instance
 * bundle), so the effect below computes only the *remaining* time instead of a fresh 1600ms.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val startTimeMillis = rememberSaveable { System.currentTimeMillis() }
    LaunchedEffect(startTimeMillis) {
        val elapsed = System.currentTimeMillis() - startTimeMillis
        delay((SPLASH_DURATION_MS - elapsed).coerceAtLeast(0))
        onFinished()
    }

    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AgendaMedicaLogo(size = 216.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                color = AgendaMedicaColors.inkPrimary,
            )
        }
    }
}
