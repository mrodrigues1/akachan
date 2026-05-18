# Edit Past Breastfeeding Session — PR 3: UI Layer

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `HistoryCard` tappable, build `EditBreastfeedingSessionSheet` bottom sheet composable, and wire it into `BreastfeedingHistoryScreen`.

**Branch:** `feat/breastfeeding-edit-ui` (branch from `feat/breastfeeding-edit-viewmodel` or `main` after PR 2 merges)

**Depends on:** PR 2 (`2026-05-15-edit-breastfeeding-2-viewmodel.md`) — requires `EditSheetState`, `BreastfeedingViewModel.onEditSessionClick`, and the full handler set.

**Part of:** Edit Past Breastfeeding Session feature — see sibling plans:
- **PR 1:** `2026-05-15-edit-breastfeeding-1-domain.md`
- **PR 2:** `2026-05-15-edit-breastfeeding-2-viewmodel.md`

**Architecture:** `HistoryCard` gains an optional `onClick: (() -> Unit)?` — backward-compatible, null means not clickable. `EditBreastfeedingSessionSheet` is a stateless composable backed by M3 `ModalBottomSheet` + `DatePickerDialog` + `TimePickerDialog`. `BreastfeedingHistoryScreen` observes `uiState.editSheet` and renders the sheet when non-null.

**Tech Stack:** Kotlin 2.3.20, Jetpack Compose (BOM 2026.03.00), Material 3, Hilt 2.59.

---

## File Map

| Action | Path |
|--------|------|
| Modify | `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt` |
| Create | `app/src/main/java/com/babytracker/ui/breastfeeding/EditBreastfeedingSessionSheet.kt` |
| Modify | `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingHistoryScreen.kt` |

---

## Task 1: Make HistoryCard Clickable

Add an optional `onClick: (() -> Unit)?` parameter. When non-null, the card's row is clickable with `Role.Button` semantics. Existing callers stay source-compatible because the parameter defaults to `null`.

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt`

- [ ] **Step 1: Replace HistoryCard.kt**

Replace `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt`:

```kotlin
package com.babytracker.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.ui.theme.LocalDarkTheme

@Composable
fun HistoryCard(
    title: String,
    subtitle: String,
    trailing: String,
    badgeEmoji: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
    trailingColor: Color = MaterialTheme.colorScheme.primary,
    trailingIcon: ImageVector? = null,
    trailingIconDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val isDark = LocalDarkTheme.current
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
        .padding(horizontal = 14.dp, vertical = 12.dp)
        .semantics(mergeDescendants = true) {
            if (onClick != null) role = Role.Button
        }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = badgeColor,
                        shape = MaterialTheme.shapes.small,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = badgeEmoji, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = trailingColor,
            )

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = trailingIconDescription,
                    tint = trailingColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/component/HistoryCard.kt
git commit -m "feat(ui): add optional onClick parameter to HistoryCard"
```

---

## Task 2: EditBreastfeedingSessionSheet Composable

Bottom sheet with title, subtitle, STARTED/ENDED field rows, duration/validation line, Save button, Delete button, and inline delete-confirm strip. Date/time cells use `surfaceVariant` background + `medium` shape. Pickers are M3 `DatePickerDialog` and `TimePickerDialog`. Stateless w.r.t. business data — consumes `EditSheetState` and forwards events via callbacks.

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/breastfeeding/EditBreastfeedingSessionSheet.kt`

- [ ] **Step 1: Create the composable**

Create `app/src/main/java/com/babytracker/ui/breastfeeding/EditBreastfeedingSessionSheet.kt`:

