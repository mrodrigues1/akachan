package com.babytracker.ui.bottlefeed

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.ui.common.DateTimeFieldRow
import com.babytracker.util.formatVolume
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottleFeedSheet(
    state: BottleFeedUiState,
    onTypeChange: (FeedType) -> Unit,
    onVolumeChange: (String) -> Unit,
    onTimeChange: (Instant) -> Unit,
    onBagSelect: (Long?) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
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
                    if (!state.isEditing) {
                        R.string.bottle_feed_add_title
                    } else {
                        R.string.bottle_feed_edit_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            FeedTypeSelector(
                selected = state.feedType,
                onSelect = onTypeChange,
                enabled = !state.isSaving,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.volumeText,
                onValueChange = onVolumeChange,
                label = { Text(stringResource(R.string.bottle_feed_volume_label)) },
                singleLine = true,
                isError = state.validationError != null,
                supportingText = { state.validationError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            DateTimeFieldRow(
                label = stringResource(R.string.bottle_feed_time_label),
                timestamp = state.timestamp,
                onChange = onTimeChange,
                enabled = !state.isSaving,
            )

            if (state.feedType == FeedType.BREAST_MILK && state.activeBags.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                BagPicker(
                    bags = state.activeBags,
                    selectedBagId = state.selectedBagId,
                    unit = state.volumeUnit,
                    onSelect = onBagSelect,
                    enabled = !state.isSaving,
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.bottle_feed_notes_label)) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onConfirm,
                enabled = !state.isSaving,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.bottle_feed_save), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
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
private fun FeedTypeSelector(
    selected: FeedType,
    onSelect: (FeedType) -> Unit,
    enabled: Boolean,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        FeedType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = FeedType.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.errorContainer,
                    activeContentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                // Keep the chosen type legible even while the form is disabled mid-save.
                enabled = enabled || selected == type,
                label = {
                    Text(
                        text = stringResource(
                            when (type) {
                                FeedType.BREAST_MILK -> R.string.bottle_feed_type_breast_milk
                                FeedType.FORMULA -> R.string.bottle_feed_type_formula
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun BagPicker(
    bags: List<MilkBag>,
    selectedBagId: Long?,
    unit: VolumeUnit,
    onSelect: (Long?) -> Unit,
    enabled: Boolean,
) {
    Column {
        Text(
            text = stringResource(R.string.bottle_feed_bag_picker_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = BagPickerMaxHeight),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(BagPickerItemSpacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BAG_PICKER_LIST_TAG),
            ) {
                items(items = bags, key = { it.id }) { bag ->
                    val isSelected = bag.id == selectedBagId
                    BagRow(
                        label = bag.toPickerLabel(unit),
                        selected = isSelected,
                        // Tapping the selected bag again unlinks it.
                        onClick = { onSelect(if (isSelected) null else bag.id) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun BagRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(color = background, shape = MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val bagDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val BagPickerRowMinHeight = 56.dp
private val BagPickerItemSpacing = 8.dp
private val BagPickerMaxHeight = (BagPickerRowMinHeight * 4) + (BagPickerItemSpacing * 3)
internal const val BAG_PICKER_LIST_TAG = "BottleFeedBagPickerList"

private fun MilkBag.toPickerLabel(unit: VolumeUnit): String {
    val date = bagDateFormatter.format(collectionDate.atZone(ZoneId.systemDefault()).toLocalDate())
    return "${formatVolume(volumeMl, unit)} · $date"
}
