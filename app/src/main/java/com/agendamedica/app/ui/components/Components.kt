package com.agendamedica.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.agendamedica.app.ui.theme.AgendaMedicaColors
import com.agendamedica.app.ui.theme.ShapeMd
import com.agendamedica.app.ui.theme.ShapePill

/**
 * Reusable pieces shared by every screen in this story, built directly from DESIGN.md's
 * Components section rather than reimplemented per-screen. Kept in one small file since this
 * story only needs a handful of them; split up if a later story grows this list.
 */

/** Full-width primary action button. Disabled state uses surface-disabled/ink-disabled per DESIGN.md. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ShapeMd,
        colors = ButtonDefaults.buttonColors(
            containerColor = AgendaMedicaColors.accentPrimary,
            contentColor = AgendaMedicaColors.surfaceCard,
            disabledContainerColor = AgendaMedicaColors.surfaceDisabled,
            disabledContentColor = AgendaMedicaColors.inkDisabled,
        ),
        contentPadding = PaddingValues(vertical = 14.dp),
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Secondary action, outlined, brand-colored border/text by default. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: androidx.compose.ui.graphics.Color = AgendaMedicaColors.accentPrimary,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ShapeMd,
        border = BorderStroke(1.dp, if (enabled) borderColor else AgendaMedicaColors.borderInput),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = borderColor),
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Multi/single-select toggle chip (Convênio, Dia de atendimento, Paciente/Médico tab).
 * Active: 2px accent border + accent tint background + accent text. Inactive: 1px neutral
 * border + white background + secondary text.
 */
@Composable
fun ToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) {
        BorderStroke(2.dp, AgendaMedicaColors.accentPrimary)
    } else {
        BorderStroke(1.dp, AgendaMedicaColors.borderInput)
    }
    val containerColor = if (selected) AgendaMedicaColors.accentPrimaryTint else AgendaMedicaColors.surfaceCard
    val contentColor = if (selected) AgendaMedicaColors.accentPrimary else AgendaMedicaColors.inkSecondary

    OutlinedButton(
        onClick = onClick,
        shape = ShapePill,
        border = border,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier.sizeIn(minHeight = 48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Text/password input, `md` rounded, `border-input` border, per DESIGN.md. */
@Composable
fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        shape = ShapeMd,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AgendaMedicaColors.accentPrimary,
            unfocusedBorderColor = AgendaMedicaColors.borderInput,
            focusedContainerColor = AgendaMedicaColors.surfaceInput,
            unfocusedContainerColor = AgendaMedicaColors.surfaceInput,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Circular icon button (back, etc.) with a mandatory accessible label — UX-DR7. */
@Composable
fun AccessibleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(icon, contentDescription = null)
    }
}

/** Read-only tag chip (accepted insurance), success-family, per DESIGN.md. */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = AgendaMedicaColors.successInkMuted,
        modifier = modifier
            .background(AgendaMedicaColors.successBg, ShapePill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
