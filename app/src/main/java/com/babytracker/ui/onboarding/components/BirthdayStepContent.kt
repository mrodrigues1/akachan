package com.babytracker.ui.onboarding.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.ui.onboarding.OnboardingStep
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun BirthdayStepContent(
    babyName: String,
    selectedDate: LocalDate,
    birthDateError: String?,
    showAgeWarning: Boolean,
    bornEarly: Boolean,
    dueDate: LocalDate?,
    dueDateError: String?,
    isNextEnabled: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onBornEarlyToggled: (Boolean) -> Unit,
    onDueDateSelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBirthPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    }
    val babyLabel = babyName.trim().ifEmpty { stringResource(R.string.onboarding_baby_fallback) }
    val ageText = babyAgeLabel(selectedDate)

    OnboardingScaffold(
        modifier = modifier,
        currentStep = OnboardingStep.BIRTHDAY,
        onBack = onBack,
        primaryLabel = stringResource(R.string.onboarding_continue),
        onPrimary = onNext,
        primaryEnabled = isNextEnabled,
        primaryTestTag = "onboarding_birthday_primary_action",
    ) {
        Text(
            text = stringResource(R.string.onboarding_birthday_question, babyLabel),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_birthday_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        DateField(
            value = selectedDate.format(formatter),
            label = stringResource(R.string.onboarding_dob_label),
            error = birthDateError,
            contentDescription = if (birthDateError == null) {
                stringResource(R.string.onboarding_dob_cd, selectedDate.format(formatter))
            } else {
                stringResource(R.string.onboarding_dob_cd_error, selectedDate.format(formatter), birthDateError)
            },
            changeLabel = stringResource(R.string.onboarding_change_dob),
            onClick = { showBirthPicker = true },
        )
        Spacer(modifier = Modifier.height(12.dp))
        BabyAgeSummary(ageText = ageText, showAgeWarning = showAgeWarning)
        Spacer(modifier = Modifier.height(16.dp))
        BornEarlyToggle(checked = bornEarly, onToggle = onBornEarlyToggled)
        AnimatedVisibility(
            visible = bornEarly,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                DateField(
                    value = dueDate?.format(formatter) ?: stringResource(R.string.onboarding_due_date_unset),
                    label = stringResource(R.string.onboarding_due_date_label),
                    error = dueDateError,
                    contentDescription = stringResource(
                        R.string.onboarding_due_date_label,
                    ) + ", " + (dueDate?.format(formatter) ?: stringResource(R.string.onboarding_due_date_unset)),
                    changeLabel = stringResource(R.string.onboarding_due_date_label),
                    onClick = { showDuePicker = true },
                )
            }
        }
    }

    if (showBirthPicker) {
        BirthDatePickerDialog(
            initialDate = selectedDate,
            maxDate = LocalDate.now(),
            minDate = null,
            onConfirm = {
                onDateSelected(it)
                showBirthPicker = false
            },
            onDismiss = { showBirthPicker = false },
        )
    }
    if (showDuePicker) {
        BirthDatePickerDialog(
            initialDate = dueDate ?: selectedDate,
            maxDate = null,
            minDate = selectedDate,
            onConfirm = {
                onDueDateSelected(it)
                showDuePicker = false
            },
            onDismiss = { showDuePicker = false },
        )
    }
}

@Composable
private fun BornEarlyToggle(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_born_early),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.onboarding_born_early_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun DateField(
    value: String,
    label: String,
    error: String?,
    contentDescription: String,
    changeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = contentDescription
                onClick(label = changeLabel) {
                    onClick()
                    true
                }
            },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = {
                Icon(imageVector = Icons.Default.DateRange, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClickLabel = changeLabel, role = Role.Button, onClick = onClick)
                .clearAndSetSemantics {},
        )
    }
}

@Composable
private fun BabyAgeSummary(
    ageText: String,
    showAgeWarning: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (showAgeWarning) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (showAgeWarning) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = ageText, style = MaterialTheme.typography.titleSmall)
            if (showAgeWarning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_age_range),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(
    initialDate: LocalDate,
    maxDate: LocalDate?,
    minDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = remember(initialDate) {
        initialDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }
    val maxMillis = remember(maxDate) {
        maxDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()
    }
    val minMillis = remember(minDate) {
        minDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()
    }
    val datePickerState = key(initialDate, maxDate, minDate) {
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    (maxMillis == null || utcTimeMillis <= maxMillis) &&
                        (minMillis == null || utcTimeMillis >= minMillis)
            },
        )
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                },
            ) {
                Text(stringResource(R.string.onboarding_use_date))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun babyAgeLabel(date: LocalDate): String {
    val today = LocalDate.now()
    if (date.isAfter(today)) return stringResource(R.string.error_birth_date_future)

    val period = Period.between(date, today)
    val parts = buildList {
        if (period.years > 0) {
            add(pluralStringResource(R.plurals.onboarding_age_years, period.years, period.years))
        }
        if (period.months > 0) {
            add(pluralStringResource(R.plurals.onboarding_age_months, period.months, period.months))
        }
        if (period.years == 0) {
            add(pluralStringResource(R.plurals.onboarding_age_days, period.days, period.days))
        }
    }
    val separator = stringResource(R.string.onboarding_age_separator)
    return stringResource(R.string.onboarding_age_old, parts.joinToString(separator))
}
