package com.babytracker.ui.doctorvisit

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.ui.common.DateTimeFieldRow
import com.babytracker.ui.common.FieldAccent
import com.babytracker.ui.theme.doctorVisitColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val DOCTOR_VISIT_SAVE_TAG = "DoctorVisitSaveButton"

// Localized (locale-aware field order) medium date, e.g. "Jun 20, 2026" / "20 de jun. de 2026".
private val snapshotDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorVisitSheet(
    state: DoctorVisitUiState,
    onDateChange: (Instant) -> Unit,
    onProviderChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onToggleQuestion: (Long) -> Unit,
    onAttachSnapshot: (String) -> Unit,
    onViewSnapshot: () -> Unit,
    onManageQuestions: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToHistory: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = doctorVisitColors()
    val savingDescription = stringResource(R.string.doctor_visit_saving)
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
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(
                    if (state.isEditing) R.string.doctor_visit_edit_title else R.string.doctor_visit_add_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))

            DateTimeFieldRow(
                label = stringResource(R.string.doctor_visit_date),
                timestamp = state.date,
                onChange = onDateChange,
                enabled = !state.isSaving,
                accent = FieldAccent(
                    accent = colors.accent,
                    onAccent = colors.onAccent,
                    container = colors.container,
                    onContainer = colors.onContainer,
                ),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.providerName,
                onValueChange = onProviderChange,
                label = { Text(stringResource(R.string.doctor_visit_provider_hint)) },
                singleLine = true,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.doctor_visit_notes_hint)) },
                enabled = !state.isSaving,
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            AttachQuestionsSection(
                state = state,
                onToggleQuestion = onToggleQuestion,
                onManageQuestions = onManageQuestions,
            )
            Spacer(Modifier.height(8.dp))

            SnapshotSection(
                state = state,
                onAttachSnapshot = onAttachSnapshot,
                onViewSnapshot = onViewSnapshot,
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DOCTOR_VISIT_SAVE_TAG)
                    .semantics { if (state.isSaving) stateDescription = savingDescription },
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.onAccent,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.doctor_visit_save), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            if (onNavigateToHistory != null && !state.isEditing) {
                TextButton(
                    onClick = onNavigateToHistory,
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.doctor_visit_view_history), style = MaterialTheme.typography.labelLarge)
                }
            }
            if (onNavigateToSettings != null && !state.isEditing) {
                TextButton(
                    onClick = onNavigateToSettings,
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.doctor_visit_reminder_settings_button),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AttachQuestionsSection(
    state: DoctorVisitUiState,
    onToggleQuestion: (Long) -> Unit,
    onManageQuestions: () -> Unit,
) {
    val colors = doctorVisitColors()
    val questions: List<VisitQuestion> = remember(state.attachedQuestions, state.inboxQuestions) {
        val attachedIds = state.attachedQuestions.map { it.id }.toSet()
        state.attachedQuestions + state.inboxQuestions.filterNot { it.id in attachedIds }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.doctor_visit_attach_questions),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(
            onClick = onManageQuestions,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
        ) {
            Text(stringResource(R.string.doctor_visit_manage_questions))
        }
    }
    questions.forEach { question ->
        val checked = question.id in state.selectedQuestionIds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = { onToggleQuestion(question.id) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = colors.accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = question.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SnapshotSection(
    state: DoctorVisitUiState,
    onAttachSnapshot: (String) -> Unit,
    onViewSnapshot: () -> Unit,
) {
    val colors = doctorVisitColors()
    if (!state.isEditing) {
        Text(
            text = stringResource(R.string.doctor_visit_snapshot_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val todayLabel = remember {
        snapshotDateFormatter.format(Instant.now().atZone(ZoneId.systemDefault()).toLocalDate())
    }
    val snapshotLabel = stringResource(R.string.doctor_visit_snapshot_label, todayLabel)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { onAttachSnapshot(snapshotLabel) },
            enabled = !state.isSaving,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
        ) {
            Text(
                stringResource(
                    if (state.snapshotLabel != null) {
                        R.string.doctor_visit_snapshot_replace
                    } else {
                        R.string.doctor_visit_snapshot_attach
                    },
                ),
            )
        }
        if (state.snapshotLabel != null) {
            TextButton(
                onClick = onViewSnapshot,
                enabled = !state.isSaving,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) {
                Text(stringResource(R.string.doctor_visit_snapshot_view))
            }
        }
    }
}
