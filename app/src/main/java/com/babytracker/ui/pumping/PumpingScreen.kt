package com.babytracker.ui.pumping

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.displayName
import com.babytracker.ui.component.TimerDisplay
import com.babytracker.util.formatTime12h
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
fun PumpingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToInventory: () -> Unit = {},
    viewModel: PumpingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStopSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorDismissed()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Pumping") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToInventory) {
                        Icon(Icons.Filled.Archive, contentDescription = "Milk stash")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Filled.History, contentDescription = "History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            ModeSegmentedControl(
                mode = state.mode,
                onModeChange = viewModel::onModeChange,
                enabled = state.activeSession == null,
            )
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = state.mode,
                transitionSpec = {
                    (fadeIn(tween(250)) + slideInVertically(tween(300)) { 30 }) togetherWith
                        (fadeOut(tween(200)) + slideOutVertically(tween(250)) { -30 })
                },
                modifier = Modifier.fillMaxWidth(),
                label = "mode_content",
            ) { mode ->
                when (mode) {
                    PumpingMode.MANUAL -> ManualModeContent(
                        state = state,
                        onFieldChange = viewModel::onManualFieldChange,
                        onSave = viewModel::onManualSave,
                    )
                    PumpingMode.TIMER -> TimerModeContent(
                        state = state,
                        onBreastSelected = viewModel::onBreastSelected,
                        onStart = viewModel::onStartTimer,
                        onPause = viewModel::onPause,
                        onResume = viewModel::onResume,
                        onStop = { showStopSheet = true },
                    )
                }
            }
        }
    }

    if (showStopSheet) {
        StopVolumeSheet(
            onConfirm = { volume ->
                showStopSheet = false
                viewModel.onStopTimer(volume)
            },
            onDismiss = { showStopSheet = false },
        )
    }

    state.bagPrompt?.let { prompt ->
        AddBagPromptSheet(
            state = prompt,
            onFieldChange = viewModel::onBagPromptFieldChange,
            onConfirm = viewModel::onBagPromptConfirm,
            onDismiss = viewModel::onBagPromptSkip,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSegmentedControl(
    mode: PumpingMode,
    onModeChange: (PumpingMode) -> Unit,
    enabled: Boolean,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        PumpingMode.entries.forEachIndexed { index, pumpingMode ->
            SegmentedButton(
                selected = mode == pumpingMode,
                onClick = { onModeChange(pumpingMode) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = PumpingMode.entries.size,
                ),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveBorderColor = MaterialTheme.colorScheme.primary,
                ),
                enabled = enabled || mode == pumpingMode,
                label = {
                    Text(
                        text = when (pumpingMode) {
                            PumpingMode.TIMER -> "Timer"
                            PumpingMode.MANUAL -> "Manual"
                        },
                    )
                },
            )
        }
    }
}

@Composable
internal fun TimerModeContent(
    state: PumpingUiState,
    onBreastSelected: (PumpingBreast) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    AnimatedContent(
        targetState = state.activeSession,
        contentKey = { it != null },
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(tween(300)) + slideInVertically(tween(350)) { 50 }) togetherWith
                    (fadeOut(tween(200)) + slideOutVertically(tween(250)) { -50 })
            } else {
                (fadeIn(tween(300)) + slideInVertically(tween(350)) { -50 }) togetherWith
                    (fadeOut(tween(200)) + slideOutVertically(tween(250)) { 50 })
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = "timer_session_content",
    ) { session ->
        if (session != null) {
            ActiveTimerContent(
                session = session,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
            )
        } else {
            IdleTimerContent(
                selectedBreast = state.selectedBreast,
                isStarting = state.isStarting,
                onBreastSelected = onBreastSelected,
                onStart = onStart,
            )
        }
    }
}

