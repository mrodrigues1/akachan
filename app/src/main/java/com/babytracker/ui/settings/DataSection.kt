package com.babytracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.export.domain.model.DateRange
import kotlinx.coroutines.launch

@Composable
fun DataSection(
    state: DataExportUiState,
    onSavePdf: (DateRange) -> Unit,
    onSharePdf: (DateRange) -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissMessage: () -> Unit = {},
) {
    var showRangeSheet by remember { mutableStateOf(false) }
    val working = state.status == DataExportUiState.Status.WORKING

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_data_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (working) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
            )
        }

        if (state.status == DataExportUiState.Status.ERROR && state.message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ReportProblem,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onDismissMessage,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }

        if (state.importIncomplete) {
            WarningSurface(
                title = stringResource(R.string.settings_import_incomplete),
                actionLabel = stringResource(R.string.action_reimport),
                onAction = onImport,
                modifier = Modifier.testTag("importIncompleteNotice"),
            )
        }

        DataActionRow(
            label = stringResource(R.string.settings_export_pdf),
            value = stringResource(R.string.settings_export_pdf_value),
            actionLabel = stringResource(R.string.action_export),
            enabled = !working,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            },
            onClick = { showRangeSheet = true },
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        DataActionRow(
            label = stringResource(R.string.settings_export_json),
            value = stringResource(R.string.settings_export_json_value),
            actionLabel = stringResource(R.string.save),
            enabled = !working,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            },
            onClick = onExportJson,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        DataActionRow(
            label = stringResource(R.string.settings_export_csv),
            value = stringResource(R.string.settings_export_csv_value),
            actionLabel = stringResource(R.string.sharing_share),
            enabled = !working,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.TableChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            },
            onClick = onExportCsv,
        )

        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        DataActionRow(
            label = stringResource(R.string.settings_restore),
            value = stringResource(R.string.settings_restore_value),
            actionLabel = stringResource(R.string.action_restore),
            enabled = !working,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            },
            onClick = onImport,
        )
    }

    if (showRangeSheet) {
        RangePickerSheet(
            onDismiss = { showRangeSheet = false },
            onSave = { range ->
                showRangeSheet = false
                onSavePdf(range)
            },
            onShare = { range ->
                showRangeSheet = false
                onSharePdf(range)
            },
        )
    }

    val preview = state.importPreview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text(stringResource(R.string.settings_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.settings_import_merge_intro),
                        modifier = Modifier.testTag("importConfirmText"),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_import_feeding, preview.breastfeeding))
                    Text(stringResource(R.string.settings_import_sleep, preview.sleep))
                    Text(stringResource(R.string.settings_import_pumping, preview.pumping))
                    Text(stringResource(R.string.settings_import_milk_bags, preview.milkBags))
                    Text(stringResource(R.string.settings_import_bottle_feeds, preview.bottleFeeds))
                    Text(stringResource(R.string.settings_import_growth, preview.growth))
                    Text(stringResource(R.string.settings_import_milestones, preview.milestones))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_import_merge_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onConfirmImport, enabled = !working) { Text(stringResource(R.string.settings_import_confirm)) } },
            dismissButton = { TextButton(onClick = onCancelImport) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DataActionRow(
    label: String,
    value: String,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(actionLabel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerSheet(
    onDismiss: () -> Unit,
    onSave: (DateRange) -> Unit,
    onShare: (DateRange) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(7) }

    val options = listOf(
        7 to stringResource(R.string.range_last_7_days),
        14 to stringResource(R.string.range_last_2_weeks),
        30 to stringResource(R.string.range_last_month),
        -1 to stringResource(R.string.range_all_time),
    )

    fun dismissThen(action: (DateRange) -> Unit) {
        val days = selected
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            val range = if (days < 0) DateRange.allTime() else DateRange.lastDays(days.toLong())
            action(range)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(stringResource(R.string.settings_pdf_report_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_pdf_date_range),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            options.forEachIndexed { index, (days, label) ->
                RangeOption(
                    label = label,
                    selected = selected == days,
                    onClick = { selected = days },
                )
                if (index < options.lastIndex) {
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { dismissThen(onSave) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_save_pdf))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { dismissThen(onShare) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_share_pdf))
            }
        }
    }
}

@Composable
private fun RangeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val selectedState = stringResource(R.string.state_selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                stateDescription = if (selected) selectedState else ""
            }
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