```kotlin
package com.babytracker.ui.breastfeeding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.window.DialogProperties
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.usecase.breastfeeding.foldPause
import com.babytracker.util.formatTime12h
import com.babytracker.util.toRelativeLabel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBreastfeedingSessionSheet(
    state: EditSheetState,
    onStartChanged: (Instant) -> Unit,
    onEndChanged: (Instant?) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        EditSheetBody(
            state = state,
            onStartChanged = onStartChanged,
            onEndChanged = onEndChanged,
            onDismiss = onDismiss,
            onSave = onSave,
            onDeleteRequested = onDeleteRequested,
            onDeleteConfirmed = onDeleteConfirmed,
            onDeleteCancelled = onDeleteCancelled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheetBody(
    state: EditSheetState,
    onStartChanged: (Instant) -> Unit,
    onEndChanged: (Instant?) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
) {
    var datePickerFor by remember { mutableStateOf<EditField?>(null) }
    var timePickerFor by remember { mutableStateOf<EditField?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Edit feeding",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitleFor(state.original.startingSide, state.original.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("STARTED")
        Spacer(Modifier.height(8.dp))
        FieldRow(
            dateLabel = state.editedStart.toDateLabel(),
            timeLabel = state.editedStart.formatTime12h(),
            onDateClick = { datePickerFor = EditField.START },
            onTimeClick = { timePickerFor = EditField.START },
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("ENDED")
        Spacer(Modifier.height(8.dp))
        FieldRow(
            dateLabel = state.editedEnd?.toDateLabel() ?: "Set date",
            timeLabel = state.editedEnd?.formatTime12h() ?: "Set time",
            onDateClick = { datePickerFor = EditField.END },
            onTimeClick = { timePickerFor = EditField.END },
            placeholder = state.editedEnd == null,
        )

        Spacer(Modifier.height(12.dp))
        DurationOrError(state = state)

        Spacer(Modifier.height(24.dp))
        if (state.deleteConfirm) {
            DeleteConfirmRow(
                isDeleting = state.isDeleting,
                onCancel = onDeleteCancelled,
                onConfirm = onDeleteConfirmed,
            )
        } else {
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save changes", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDeleteRequested,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    datePickerFor?.let { field ->
        val original = if (field == EditField.START) state.editedStart else state.editedEnd ?: state.editedStart
        EditDatePicker(
            initial = original,
            onConfirm = { newDate ->
                val combined = original.withDate(newDate)
                if (field == EditField.START) onStartChanged(combined) else onEndChanged(combined)
                datePickerFor = null
            },
            onDismiss = { datePickerFor = null },
        )
    }

    timePickerFor?.let { field ->
        val original = if (field == EditField.START) state.editedStart else state.editedEnd ?: state.editedStart
        EditTimePicker(
            initial = original,
            onConfirm = { newTime ->
                val combined = original.withTime(newTime)
                if (field == EditField.START) onStartChanged(combined) else onEndChanged(combined)
                timePickerFor = null
            },
            onDismiss = { timePickerFor = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FieldRow(
    dateLabel: String,
    timeLabel: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    placeholder: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FieldCell(
            label = dateLabel,
            onClick = onDateClick,
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
        )
        FieldCell(
            label = timeLabel,
            onClick = onTimeClick,
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
        )
    }
}

@Composable
private fun FieldCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (placeholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DurationOrError(state: EditSheetState) {
    val error = state.validationError
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    val end = state.editedEnd
    if (end == null) {
        Text(
            text = "Session in progress",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val (projectedPausedMs, _) = foldPause(state.original, state.editedStart, end)
    val active = Duration.between(state.editedStart, end)
        .minusMillis(projectedPausedMs)
        .coerceAtLeast(Duration.ZERO)
    Text(
        text = "Duration: ${formatDurationShort(active)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DeleteConfirmRow(
    isDeleting: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Delete this session?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "It can't be undone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                enabled = !isDeleting,
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Text("Delete", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDatePicker(
    initial: Instant,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // M3 DatePickerState exposes selectedDateMillis as UTC midnight for the chosen date.
    // Initialize with the same convention so the picker opens on the correct day regardless
    // of the device's local UTC offset (a positive-offset zone otherwise shifts the picker back a day).
    val zone = ZoneId.systemDefault()
    val localDate = initial.atZone(zone).toLocalDate()
    val initialEpochMillis = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialEpochMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                onConfirm(picked)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTimePicker(
    initial: Instant,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val local = initial.atZone(ZoneId.systemDefault()).toLocalTime()
    val state = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = false)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
        shape = MaterialTheme.shapes.large,
    )
}

private enum class EditField { START, END }

private fun subtitleFor(side: BreastSide, started: Instant): String {
    val sideLabel = if (side == BreastSide.LEFT) "Left side" else "Right side"
    val date = started.atZone(ZoneId.systemDefault()).toLocalDate()
    return "$sideLabel · ${date.toRelativeLabel()}"
}

private fun Instant.toDateLabel(): String {
    val date = atZone(ZoneId.systemDefault()).toLocalDate()
    val relative = date.toRelativeLabel()
    return if (relative == "Today" || relative == "Yesterday") relative
    else DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(date)
}

private fun Instant.withDate(date: LocalDate): Instant {
    val zone = ZoneId.systemDefault()
    val time = atZone(zone).toLocalTime()
    return LocalDateTime.of(date, time).atZone(zone).toInstant()
}

private fun Instant.withTime(time: LocalTime): Instant {
    val zone = ZoneId.systemDefault()
    val existingDate = atZone(zone).toLocalDate()
    return LocalDateTime.of(existingDate, time).atZone(zone).toInstant()
}

private fun formatDurationShort(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = (duration.toMinutes() % 60).toInt()
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "less than 1m"
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run ktlintFormat then detekt**

```
./gradlew ktlintFormat
./gradlew detekt
```
Expected: both succeed; fix violations by adjusting code (never `@Suppress`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/breastfeeding/EditBreastfeedingSessionSheet.kt
git commit -m "feat(breastfeeding): add EditBreastfeedingSessionSheet composable"
```

