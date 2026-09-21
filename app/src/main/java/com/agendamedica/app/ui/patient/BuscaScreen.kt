package com.agendamedica.app.ui.patient

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.data.location.AndroidLocationProvider
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.search.DoctorSummary
import com.agendamedica.app.ui.components.AccessibleIconButton
import com.agendamedica.app.ui.components.LabeledTextField
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.TagChip
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.ShapeMd

/** Busca de médicos (FR4) — Especialidade filter, region text or GPS, result cards. */
@Composable
fun BuscaScreen(onDoctorClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: BuscaViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BuscaViewModel(DoctorRepository(), AndroidLocationProvider(context)) as T
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) viewModel.onUseLocation() else viewModel.onPermissionDenied()
    }

    val onGpsClick = {
        val permissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        val granted = permissions.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) viewModel.onUseLocation() else permissionLauncher.launch(permissions)
    }

    Scaffold(containerColor = AgendaMedicaColors.surfaceCanvas) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Buscar médicos",
                style = MaterialTheme.typography.titleLarge,
                color = AgendaMedicaColors.inkPrimary,
            )
            Spacer(Modifier.height(12.dp))

            EspecialidadeFilter(selected = uiState.especialidade, onSelected = viewModel::onEspecialidadeSelected)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledTextField(
                    value = uiState.regionFieldText,
                    onValueChange = viewModel::onRegionChanged,
                    label = "Cidade ou bairro",
                    modifier = Modifier.weight(1f),
                )
                AccessibleIconButton(
                    icon = Icons.Filled.Place,
                    contentDescription = "Usar minha localização",
                    onClick = onGpsClick,
                )
            }
            uiState.locationNotice?.let { notice ->
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = AgendaMedicaColors.dangerInk,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (uiState.isLocating) {
                Text(
                    text = "Obtendo sua localização...",
                    style = MaterialTheme.typography.bodySmall,
                    color = AgendaMedicaColors.inkSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            BuscaContent(uiState = uiState, onRetry = viewModel::retry, onDoctorClick = onDoctorClick)
        }
    }
}

@Composable
private fun BuscaContent(uiState: BuscaUiState, onRetry: () -> Unit, onDoctorClick: (String) -> Unit) {
    val errorMessage = uiState.errorMessage
    when {
        uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AgendaMedicaColors.accentPrimary)
        }
        errorMessage != null -> Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = AgendaMedicaColors.dangerInk,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            OutlineButton(text = "Tentar novamente", onClick = onRetry)
        }
        else -> {
            val results = uiState.results
            if (results.isEmpty()) {
                Text(
                    text = MSG_SEM_RESULTADOS,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgendaMedicaColors.inkSecondary,
                    modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { doctor -> DoctorCard(doctor, onClick = { onDoctorClick(doctor.id) }) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoctorCard(doctor: DoctorSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = ShapeMd,
        colors = CardDefaults.cardColors(containerColor = AgendaMedicaColors.surfaceCard),
        border = BorderStroke(1.dp, AgendaMedicaColors.borderHairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(48.dp).background(AgendaMedicaColors.accentPrimaryTint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initialsOf(doctor.name),
                    style = MaterialTheme.typography.labelLarge,
                    color = AgendaMedicaColors.accentPrimary,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(doctor.name, style = MaterialTheme.typography.titleMedium, color = AgendaMedicaColors.inkPrimary)
                Text(
                    doctor.especialidade.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgendaMedicaColors.inkSecondary,
                )
                Text(
                    doctor.city,
                    style = MaterialTheme.typography.bodySmall,
                    color = AgendaMedicaColors.inkTertiary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    doctor.convenios.forEach { TagChip(it.label) }
                }
            }
        }
    }
}

/** First letters of the first two words, ignoring a leading "Dr."/"Dra." title. */
internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val significant = words.filterNot { it.trimEnd('.').lowercase() in setOf("dr", "dra") }.ifEmpty { words }
    return significant.take(2).joinToString("") { it.first().uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EspecialidadeFilter(selected: Especialidade?, onSelected: (Especialidade?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label ?: "Todas",
            onValueChange = {},
            readOnly = true,
            label = { Text("Especialidade") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = ShapeMd,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Todas") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            Especialidade.entries.forEach { especialidade ->
                DropdownMenuItem(
                    text = { Text(especialidade.label) },
                    onClick = {
                        onSelected(especialidade)
                        expanded = false
                    },
                )
            }
        }
    }
}
