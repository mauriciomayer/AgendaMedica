package com.agendamedica.app.ui.patient

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agendamedica.app.ui.theme.AgendaMedicaColors

/** Shell destination for an authenticated Paciente; the real search is Story 2.1. */
@Composable
fun BuscaScreen() {
    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(18.dp)) {
            Text(
                text = "Buscar médicos",
                style = MaterialTheme.typography.titleLarge,
                color = AgendaMedicaColors.inkPrimary,
            )
            Text(
                text = "A busca de médicos chega em breve.",
                style = MaterialTheme.typography.bodyMedium,
                color = AgendaMedicaColors.inkSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
