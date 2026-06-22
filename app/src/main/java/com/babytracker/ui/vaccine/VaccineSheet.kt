package com.babytracker.ui.vaccine

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
                .imePadding()
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

            val nameErrorMsg = state.validationError?.takeIf { state.errorField == VaccineField.NAME }
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.vaccine_name_label)) },
                singleLine = true,
                enabled = !state.isSaving,
                isError = nameErrorMsg != null,
                supportingText = nameErrorMsg?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
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
                        modifier = Modifier.heightIn(min = 48.dp),
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
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
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
                        modifier = Modifier.heightIn(min = 48.dp),
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
                // The date error now renders inside the row (error-colored label + announced message)
                // so a screen reader hears the failure instead of it appearing silently.
                errorText = state.validationError?.takeIf { state.errorField == VaccineField.DATE },
                // Vaccines are tracked at day granularity; the time-of-day cell is noise here.
                showTime = false,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.vaccine_notes_label)) },
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            // Generic (non-field) save errors have nowhere better to live; field errors render inline above.
            if (state.errorField == null && state.validationError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.validationError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    // Announce the failure the moment it appears, since it follows the user's Save tap.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
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
