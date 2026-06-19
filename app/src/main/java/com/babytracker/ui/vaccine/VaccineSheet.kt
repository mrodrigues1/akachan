package com.babytracker.ui.vaccine

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.ui.common.DateTimeFieldRow
import com.babytracker.ui.common.FieldAccent
import com.babytracker.ui.theme.vaccineColors
import java.time.Instant

const val VACCINE_SAVE_TAG = "VaccineSaveButton"
const val VACCINE_NAME_TAG = "VaccineNameField"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineSheet(
    state: VaccineUiState,
    onNameChange: (String) -> Unit,
    onDoseChange: (String) -> Unit,
    onModeChange: (VaccineStatus) -> Unit,
    onDateChange: (Instant) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToHistory: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val vaccine = vaccineColors()
    ModalBottomSheet(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(
                    if (!state.isEditing) R.string.vaccine_add_title else R.string.vaccine_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.vaccine_name_label)) },
                singleLine = true,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VACCINE_NAME_TAG),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.suggestions.forEach { suggestion ->
                    FilterChip(
                        selected = state.name == suggestion,
                        onClick = { onNameChange(suggestion) },
                        enabled = !state.isSaving,
                        label = { Text(suggestion) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = vaccine.container,
                            selectedLabelColor = vaccine.onContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.doseLabel,
                onValueChange = onDoseChange,
                label = { Text(stringResource(R.string.vaccine_dose_label)) },
                singleLine = true,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(
                    VaccineStatus.ADMINISTERED to R.string.vaccine_mode_administered,
                    VaccineStatus.SCHEDULED to R.string.vaccine_mode_scheduled,
                )
                modes.forEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        selected = state.status == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        enabled = !state.isSaving || state.status == mode,
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = vaccine.container,
                            activeContentColor = vaccine.onContainer,
                        ),
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            DateTimeFieldRow(
                label = stringResource(
                    if (state.status == VaccineStatus.SCHEDULED) {
                        R.string.vaccine_date_scheduled_label
                    } else {
                        R.string.vaccine_date_administered_label
                    },
                ),
                timestamp = state.date,
                onChange = onDateChange,
                enabled = !state.isSaving,
                accent = FieldAccent(
                    accent = vaccine.accent,
                    onAccent = vaccine.onAccent,
                    container = vaccine.container,
                    onContainer = vaccine.onContainer,
                ),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.vaccine_notes_label)) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            state.validationError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onConfirm,
                enabled = !state.isSaving,
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = vaccine.accent,
                    contentColor = vaccine.onAccent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VACCINE_SAVE_TAG),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = vaccine.onAccent,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.vaccine_save), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            if (onNavigateToHistory != null && !state.isEditing) {
                TextButton(
                    onClick = onNavigateToHistory,
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = vaccine.accent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.vaccine_view_history), style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
                colors = ButtonDefaults.textButtonColors(contentColor = vaccine.accent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
