package com.babytracker.ui.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.util.centimetresToMillimetres
import com.babytracker.util.gramsToKilograms
import com.babytracker.util.gramsToPoundsOunces
import com.babytracker.util.inchesToMillimetres
import com.babytracker.util.kilogramsToGrams
import com.babytracker.util.lengthUnitLabel
import com.babytracker.util.millimetresToCentimetres
import com.babytracker.util.millimetresToInches
import com.babytracker.util.poundsOuncesToGrams
import com.babytracker.util.weightUnitLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementSheet(
    type: GrowthType,
    system: MeasurementSystem,
    onDismiss: () -> Unit,
    onSave: (valueCanonical: Long, takenAt: Instant, notes: String?) -> Unit,
    modifier: Modifier = Modifier,
    initial: GrowthMeasurement? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var primaryValue by remember(type, system, initial) { mutableStateOf(initialPrimary(type, system, initial)) }
    var ouncesValue by remember(type, system, initial) { mutableStateOf(initialOunces(type, system, initial)) }
    var notes by remember(initial) { mutableStateOf(initial?.notes.orEmpty()) }
    var selectedDate by remember(initial) {
        mutableStateOf(
            initial?.takenAt?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.now(),
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val imperialWeight = type == GrowthType.WEIGHT && system == MeasurementSystem.IMPERIAL
    val canonical = parseCanonical(type, system, primaryValue, ouncesValue)
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val dateDescription = stringResource(R.string.growth_date_field_cd, selectedDate.format(dateFormatter))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (initial != null) R.string.growth_edit_title else R.string.growth_add_title,
                    stringResource(type.tabLabelRes()).lowercase(),
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            Column {
                Text(
                    text = stringResource(R.string.growth_date_field_label),
                    style = MaterialTheme.typography.labelMedium,
                    // Teal via the GrowthTealTheme override this sheet renders inside.
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                        )
                        .clickable { showDatePicker = true }
                        // Announce as a button carrying the date, so TalkBack users know it opens a picker.
                        .semantics {
                            role = Role.Button
                            contentDescription = dateDescription
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = selectedDate.format(dateFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (imperialWeight) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = primaryValue,
                        onValueChange = { primaryValue = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.growth_unit_lb)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("growth_value_input"),
                    )
                    OutlinedTextField(
                        value = ouncesValue,
                        onValueChange = { ouncesValue = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.growth_unit_oz)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                val unit = if (type == GrowthType.WEIGHT) weightUnitLabel(system) else lengthUnitLabel(system)
                OutlinedTextField(
                    value = primaryValue,
                    onValueChange = { primaryValue = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(stringResource(R.string.growth_value_label, unit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("growth_value_input"),
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.growth_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val value = canonical ?: return@Button
                    val takenAt = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    onSave(value, takenAt, notes.takeIf { it.isNotBlank() })
                },
                enabled = canonical != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("growth_save_measurement"),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** Pre-fills the main value field from an existing measurement, converted back into the display unit. */
private fun initialPrimary(type: GrowthType, system: MeasurementSystem, initial: GrowthMeasurement?): String {
    val canonical = initial?.valueCanonical ?: return ""
    return when {
        type == GrowthType.WEIGHT && system == MeasurementSystem.IMPERIAL ->
            gramsToPoundsOunces(canonical).first.toString()
        type == GrowthType.WEIGHT -> trimNumber(gramsToKilograms(canonical))
        system == MeasurementSystem.IMPERIAL -> trimNumber(millimetresToInches(canonical))
        else -> trimNumber(millimetresToCentimetres(canonical))
    }
}

/** Pre-fills the ounces field for imperial weight; empty for every other metric/system. */
private fun initialOunces(type: GrowthType, system: MeasurementSystem, initial: GrowthMeasurement?): String {
    val canonical = initial?.valueCanonical ?: return ""
    return if (type == GrowthType.WEIGHT && system == MeasurementSystem.IMPERIAL) {
        gramsToPoundsOunces(canonical).second.toString()
    } else {
        ""
    }
}

/** Up to two decimals, trailing zeros and a dangling point stripped (e.g. 6.90 -> "6.9", 64.0 -> "64"). */
private fun trimNumber(value: Double): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun parseCanonical(
    type: GrowthType,
    system: MeasurementSystem,
    primary: String,
    ounces: String,
): Long? {
    val canonical = when {
        type == GrowthType.WEIGHT && system == MeasurementSystem.IMPERIAL -> {
            val lb = primary.toIntOrNull()
            val oz = ounces.toIntOrNull() ?: 0
            if (lb == null && ounces.isBlank()) return null
            poundsOuncesToGrams(lb ?: 0, oz)
        }
        type == GrowthType.WEIGHT -> primary.toDoubleOrNull()?.let { kilogramsToGrams(it) }
        system == MeasurementSystem.IMPERIAL -> primary.toDoubleOrNull()?.let { inchesToMillimetres(it) }
        else -> primary.toDoubleOrNull()?.let { centimetresToMillimetres(it) }
    }
    return canonical?.takeIf { it > 0 }
}
