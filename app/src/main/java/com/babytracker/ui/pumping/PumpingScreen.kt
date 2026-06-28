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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.ui.component.EditDatePicker
import com.babytracker.ui.component.EditDateTimeRow
import com.babytracker.ui.component.EditTimePicker
import com.babytracker.ui.component.TimerDisplay
import com.babytracker.ui.component.toEditDateLabel
import com.babytracker.ui.component.withEditedDate
import com.babytracker.ui.component.withEditedTime
import com.babytracker.util.formatTime12h

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
                title = { Text(stringResource(R.string.pumping_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToInventory) {
                        Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.pumping_cd_stash))
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.pumping_cd_history))
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
                            PumpingMode.TIMER -> stringResource(R.string.pumping_mode_timer)
                            PumpingMode.MANUAL -> stringResource(R.string.pumping_mode_manual)
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
            text = stringResource(R.string.pumping_start_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.pumping_start_subtitle),
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
                Text(stringResource(R.string.pumping_start_button), style = MaterialTheme.typography.labelLarge)
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
                text = stringResource(session.breast.labelRes()),
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
                        text = if (isPaused) stringResource(R.string.breastfeeding_resume) else stringResource(R.string.breastfeeding_pause),
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
            Text(text = stringResource(R.string.breastfeeding_stop_session), style = MaterialTheme.typography.labelLarge)
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
            val label = stringResource(breast.labelRes())

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
            text = stringResource(R.string.breastfeeding_edit_started),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        EditDateTimeRow(
            dateLabel = manual.startTime.toEditDateLabel(),
            timeLabel = manual.startTime.formatTime12h(),
            onDateClick = { datePickerFor = ManualField.START },
            onTimeClick = { timePickerFor = ManualField.START },
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.breastfeeding_edit_ended),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        EditDateTimeRow(
            dateLabel = manual.endTime.toEditDateLabel(),
            timeLabel = manual.endTime.formatTime12h(),
            onDateClick = { datePickerFor = ManualField.END },
            onTimeClick = { timePickerFor = ManualField.END },
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.pumping_breast_caps),
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
            label = { Text(stringResource(R.string.pumping_volume_label)) },
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
            label = { Text(stringResource(R.string.growth_notes_label)) },
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
            Text(stringResource(R.string.pumping_save_session), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(32.dp))
    }

    datePickerFor?.let { field ->
        val original = if (field == ManualField.START) manual.startTime else manual.endTime
        EditDatePicker(
            initial = original,
            onConfirm = { newDate ->
                val combined = original.withEditedDate(newDate)
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
        EditTimePicker(
            initial = original,
            onConfirm = { newTime ->
                val combined = original.withEditedTime(newTime)
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
                        text = stringResource(R.string.pumping_end_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.pumping_end_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = volumeInput,
                onValueChange = { volumeInput = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.pumping_volume_label)) },
                placeholder = { Text(stringResource(R.string.pumping_optional)) },
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
                Text(stringResource(R.string.breastfeeding_stop_session), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.breastfeeding_stop_dismiss), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private enum class ManualField { START, END }
