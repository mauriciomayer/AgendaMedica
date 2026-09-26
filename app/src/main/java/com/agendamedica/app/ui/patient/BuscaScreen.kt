package com.agendamedica.app.ui.patient

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendamedica.app.data.location.AndroidLocationProvider
import com.agendamedica.app.data.repository.DoctorRepository
import com.agendamedica.app.domain.model.Especialidade
import com.agendamedica.app.domain.search.DoctorSummary
import com.agendamedica.app.ui.components.AppCard
import com.agendamedica.app.ui.components.Avatar
import com.agendamedica.app.ui.components.BotaoPilula
import com.agendamedica.app.ui.components.OutlineButton
import com.agendamedica.app.ui.components.TagChip
import com.agendamedica.app.ui.components.TelaPadrao
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.ShapeMd

/** Busca de médicos (FR4) — Especialidade filter, region text or GPS, result cards. Design prototype layout (Story 8.2). */
@Composable
fun BuscaScreen(onDoctorClick: (String) -> Unit = {}, onMinhasConsultas: () -> Unit = {}) {
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

    BuscaConteudo(
        uiState = uiState,
        onEspecialidadeSelected = viewModel::onEspecialidadeSelected,
        onRegionChanged = viewModel::onRegionChanged,
        onGpsClick = onGpsClick,
        onRetry = viewModel::retry,
        onDoctorClick = onDoctorClick,
        onMinhasConsultas = onMinhasConsultas,
    )
}

/** Stateless Busca: everything the screen shows comes from [uiState], so it can be tested without a ViewModel. */
@Composable
internal fun BuscaConteudo(
    uiState: BuscaUiState,
    onEspecialidadeSelected: (Especialidade?) -> Unit,
    onRegionChanged: (String) -> Unit,
    onGpsClick: () -> Unit,
    onRetry: () -> Unit,
    onDoctorClick: (String) -> Unit,
    onMinhasConsultas: () -> Unit,
) {
    TelaPadrao(
        title = "Agende",
        subtitle = "Encontre um médico e agende",
        scrollable = false,
        actions = { BotaoPilula(text = "Minhas consultas", onClick = onMinhasConsultas) },
    ) {
        // One lazy list holds the filter card too, so with a big font or the keyboard open the whole screen
        // scrolls instead of the results being squeezed into what is left below a fixed card.
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
            item {
                AppCard {
                    EspecialidadeFilter(selected = uiState.especialidade, onSelected = onEspecialidadeSelected)
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CampoCidade(value = uiState.regionFieldText, onValueChange = onRegionChanged, modifier = Modifier.weight(1f))
                        BotaoLocalizacao(onClick = onGpsClick)
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
                }
            }
            resultados(uiState = uiState, onRetry = onRetry, onDoctorClick = onDoctorClick)
        }
    }
}

private fun LazyListScope.resultados(uiState: BuscaUiState, onRetry: () -> Unit, onDoctorClick: (String) -> Unit) {
    val errorMessage = uiState.errorMessage
    when {
        uiState.isLoading -> item {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AgendaMedicaColors.accentPrimary)
            }
        }
        errorMessage != null -> item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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
        }
        else -> {
            val results = uiState.results
            item {
                Text(
                    text = "${results.size} médico(s) encontrado(s)",
                    color = AgendaMedicaColors.inkSecondary,
                    fontSize = 13.sp,
                )
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        text = MSG_SEM_RESULTADOS,
                        color = AgendaMedicaColors.inkTertiary,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(vertical = 30.dp, horizontal = 10.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                items(results, key = { it.id }) { doctor -> DoctorCard(doctor, onClick = { onDoctorClick(doctor.id) }) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoctorCard(doctor: DoctorSummary, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Avatar(nome = doctor.name, tamanho = 44.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    doctor.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                    color = AgendaMedicaColors.inkPrimary,
                )
                Text(
                    "${doctor.especialidade.label} · ${doctor.city}",
                    fontSize = 13.sp,
                    color = AgendaMedicaColors.inkSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    doctor.convenios.forEach { TagChip(it.label) }
                }
            }
        }
    }
}

/** Text field of the filter card: no label, the example inside (prototype "Cidade (ex.: São Paulo, SP)"). */
@Composable
private fun CampoCidade(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val foco = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Cidade (ex.: São Paulo, SP)", color = AgendaMedicaColors.inkTertiary) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { foco.clearFocus() }),
        shape = ShapeMd,
        colors = coresDoCampo(),
        modifier = modifier,
    )
}

@Composable
private fun coresDoCampo() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AgendaMedicaColors.accentPrimary,
    unfocusedBorderColor = AgendaMedicaColors.borderInput,
    focusedContainerColor = AgendaMedicaColors.surfaceInput,
    unfocusedContainerColor = AgendaMedicaColors.surfaceInput,
)

/** Square GPS button of the prototype: 40dp drawn (border, input fill, blue icon), 48dp touch target. */
@Composable
private fun BotaoLocalizacao(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(ShapeMd)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Usar minha localização" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(AgendaMedicaColors.surfaceInput, ShapeMd)
                .border(1.dp, AgendaMedicaColors.borderInput, ShapeMd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = AgendaMedicaColors.accentPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EspecialidadeFilter(selected: Especialidade?, onSelected: (Especialidade?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        // The selected value ("Todas as especialidades", "Cardiologia"...) is the field's own text, as in the prototype.
        OutlinedTextField(
            value = selected?.label ?: "Todas as especialidades",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = ShapeMd,
            colors = coresDoCampo(),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Todas as especialidades") },
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
