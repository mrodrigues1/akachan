package com.babytracker.ui.milestone

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.Milestone
import com.babytracker.ui.common.FieldAccent
import com.babytracker.ui.common.PickerAccentTheme
import com.babytracker.ui.theme.milestoneColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Add/edit bottom sheet for a free-form moment. [existing] is null in add mode.
 * Title and date are required; time, photo, and note are optional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneEditorSheet(
    existing: Milestone?,
    onDismiss: () -> Unit,
    onSave: (Milestone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val colors = milestoneColors()
    // The whole sheet lives on a Milestones (purple) screen, so every interactive control is
    // remapped off the global M3 primary (carnation pink) into the section accent: text-field
    // focus, the secondary buttons, and the date/time picker dialogs.
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        focusedLabelColor = colors.accent,
        cursorColor = colors.accent,
    )
    val buttonColors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent)
    val pickerAccent = FieldAccent(
        accent = colors.accent,
        onAccent = colors.onAccent,
        container = colors.container,
        onContainer = colors.onContainer,
    )

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var time by remember { mutableStateOf(existing?.time) }
    var photoUri by remember { mutableStateOf(existing?.photoUri) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isPersistingPhoto by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { picked ->
        if (picked != null) {
            isPersistingPhoto = true
            photoError = false
            scope.launch {
                // Keep the previous photo if the copy fails, rather than dropping to null,
                // and surface a calm note so the silent no-op never reads as success.
                val persisted = persistMilestonePhoto(context, picked)
                if (persisted != null) {
                    photoUri = persisted.toString()
                } else {
                    photoError = true
                }
                isPersistingPhoto = false
            }
        }
    }
    val thumbnail = rememberMilestoneBitmap(photoUri)
    val canSave = title.isNotBlank() && !isPersistingPhoto

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (existing == null) stringResource(R.string.milestone_editor_new) else stringResource(R.string.milestone_editor_edit),
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MILESTONE_TITLE_MAX) },
                label = { Text(stringResource(R.string.milestone_title_label)) },
                // The hint guides the resting state; near the cap it gives way to a counter
                // so the swap never adds or removes a line of height.
                supportingText = {
                    if (title.length >= MILESTONE_TITLE_MAX - COUNTER_REVEAL_AT) {
                        Text(stringResource(R.string.milestone_char_count, title.length, MILESTONE_TITLE_MAX))
                    } else {
                        Text(stringResource(R.string.milestone_title_hint))
                    }
                },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().testTag("milestone_title"),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    colors = buttonColors,
                    modifier = Modifier.testTag("milestone_date"),
                ) {
                    Text(date.format(dateFormatter))
                }
                val selectedTime = time
                if (selectedTime == null) {
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        colors = buttonColors,
                        modifier = Modifier.testTag("milestone_add_time"),
                    ) {
                        Text(stringResource(R.string.milestone_add_time))
                    }
                } else {
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        colors = buttonColors,
                        modifier = Modifier.testTag("milestone_time"),
                    ) {
                        Text(selectedTime.format(timeFormatter))
                    }
                    IconButton(
                        onClick = { time = null },
                        modifier = Modifier.testTag("milestone_clear_time"),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.milestone_remove_time))
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        photoError = false
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    colors = buttonColors,
                    modifier = Modifier.testTag("milestone_add_photo"),
                ) {
                    Text(if (photoUri == null) stringResource(R.string.milestone_add_photo) else stringResource(R.string.milestone_change_photo))
                }
                Box {
                    thumbnail?.let {
                        Image(
                            bitmap = it,
                            contentDescription = stringResource(R.string.milestone_photo_cd),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }

            // Photo status sits with its control, not below the note, so the feedback reads as
            // a response to the tap the parent just made.
            if (isPersistingPhoto) {
                Text(
                    text = stringResource(R.string.milestone_saving_photo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (photoError) {
                Text(
                    text = stringResource(R.string.milestone_photo_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("milestone_photo_error"),
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(MILESTONE_NOTE_MAX) },
                label = { Text(stringResource(R.string.milestone_note_label)) },
                // Bound the height so a long note scrolls inside the field instead of
                // pushing the Save button off-screen; the counter appears only near the cap.
                maxLines = MILESTONE_NOTE_MAX_LINES,
                supportingText = if (note.length >= MILESTONE_NOTE_MAX - COUNTER_REVEAL_AT) {
                    { Text(stringResource(R.string.milestone_char_count, note.length, MILESTONE_NOTE_MAX)) }
                } else {
                    null
                },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().testTag("milestone_note"),
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onSave(
                        (existing ?: Milestone(title = "", date = date)).copy(
                            title = title.trim(),
                            date = date,
                            time = time,
                            photoUri = photoUri,
                            note = note.takeIf { it.isNotBlank() },
                        ),
                    )
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
                modifier = Modifier.fillMaxWidth().testTag("milestone_save"),
            ) {
                Text(
                    if (existing == null) {
                        stringResource(R.string.milestone_save_moment)
                    } else {
                        stringResource(R.string.save)
                    },
                )
            }
        }
    }

    if (showDatePicker) {
        // A moment is something that already happened, and the timeline groups by month with
        // sticky headers, so a future date would pin an empty future month at the very top.
        val todayMillis = remember {
            LocalDate.now().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayMillis
            },
        )
        PickerAccentTheme(pickerAccent) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        }
                        showDatePicker = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    if (showTimePicker) {
        val initial = time ?: LocalTime.now()
        val timePickerState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
        )
        PickerAccentTheme(pickerAccent) {
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TimePicker(state = timePickerState)
                    }
                },
            )
        }
    }
}

// Caps keep a single moment's row bounded in the DB, the partner snapshot, and the timeline
// card (which clamps the title to one line and the note to two). The counter only reveals as
// the field nears its cap, keeping the resting editor calm.
private const val MILESTONE_TITLE_MAX = 80
private const val MILESTONE_NOTE_MAX = 500
private const val MILESTONE_NOTE_MAX_LINES = 6
private const val COUNTER_REVEAL_AT = 24
