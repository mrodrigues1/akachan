package com.babytracker.ui.onboarding.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyInfoStepContent(
    name: String,
    selectedDate: LocalDate,
    showAgeWarning: Boolean,
    isNextEnabled: Boolean,
    onNameChanged: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM d, yyyy") }
    val ageText = remember(selectedDate) { selectedDate.toAgeLabel() }
    val todayMillis = remember {
        LocalDate.now().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }
    val initialMillis = remember(selectedDate) {
        selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }
    val datePickerState = key(selectedDate) {
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayMillis
            },
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            OnboardingHeroStrip(
                title = "Baby profile",
                stepLabel = "STEP 2 OF 3",
                progress = 0.66f,
                accentColor = MaterialTheme.colorScheme.primary,
                accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Start with the basics",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This helps keep ages and records clear across feeding and sleep history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChanged,
                    label = { Text("Baby's name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { if (name.isNotBlank()) onNext() },
                    ),
                    supportingText = if (name.length > 40) {
                        { Text("${name.length}/50") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                DateOfBirthField(
                    value = selectedDate.format(formatter),
                    onClick = { showDatePicker = true },
                )
                Spacer(modifier = Modifier.height(12.dp))
                BabyAgeSummary(
                    ageText = ageText,
                    showAgeWarning = showAgeWarning,
                )
            }
            Button(
                onClick = onNext,
                enabled = isNextEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("Continue")
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            onDateSelected(date)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun DateOfBirthField(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Date of birth") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Select date",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick),
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
            Text(
                text = ageText,
                style = MaterialTheme.typography.titleSmall,
            )
            if (showAgeWarning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Akachan is designed for babies 0-12 months.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun LocalDate.toAgeLabel(): String {
    val today = LocalDate.now()
    if (isAfter(today)) {
        return "Date cannot be in the future"
    }

    val period = Period.between(this, today)
    return buildString {
        if (period.months > 0) {
            append("${period.months} month${if (period.months != 1) "s" else ""}, ")
        }
        append("${period.days} day${if (period.days != 1) "s" else ""} old")
    }
}
