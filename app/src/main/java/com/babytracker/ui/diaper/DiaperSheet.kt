package com.babytracker.ui.diaper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.DiaperType
import com.babytracker.ui.common.DateTimeFieldRow
import com.babytracker.ui.common.FieldAccent
import com.babytracker.ui.component.DiaperTypeIcon
import com.babytracker.ui.component.labelRes
import com.babytracker.ui.theme.diaperColors
import java.time.Instant

const val DIAPER_SAVE_TAG = "DiaperSaveButton"

// Reveal the note counter only within this many chars of the cap; stays hidden the rest of the time.
private const val COUNTER_REVEAL_AT = 40

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
    val diaper = diaperColors()
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

            DiaperTypeSelector(
                selected = state.type,
                onSelect = onTypeChange,
                enabled = !state.isSaving,
                activeContainerColor = diaper.container,
                activeContentColor = diaper.onContainer,
            )
            Spacer(Modifier.height(12.dp))

            DateTimeFieldRow(
                label = stringResource(R.string.diaper_time_label),
                timestamp = state.timestamp,
                onChange = onTimeChange,
                enabled = !state.isSaving,
                accent = FieldAccent(
                    accent = diaper.accent,
                    onAccent = diaper.onAccent,
                    container = diaper.container,
                    onContainer = diaper.onContainer,
                ),
            )
            state.timeError?.let { error ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.diaper_notes_label)) },
                enabled = !state.isSaving,
                // Bound the height so a long note scrolls inside the field instead of growing the sheet.
                maxLines = 4,
                // Show the counter only as the limit nears, keeping the resting state calm.
                supportingText = if (state.notes.length >= DIAPER_NOTES_MAX - COUNTER_REVEAL_AT) {
                    {
                        Text(
                            text = stringResource(
                                R.string.diaper_notes_counter,
                                state.notes.length,
                                DIAPER_NOTES_MAX,
                            ),
                        )
                    }
                } else {
                    null
                },
                // Keep the field in the diaper (yellow) palette instead of the M3 primary (pink) default.
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = diaper.accent,
                    focusedLabelColor = diaper.accent,
                    cursorColor = diaper.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            state.saveError?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onConfirm,
                enabled = !state.isSaving,
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = diaper.accent,
                    contentColor = diaper.onAccent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DIAPER_SAVE_TAG),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = diaper.onAccent,
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
                    colors = ButtonDefaults.textButtonColors(contentColor = diaper.accent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.diaper_view_history), style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
                colors = ButtonDefaults.textButtonColors(contentColor = diaper.accent),
                modifier = Modifier.fillMaxWidth(),
            ) {
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
    activeContainerColor: Color,
    activeContentColor: Color,
) {
    SingleChoiceSegmentedButtonRow(
        // 48dp floor: the M3 segmented default (40dp) is below the one-handed-at-3am tap minimum.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        DiaperType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = DiaperType.entries.size),
                // Keep the chosen type legible even while the form is disabled mid-save.
                enabled = enabled || selected == type,
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = activeContainerColor,
                    activeContentColor = activeContentColor,
                ),
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DiaperTypeIcon(
                            type = type,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(type.labelRes()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        }
    }
}
