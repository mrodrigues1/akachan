package com.babytracker.ui.vaccine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.ui.theme.VaccinePalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Shared building blocks for the vaccine dashboard and history screens, so the two surfaces speak the
// same visual language (flat rows with a leading status dot, one add affordance) without duplicating
// the row chrome, the date format, or the name+dose composition.

/** A 10dp domain-colored status dot: filled = administered, hollow (low alpha) = still scheduled. */
@Composable
internal fun StatusDot(color: Color, filled: Boolean) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.18f)),
    )
}

/** The full-width "Add vaccine" bar pinned to the bottom of the dashboard and history screens. */
@Composable
internal fun AddVaccineBar(
    colors: VaccinePalette,
    onAddVaccine: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = onAddVaccine,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(text = stringResource(R.string.vaccine_add_title), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Confirmation before deleting a vaccine record. A record is a long-lived document (daycare, school),
 * so deletion is gated by this dialog and a follow-up undo snackbar. Shared by the dashboard and
 * history screens so both delete paths feel identical.
 */
@Composable
internal fun DeleteConfirmDialog(vaccine: VaccinePalette, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vaccine_delete_title)) },
        text = { Text(stringResource(R.string.vaccine_delete_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                // Destructive action carries the error role, not the friendly domain accent.
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = vaccine.onContainer),
            ) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** "Name · Dose" when a dose label exists, otherwise just the name. Localized separator. */
@Composable
internal fun VaccineRecord.nameWithDose(): String =
    doseLabel?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.vaccine_name_with_dose, name, it) } ?: name

// Cache the formatter but rebuild it when the device locale changes, so a runtime language switch is
// reflected instead of freezing the locale captured at class-load. UI formatting is main-thread only,
// so the unsynchronized cache is safe here.
private var cachedLocale: Locale? = null
private var cachedFormatter: DateTimeFormatter? = null

private fun vaccineDateFormatter(): DateTimeFormatter {
    val locale = Locale.getDefault()
    return cachedFormatter?.takeIf { locale == cachedLocale }
        ?: DateTimeFormatter.ofPattern("EEE, MMM d", locale).also {
            cachedFormatter = it
            cachedLocale = locale
        }
}

/** Short weekday + month/day label used across the vaccine rows. */
internal fun Instant.toVaccineDateLabel(): String =
    vaccineDateFormatter().format(atZone(ZoneId.systemDefault()).toLocalDate())
