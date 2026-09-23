package com.agendamedica.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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

/**
 * Text/password input, `md` rounded, `border-input` border, per DESIGN.md.
 *
 * When [isPassword] is true, a show/hide eye icon is added (spec-6-1: added once here, covering
 * every screen that uses this shared component — Login, Cadastro de Médico, Cadastro de
 * Paciente, Nova Senha — instead of duplicated per screen). Visibility state is local to the
 * field ([remember]) and resets when the screen recomposes from scratch; toggling it never loses
 * the text already typed.
 */
@Composable
fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isEmail: Boolean = false,
) {
    var senhaVisivel by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(if (isEmail) filtrarEmail(it, value) else it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (isEmail) {
            KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            )
        } else {
            KeyboardOptions.Default
        },
        visualTransformation = if (isPassword && !senhaVisivel) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                    Icon(
                        imageVector = if (senhaVisivel) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (senhaVisivel) "Ocultar senha" else "Mostrar senha",
                    )
                }
            }
        } else {
            null
        },
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

private val EMAIL_CHARS = Regex("[A-Za-z0-9._%+\\-]")

/**
 * Typing mask for e-mail fields (spec-6-5): keeps only ASCII letters, digits and `@ . _ - + %`,
 * drops everything else silently, and keeps only the first `@`. Case is preserved.
 */
internal fun filtrarEmail(input: String, anterior: String = ""): String {
    var texto = input
    // Typing an "@" before an existing one must reject the NEW "@", not delete the original:
    // drop "@" only from the inserted segment (what differs from [anterior]).
    if ('@' in anterior && input.count { it == '@' } > 1) {
        val prefixo = anterior.commonPrefixWith(input).length
        val sufixo = anterior.commonSuffixWith(input).length.coerceAtMost(minOf(anterior.length, input.length) - prefixo)
        val fim = input.length - sufixo
        texto = input.substring(0, prefixo) + input.substring(prefixo, fim).replace("@", "") + input.substring(fim)
    }
    val sb = StringBuilder()
    var temArroba = false
    for (c in texto) {
        when {
            c == '@' -> if (!temArroba) {
                sb.append(c)
                temArroba = true
            }
            EMAIL_CHARS.matches(c.toString()) -> sb.append(c)
        }
    }
    return sb.toString()
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
