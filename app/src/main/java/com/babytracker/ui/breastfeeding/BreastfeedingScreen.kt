package com.babytracker.ui.breastfeeding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.ui.component.BreastfeedingIcon
import com.babytracker.ui.component.EditDeleteOverflowMenu
import com.babytracker.ui.component.SideSelector
import com.babytracker.ui.component.TimerDisplay
import com.babytracker.ui.component.labelRes
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime
import com.babytracker.util.formatTime12h
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreastfeedingScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    viewModel: BreastfeedingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeSession = uiState.activeSession
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var notificationPermissionGranted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    var showStopConfirmation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted = granted
    }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorDismissed()
    }

    fun onStartSessionWithPermission() {
        when (
            resolveStartSessionAction(
                hasSelectedSide = uiState.selectedSide != null,
                shouldRequestNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                notificationPermissionGranted = notificationPermissionGranted,
            )
        ) {
            StartSessionAction.NoOp -> return
            StartSessionAction.RequestPermissionAndStart -> {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                viewModel.onStartSession()
            }
            StartSessionAction.StartOnly -> viewModel.onStartSession()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.breastfeeding_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = stringResource(R.string.breastfeeding_action_history),
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.breastfeeding_action_settings),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (activeSession != null) {
                ActiveSessionBottomBar(
                    session = activeSession,
                    onPause = viewModel::onPauseSession,
                    onResume = viewModel::onResumeSession,
                    onStop = { showStopConfirmation = true },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            AnimatedContent(
                targetState = activeSession,
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
                label = "session_content",
            ) { session ->
                if (session != null) {
                    ActiveSessionContent(
                        session = session,
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                } else {
                    IdleSessionContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onStartSession = ::onStartSessionWithPermission,
                    )
                }
            }
        }
    }

    val editSheet = uiState.editSheet
    if (editSheet != null) {
        EditBreastfeedingSessionSheet(
            state = editSheet,
            onStartChanged = { viewModel.onEditTimeChanged(it, editSheet.editedEnd) },
            onEndChanged = { viewModel.onEditTimeChanged(editSheet.editedStart, it) },
            onDismiss = viewModel::onEditDismiss,
            onSave = viewModel::onEditSave,
        )
    }

    if (uiState.pendingDeleteSession != null) {
        BreastfeedingDeleteConfirmationDialog(
            onConfirm = viewModel::onConfirmDeleteSession,
            onDismiss = { viewModel.onPendingDeleteSessionChanged(null) },
        )
    }

    if (uiState.showManualEntrySheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::onDismissManualEntry,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AddFeedEntrySheetContent(
                uiState = uiState,
                onDateChanged = { viewModel.onManualEntryChanged(date = it) },
                onTimeChanged = { target, time ->
                    when (target) {
                        FeedTimePickerTarget.ENTRY_START -> viewModel.onManualEntryChanged(startTime = time)
                        FeedTimePickerTarget.ENTRY_END -> viewModel.onManualEntryChanged(endTime = time)
                    }
                },
                onSave = viewModel::onSaveManualEntry,
            )
        }
    }

    if (showStopConfirmation) {
        ModalBottomSheet(
            onDismissRequest = { showStopConfirmation = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.breastfeeding_stop_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.breastfeeding_stop_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        showStopConfirmation = false
                        viewModel.onStopSession()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(
                        stringResource(R.string.breastfeeding_stop_confirm),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showStopConfirmation = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        stringResource(R.string.breastfeeding_stop_dismiss),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionContent(
    session: BreastfeedingSession,
    uiState: BreastfeedingUiState,
    viewModel: BreastfeedingViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        FeedingSessionHero(
            session = session,
            uiState = uiState,
            onSwitchSide = viewModel::onSwitchSide,
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeedingSessionHero(
    session: BreastfeedingSession,
    uiState: BreastfeedingUiState,
    onSwitchSide: () -> Unit,
) {
    var sideDurations by remember(
        session.id,
        session.startTime,
        session.switchTime,
        session.pausedAt,
        session.pausedDurationMs,
    ) {
        mutableStateOf(
            session.sideDurationsUntil(session.pausedAt ?: Instant.now()),
        )
    }

    LaunchedEffect(
        session.id,
        session.startTime,
        session.switchTime,
        session.pausedAt,
        session.pausedDurationMs,
    ) {
        while (true) {
            val now = session.pausedAt ?: Instant.now()
            sideDurations = session.sideDurationsUntil(now)
            if (session.isPaused) break
            delay(1_000L)
        }
    }

    val frozenElapsedSeconds: Long? = if (session.isPaused) {
        val pausedAt = session.pausedAt!!
        (pausedAt.toEpochMilli() - session.startTime.toEpochMilli() - session.pausedDurationMs) / 1000L
    } else {
        null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ActiveStatusPill(isPaused = session.isPaused)

        Spacer(modifier = Modifier.height(20.dp))

        TimerDisplay(
            startTimeMillis = session.startTime.toEpochMilli() + session.pausedDurationMs,
            isRunning = !session.isPaused,
            frozenElapsedSeconds = frozenElapsedSeconds,
            maxDurationSeconds = if (uiState.maxTotalFeedMinutes > 0) {
                uiState.maxTotalFeedMinutes * 60
            } else {
                0
            },
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(BreastSide.LEFT, BreastSide.RIGHT).forEach { side ->
                val isCurrentSide = side == (uiState.currentSide ?: session.startingSide)
                val duration = if (side == session.startingSide) {
                    sideDurations.first
                } else {
                    sideDurations.second ?: Duration.ZERO
                }

                FeedingSideDurationCard(
                    side = side,
                    duration = duration,
                    isCurrentSide = isCurrentSide,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (session.switchTime == null && !session.isPaused) {
            val nextSide = if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
            OutlinedButton(
                onClick = onSwitchSide,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.breastfeeding_switch_to, stringResource(nextSide.labelRes())),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActiveStatusPill(isPaused: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = EaseOutQuart),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "status_dot_alpha",
    )
    val statusText = if (isPaused) {
        stringResource(R.string.breastfeeding_status_paused)
    } else {
        stringResource(R.string.breastfeeding_status_in_progress)
    }
    val statusCardColor by animateColorAsState(
        targetValue = if (isPaused) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "status_card_color",
    )
    val statusTextColor by animateColorAsState(
        targetValue = if (isPaused) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "status_text_color",
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = statusCardColor,
        contentColor = statusTextColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isPaused) {
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
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun FeedingSideDurationCard(
    side: BreastSide,
    duration: Duration,
    isCurrentSide: Boolean,
    modifier: Modifier = Modifier,
) {
    val sideLabel = stringResource(side.labelRes())
    val durationLabel = duration.formatDuration()
    val cardDescription = if (isCurrentSide) {
        stringResource(R.string.breastfeeding_side_duration_current_cd, sideLabel, durationLabel)
    } else {
        stringResource(R.string.breastfeeding_side_duration_cd, sideLabel, durationLabel)
    }
    val cardContainerColor by animateColorAsState(
        targetValue = if (isCurrentSide) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "side_card_color_${side.name}",
    )
    val cardTextColor by animateColorAsState(
        targetValue = if (isCurrentSide) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "side_text_color_${side.name}",
    )
    val cardElevation by animateDpAsState(
        targetValue = if (isCurrentSide) 6.dp else 0.dp,
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "side_card_elevation_${side.name}",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isCurrentSide) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "side_card_border_${side.name}",
    )

    Card(
        modifier = modifier
            .heightIn(min = 82.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = cardDescription
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 82.dp)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = sideLabel,
                style = MaterialTheme.typography.labelMedium,
                color = cardTextColor,
            )
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cardTextColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ActiveSessionBottomBar(
    session: BreastfeedingSession,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = if (session.isPaused) onResume else onPause,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                AnimatedContent(
                    targetState = session.isPaused,
                    transitionSpec = {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                    },
                    label = "pause_resume_content",
                ) { isPaused ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPaused) {
                                stringResource(R.string.breastfeeding_resume)
                            } else {
                                stringResource(R.string.breastfeeding_pause)
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
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
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.breastfeeding_stop_session),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun IdleSessionContent(
    uiState: BreastfeedingUiState,
    viewModel: BreastfeedingViewModel,
    onStartSession: () -> Unit,
) {
    val summary = uiState.lastFeedingSummary
    LaunchedEffect(summary, uiState.selectedSide) {
        if (summary is LastFeedingSummaryState.Populated && uiState.selectedSide == null) {
            viewModel.onSideSelected(summary.nextRecommendedSide)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.breastfeeding_start_heading),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(24.dp))

        SideSelector(
            selectedSide = uiState.selectedSide,
            onSideSelected = viewModel::onSideSelected,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onStartSession,
            enabled = uiState.selectedSide != null,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(stringResource(R.string.breastfeeding_start_session), style = MaterialTheme.typography.labelLarge)
        }

        if (summary is LastFeedingSummaryState.Populated) {
            Spacer(modifier = Modifier.height(24.dp))
            LastFeedingSummaryCard(
                summary = summary,
                prediction = uiState.nextFeedPrediction,
                onEditSession = viewModel::onEditSessionClick,
                onDeleteSession = viewModel::onPendingDeleteSessionChanged,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = viewModel::onAddEntryClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.breastfeeding_log_past_feed), style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun isNotificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun LikelyHungryRow(
    prediction: FeedPrediction,
    modifier: Modifier = Modifier,
) {
    val subtitle = remember(prediction) { PredictionCopy.forPrediction(prediction) }
    val timeLabel = remember(prediction) { prediction.predictedAt.formatTime() }
    val primaryText = subtitle.primaryText(timeLabel)
    val detailText = subtitle.detailText()
    val subtitleDescription = subtitle.contentDescriptionText(timeLabel)
    val isOverdue = prediction.isOverdue && abs(prediction.minutesUntil) >= 5
    val isDark = LocalDarkTheme.current

    val containerColor = when {
        isOverdue && isDark -> WarningContainerAmberDark
        isOverdue -> WarningContainerAmber
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        isOverdue && isDark -> OnWarningContainerAmberDark
        isOverdue -> OnWarningContainerAmber
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = subtitleDescription
            },
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detailText.isNotEmpty()) {
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LastFeedingSummaryCard(
    summary: LastFeedingSummaryState.Populated,
    prediction: FeedPrediction?,
    onEditSession: (BreastfeedingSession) -> Unit,
    onDeleteSession: (BreastfeedingSession) -> Unit,
) {
    val session = summary.lastSession
    val secondSide = if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.breastfeeding_last_feeding),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = summary.elapsedLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.breastfeeding_start_recommended,
                            stringResource(summary.nextRecommendedSide.labelRes()),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                EditDeleteOverflowMenu(
                    onEdit = { onEditSession(session) },
                    onDelete = { onDeleteSession(session) },
                )
            }

            if (prediction != null) {
                LikelyHungryRow(prediction = prediction)
            }

            LastFeedSideRow(
                title = stringResource(R.string.breastfeeding_breast_label, stringResource(session.startingSide.labelRes())),
                subtitle = stringResource(R.string.breastfeeding_first_side, session.startTime.formatTime12h()),
                duration = summary.firstSideDuration.formatDuration(),
                onClick = { onEditSession(session) },
            )

            if (summary.secondSideDuration != null) {
                LastFeedSideRow(
                    title = stringResource(R.string.breastfeeding_breast_label, stringResource(secondSide.labelRes())),
                    subtitle = stringResource(R.string.breastfeeding_second_side),
                    duration = summary.secondSideDuration.formatDuration(),
                    onClick = { onEditSession(session) },
                )
            }
        }
    }
}

@Composable
private fun LastFeedSideRow(
    title: String,
    subtitle: String,
    duration: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BreastfeedingIcon(modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = duration,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddFeedEntrySheetContent(
    uiState: BreastfeedingUiState,
    onDateChanged: (LocalDate) -> Unit,
    onTimeChanged: (FeedTimePickerTarget, LocalTime) -> Unit,
    onSave: (BreastSide) -> Unit,
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a").withLocale(java.util.Locale.getDefault()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d").withLocale(java.util.Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var activePicker by remember { mutableStateOf<FeedTimePickerTarget?>(null) }
    var selectedSide by remember { mutableStateOf(uiState.manualEntrySide) }
    val changeDateDescription = stringResource(R.string.change_date)
    val changeStartTimeDescription = stringResource(R.string.change_start_time)
    val changeEndTimeDescription = stringResource(R.string.change_end_time)

    activePicker?.let { target ->
        val initial = when (target) {
            FeedTimePickerTarget.ENTRY_START -> uiState.manualEntryStartTime
            FeedTimePickerTarget.ENTRY_END -> uiState.manualEntryEndTime
        }
        FeedTimePickerDialog(
            initialTime = initial,
            onConfirm = {
                onTimeChanged(target, it)
                activePicker = null
            },
            onDismiss = { activePicker = null },
        )
    }

    if (showDatePicker) {
        val initialMillis = uiState.manualEntryDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis ?: return@TextButton
                    val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onDateChanged(picked)
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.breastfeeding_add_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.label_date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedCard(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = changeDateDescription },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    text = uiState.manualEntryDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.breastfeeding_starting_side),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SideSelector(
                selectedSide = selectedSide,
                onSideSelected = { selectedSide = it },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_start_time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedCard(
                    onClick = { activePicker = FeedTimePickerTarget.ENTRY_START },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = changeStartTimeDescription },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = uiState.manualEntryStartTime.format(timeFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_end_time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedCard(
                    onClick = { activePicker = FeedTimePickerTarget.ENTRY_END },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = changeEndTimeDescription },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = uiState.manualEntryEndTime.format(timeFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }
            }
        }

        val canSave = uiState.manualEntryDurationPreview != null && uiState.manualEntryError == null

        when {
            uiState.manualEntryError != null -> {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.breastfeeding_manual_error, uiState.manualEntryError.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }
            }
            uiState.manualEntryDurationPreview == null -> {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.breastfeeding_manual_pick_times),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }
            }
            else -> {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(
                            R.string.breastfeeding_manual_duration,
                            uiState.manualEntryDurationPreview.formatDuration(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }
            }
        }

        Button(
            onClick = { onSave(selectedSide) },
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                stringResource(R.string.breastfeeding_manual_save, stringResource(selectedSide.labelRes())),
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        text = { TimePicker(state = timePickerState) },
    )
}

internal enum class StartSessionAction {
    NoOp,
    StartOnly,
    RequestPermissionAndStart,
}

internal fun resolveStartSessionAction(
    hasSelectedSide: Boolean,
    shouldRequestNotificationPermission: Boolean,
    notificationPermissionGranted: Boolean,
): StartSessionAction = when {
    !hasSelectedSide -> StartSessionAction.NoOp
    shouldRequestNotificationPermission && !notificationPermissionGranted -> StartSessionAction.RequestPermissionAndStart
    else -> StartSessionAction.StartOnly
}