@Composable
internal fun IdleTimerContent(
    selectedBreast: PumpingBreast,
    onBreastSelected: (PumpingBreast) -> Unit,
    onStart: () -> Unit,
    isStarting: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Start a session",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Select which side you're pumping.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        PumpingBreastSelector(
            selectedBreast = selectedBreast,
            onBreastSelected = onBreastSelected,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStart,
            enabled = !isStarting,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (isStarting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Start Pumping", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
internal fun ActiveTimerContent(
    session: com.babytracker.domain.model.PumpingSession,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "status_dot_alpha",
    )

    val statusCardColor by animateColorAsState(
        targetValue = if (session.isPaused) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 300),
        label = "status_card_color",
    )
    val statusTextColor by animateColorAsState(
        targetValue = if (session.isPaused) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 300),
        label = "status_text_color",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))

        val statusText = if (session.isPaused) "Session paused" else "Session in progress"
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = statusCardColor),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (session.isPaused) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = statusTextColor,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = statusTextColor.copy(alpha = dotAlpha),
                                shape = CircleShape,
                            ),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusTextColor,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        val frozenElapsedSeconds: Long? = if (session.isPaused) {
            val pausedAt = session.pausedAt!!
            (pausedAt.toEpochMilli() - session.startTime.toEpochMilli() - session.pausedDurationMs) / 1000L
        } else {
            null
        }

        TimerDisplay(
            startTimeMillis = session.startTime.toEpochMilli() + session.pausedDurationMs,
            isRunning = !session.isPaused,
            frozenElapsedSeconds = frozenElapsedSeconds,
            ringColor = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )

        Spacer(Modifier.height(16.dp))

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Text(
                text = session.breast.displayName(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = if (session.isPaused) onResume else onPause,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            AnimatedContent(
                targetState = session.isPaused,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                label = "pause_resume_content",
            ) { isPaused ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isPaused) "Resume Session" else "Pause Session",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = "Stop Session", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PumpingBreastSelector(
    selectedBreast: PumpingBreast,
    onBreastSelected: (PumpingBreast) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PumpingBreast.entries.forEach { breast ->
            val isSelected = selectedBreast == breast
            val label = breast.displayName()

            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                animationSpec = tween(durationMillis = 220),
                label = "breast_container_${breast.name}",
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) {
                    androidx.compose.ui.graphics.Color.Transparent
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                animationSpec = tween(durationMillis = 220),
                label = "breast_border_${breast.name}",
            )
            val elevation by animateDpAsState(
                targetValue = if (isSelected) 4.dp else 0.dp,
                animationSpec = tween(durationMillis = 220),
                label = "breast_elevation_${breast.name}",
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                animationSpec = tween(durationMillis = 220),
                label = "breast_text_${breast.name}",
            )

            Card(
                onClick = { onBreastSelected(breast) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .semantics {
                        role = Role.RadioButton
                        selected = isSelected
                        contentDescription = "$label${if (isSelected) ", selected" else ""}"
                    },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                border = BorderStroke(width = 2.dp, color = borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ManualModeContent(
    state: PumpingUiState,
    onFieldChange: ((ManualEntryState) -> ManualEntryState) -> Unit,
    onSave: () -> Unit,
) {
    val manual = state.manual ?: return

    var datePickerFor by remember { mutableStateOf<ManualField?>(null) }
    var timePickerFor by remember { mutableStateOf<ManualField?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "STARTED",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        ManualFieldRow(
            dateLabel = manual.startTime.toManualDateLabel(),
            timeLabel = manual.startTime.formatTime12h(),
            onDateClick = { datePickerFor = ManualField.START },
            onTimeClick = { timePickerFor = ManualField.START },
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "ENDED",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        ManualFieldRow(
            dateLabel = manual.endTime.toManualDateLabel(),
            timeLabel = manual.endTime.formatTime12h(),
            onDateClick = { datePickerFor = ManualField.END },
            onTimeClick = { timePickerFor = ManualField.END },
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "BREAST",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        PumpingBreastSelector(
            selectedBreast = manual.breast,
            onBreastSelected = { breast -> onFieldChange { it.copy(breast = breast) } },
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = manual.volumeMl,
            onValueChange = { value -> onFieldChange { it.copy(volumeMl = value, validationError = null) } },
            label = { Text("Volume pumped (mL)") },
            singleLine = true,
            isError = manual.validationError != null,
            supportingText = { manual.validationError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = manual.notes,
            onValueChange = { value -> onFieldChange { it.copy(notes = value) } },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onSave,
            enabled = !manual.isSaving,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (manual.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("Save Session", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(32.dp))
    }

    datePickerFor?.let { field ->
        val original = if (field == ManualField.START) manual.startTime else manual.endTime
        ManualDatePicker(
            initial = original,
            onConfirm = { newDate ->
                val combined = original.withManualDate(newDate)
                if (field == ManualField.START) {
                    onFieldChange { it.copy(startTime = combined) }
                } else {
                    onFieldChange { it.copy(endTime = combined) }
                }
                datePickerFor = null
            },
            onDismiss = { datePickerFor = null },
        )
    }

    timePickerFor?.let { field ->
        val original = if (field == ManualField.START) manual.startTime else manual.endTime
        ManualTimePicker(
            initial = original,
            onConfirm = { newTime ->
                val combined = original.withManualTime(newTime)
                if (field == ManualField.START) {
                    onFieldChange { it.copy(startTime = combined) }
                } else {
                    onFieldChange { it.copy(endTime = combined) }
                }
                timePickerFor = null
            },
            onDismiss = { timePickerFor = null },
        )
    }
}

@Composable
private fun ManualFieldRow(
    dateLabel: String,
    timeLabel: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ManualFieldCell(
            label = dateLabel,
            onClick = onDateClick,
            modifier = Modifier.weight(1f),
        )
        ManualFieldCell(
            label = timeLabel,
            onClick = onTimeClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ManualFieldCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(56.dp)
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
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopVolumeSheet(
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var volumeInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "End session",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Optionally record how much you pumped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = volumeInput,
                onValueChange = { volumeInput = it.filter { c -> c.isDigit() } },
                label = { Text("Volume pumped (mL)") },
                placeholder = { Text("Optional") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val volume = volumeInput.toIntOrNull()?.takeIf { it > 0 }
                    onConfirm(volume)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Stop Session", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Keep going", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualDatePicker(
    initial: Instant,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTimePicker(
    initial: Instant,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val local = initial.atZone(ZoneId.systemDefault()).toLocalTime()
    val state = rememberTimePickerState(
        initialHour = local.hour,
        initialMinute = local.minute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
        shape = MaterialTheme.shapes.large,
    )
}

private enum class ManualField { START, END }

private fun Instant.toManualDateLabel(): String {
    val zone = ZoneId.systemDefault()
    val date = atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(date)
    }
}

private fun Instant.withManualDate(date: LocalDate): Instant {
    val zone = ZoneId.systemDefault()
    val time = atZone(zone).toLocalTime()
    return LocalDateTime.of(date, time).atZone(zone).toInstant()
}

private fun Instant.withManualTime(time: LocalTime): Instant {
    val zone = ZoneId.systemDefault()
    val date = atZone(zone).toLocalDate()
    return LocalDateTime.of(date, time).atZone(zone).toInstant()
}
