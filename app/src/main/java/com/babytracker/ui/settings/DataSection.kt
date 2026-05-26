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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.babytracker.export.domain.model.DateRange
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.WarningAmber
import com.babytracker.ui.theme.WarningAmberDark
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark
import java.time.Instant

@Composable
fun DataSection(
    state: DataExportUiState,
    onExportPdf: (DateRange) -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRangeDialog by remember { mutableStateOf(false) }
    val working = state.status == DataExportUiState.Status.WORKING
    val isDark = LocalDarkTheme.current

    Column(modifier = modifier) {
        Text(
            text = "Data",
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

        if (state.importIncomplete) {
            val warnBg = if (isDark) WarningContainerAmberDark else WarningContainerAmber
            val warnText = if (isDark) OnWarningContainerAmberDark else OnWarningContainerAmber
            val warnIcon = if (isDark) WarningAmberDark else WarningAmber
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(warnBg, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("importIncompleteNotice"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = warnIcon,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Your last import may be incomplete. Re-import your backup to finish.",
                    style = MaterialTheme.typography.bodySmall,
                    color = warnText,
                )
            }
        }

        Text(
            text = "EXPORT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp),
        )
        DataActionRow(
            label = "PDF report",
            value = "Share your tracking data as a report",
            actionLabel = "Share",
            enabled = !working,
            onClick = { showRangeDialog = true },
        )
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        DataActionRow(
            label = "Backup (JSON)",
            value = "Full snapshot, restore anytime",
            actionLabel = "Save",
            enabled = !working,
            onClick = onExportJson,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        DataActionRow(
            label = "Spreadsheet (CSV)",
            value = "Open in Excel, Numbers, or Sheets",
            actionLabel = "Share",
            enabled = !working,
            onClick = onExportCsv,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val restoreBg = if (isDark) WarningContainerAmberDark else WarningContainerAmber
        val restoreFg = if (isDark) OnWarningContainerAmberDark else OnWarningContainerAmber

        Text(
            text = "IMPORT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 0.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !working, onClick = onImport)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(text = "Restore from backup", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Merge records from a JSON backup file",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onImport,
                enabled = !working,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = restoreBg,
                    contentColor = restoreFg,
                ),
            ) {
                Text("Restore")
            }
        }

        if (showRangeDialog) {
            RangePickerDialog(
                onDismiss = { showRangeDialog = false },
                onConfirm = { days ->
                    showRangeDialog = false
                    onExportPdf(DateRange.lastDays(days.toLong(), Instant.now()))
                },
            )
        }

        val preview = state.importPreview
        if (preview != null) {
            AlertDialog(
                onDismissRequest = onCancelImport,
                title = { Text("Import backup?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "This will merge into your current data:",
                            modifier = Modifier.testTag("importConfirmText"),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("• ${preview.breastfeeding} feeding sessions")
                        Text("• ${preview.sleep} sleep records")
                        Text("• ${preview.pumping} pumping sessions")
                        Text("• ${preview.milkBags} milk bags")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Existing records are kept; duplicates are skipped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = { TextButton(onClick = onConfirmImport, enabled = !working) { Text("Import") } },
                dismissButton = { TextButton(onClick = onCancelImport) { Text("Cancel") } },
            )
        }
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

@Composable
private fun RangePickerDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val options = listOf(7, 14, 30)
    var selected by remember { mutableIntStateOf(7) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF date range") },
        text = {
            Column {
                options.forEach { days ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == days, onClick = { selected = days })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = selected == days, onClick = { selected = days })
                        Text("Last $days days")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
