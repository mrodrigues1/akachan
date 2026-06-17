package com.babytracker.ui.diaper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.babytracker.domain.model.DiaperType
import com.babytracker.ui.common.DateTimeFieldRow
import java.time.Instant

const val DIAPER_SAVE_TAG = "DiaperSaveButton"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaperSheet(
    state: DiaperUiState,
    onTypeChange: (DiaperType) -> Unit,
    onTimeChange: (Instant) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToHistory: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                    if (!state.isEditing) R.string.diaper_add_title else R.string.diaper_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            DiaperTypeSelector(selected = state.type, onSelect = onTypeChange, enabled = !state.isSaving)
            Spacer(Modifier.height(12.dp))

            DateTimeFieldRow(
                label = stringResource(R.string.diaper_time_label),
                timestamp = state.timestamp,
                onChange = onTimeChange,
                enabled = !state.isSaving,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.diaper_notes_label)) },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DIAPER_SAVE_TAG),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.diaper_save), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            if (onNavigateToHistory != null && !state.isEditing) {
                TextButton(
                    onClick = onNavigateToHistory,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View diaper history", style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(onClick = onDismiss, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaperTypeSelector(
    selected: DiaperType,
    onSelect: (DiaperType) -> Unit,
    enabled: Boolean,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        DiaperType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = DiaperType.entries.size),
                // Keep the chosen type legible even while the form is disabled mid-save.
                enabled = enabled || selected == type,
                label = { Text("${type.emoji} ${type.label}") },
            )
        }
    }
}
