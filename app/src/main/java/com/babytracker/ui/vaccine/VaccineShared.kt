package com.babytracker.ui.vaccine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.18f)),
    )
}

/** A 10dp hollow ring dot used for to-schedule rows, distinct from the filled/low-alpha [StatusDot]. */
@Composable
internal fun StatusRingDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .border(width = 1.5.dp, color = color, shape = CircleShape),
    )
}

/**
 * A to-schedule row: a tinted (container-colored) rounded background marks it visually distinct from
 * plain scheduled rows, a ring dot reinforces the state, and the trailing controls let the parent
 * Schedule it (one tap), mark it given directly, or delete it. Tapping the body edits.
 */
@Composable
internal fun ToScheduleRow(
    record: VaccineRecord,
    colors: VaccinePalette,
    isPastTarget: Boolean,
    onSchedule: () -> Unit,
    onMarkGiven: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val editLabel = stringResource(R.string.vaccine_edit_content_description, record.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.container)
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = editLabel, onClick = onEdit)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusRingDot(color = colors.onContainer)
        Spacer(Modifier.size(12.dp))
        val date = record.scheduledDate?.toVaccineDateLabel().orEmpty()
        val subtitle = if (isPastTarget) {
            stringResource(R.string.vaccine_to_schedule_past_target, date)
        } else {
            stringResource(R.string.vaccine_to_schedule_target, date)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.nameWithDose(),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onContainer)
        }
        TextButton(
            onClick = onSchedule,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.onContainer),
        ) { Text(stringResource(R.string.vaccine_action_schedule)) }
        IconButton(onClick = onMarkGiven) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.vaccine_mark_given_content_description, record.name),
                tint = colors.onContainer,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.vaccine_delete_content_description, record.name),
                tint = colors.onContainer,
            )
        }
    }
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