---

## Task 3: Wire Tap-to-Edit into BreastfeedingHistoryScreen

Hook `onClick` on each history row to `viewModel.onEditSessionClick`, observe `editSheet` from `uiState`, and render `EditBreastfeedingSessionSheet` when non-null.

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingHistoryScreen.kt`

- [ ] **Step 1: Replace the screen contents**

Replace `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingHistoryScreen.kt`:

```kotlin
package com.babytracker.ui.breastfeeding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.BreastSide
import com.babytracker.ui.component.HistoryCard
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import com.babytracker.util.groupByLocalDate
import com.babytracker.util.toRelativeLabel
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreastfeedingHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BreastfeedingViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val grouped = history.groupByLocalDate { it.startTime }
    val sortedGroups = remember(grouped) {
        grouped.entries
            .sortedByDescending { it.key }
            .map { (date, sessions) ->
                val totalDuration = sessions
                    .mapNotNull { it.activeDuration }
                    .fold(Duration.ZERO) { acc, d -> acc + d }
                Triple(date, sessions, totalDuration)
            }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Feeding History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🍼", style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No sessions yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Sessions you track will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp
                )
            ) {
                sortedGroups.forEach { (date, sessions, totalDuration) ->
                    val totalLabel = if (totalDuration.isZero) "" else " · ${totalDuration.formatDuration()} total"

                    stickyHeader(key = date.toString()) {
                        Text(
                            text = "${date.toRelativeLabel()} · ${sessions.size} sessions$totalLabel".uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }

                    items(sessions, key = { it.id }) { session ->
                        val isLeft = session.startingSide == BreastSide.LEFT
                        HistoryCard(
                            title = if (isLeft) "Left side" else "Right side",
                            subtitle = session.startTime.formatTime12h(),
                            trailing = session.activeDuration?.formatDuration() ?: "In progress",
                            badgeEmoji = "🍼",
                            badgeColor = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { viewModel.onEditSessionClick(session) },
                        )
                    }
                }
            }
        }
    }

    val editSheet = uiState.editSheet
    if (editSheet != null) {
        EditBreastfeedingSessionSheet(
            state = editSheet,
            onStartChanged = viewModel::onEditStartChanged,
            onEndChanged = viewModel::onEditEndChanged,
            onDismiss = viewModel::onEditDismiss,
            onSave = viewModel::onEditSave,
            onDeleteRequested = viewModel::onDeleteRequested,
            onDeleteConfirmed = viewModel::onDeleteConfirmed,
            onDeleteCancelled = viewModel::onDeleteCancelled,
        )
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 4: ktlintFormat and detekt**

```
./gradlew ktlintFormat
./gradlew detekt
```
Expected: both succeed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingHistoryScreen.kt
git commit -m "feat(breastfeeding): wire tap-to-edit into BreastfeedingHistoryScreen"
```

---

## Final: Open PR

```bash
git push -u origin feat/breastfeeding-edit-ui
```

Open PR targeting `main` (or `feat/breastfeeding-edit-viewmodel` if stacking). Title: `feat(breastfeeding): add edit/delete session UI (bottom sheet + history screen wiring)`.
