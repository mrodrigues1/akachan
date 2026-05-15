package com.babytracker.ui.breastfeeding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.displayName
import com.babytracker.ui.component.HistoryCard
import com.babytracker.ui.component.SideSelector
import com.babytracker.ui.component.TimerDisplay
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreastfeedingScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BreastfeedingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeSession = uiState.activeSession
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var notificationPermissionGranted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    var showStopConfirmation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
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
                notificationPermissionGranted = notificationPermissionGranted
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
                title = { Text("Breastfeeding") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToHistory) {
                        Text("History", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (activeSession != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    val statusText = if (activeSession.isPaused) "Session paused" else "Session in progress"
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeSession.isPaused)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val statusColor = if (activeSession.isPaused)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer

                            if (activeSession.isPaused) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = null,
                                    tint = statusColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Freeze elapsed seconds at pausedAt so TimerDisplay shows the correct value on cold-load.
                    val frozenElapsedSeconds: Long? = if (activeSession.isPaused) {
                        val pausedAt = activeSession.pausedAt!!
                        (pausedAt.toEpochMilli() - activeSession.startTime.toEpochMilli() - activeSession.pausedDurationMs) / 1000L
                    } else {
                        null
                    }

                    // Effective start accounts for accumulated paused time.
                    TimerDisplay(
                        startTimeMillis = activeSession.startTime.toEpochMilli() + activeSession.pausedDurationMs,
                        isRunning = !activeSession.isPaused,
                        frozenElapsedSeconds = frozenElapsedSeconds,
                        maxDurationSeconds = if (uiState.maxTotalFeedMinutes > 0) {
                            uiState.maxTotalFeedMinutes * 60
                        } else {
                            0
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    var sideDurations by remember(
                        activeSession.id,
                        activeSession.startTime,
                        activeSession.switchTime,
                        activeSession.pausedAt,
                        activeSession.pausedDurationMs
                    ) {
                        mutableStateOf(
                            activeSession.sideDurationsUntil(
                                activeSession.pausedAt ?: Instant.now()
                            )
                        )
                    }

                    LaunchedEffect(
                        activeSession.id,
                        activeSession.startTime,
                        activeSession.switchTime,
                        activeSession.pausedAt,
                        activeSession.pausedDurationMs
                    ) {
                        while (true) {
                            val now = activeSession.pausedAt ?: Instant.now()
                            sideDurations = activeSession.sideDurationsUntil(now)
                            if (activeSession.isPaused) break
                            delay(1_000L)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(BreastSide.LEFT, BreastSide.RIGHT).forEach { side ->
                            val isCurrentSide = side == (uiState.currentSide ?: activeSession.startingSide)
                            val duration = if (side == activeSession.startingSide) {
                                sideDurations.first
                            } else {
                                sideDurations.second ?: Duration.ZERO
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrentSide) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = side.displayName(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isCurrentSide) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Text(
                                        text = duration.formatDuration(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentSide) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (activeSession.switchTime == null && !activeSession.isPaused) {
                        val nextSide = if (activeSession.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
                        OutlinedButton(
                            onClick = viewModel::onSwitchSide,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Switch to ${nextSide.displayName()}",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = if (activeSession.isPaused) viewModel::onResumeSession else viewModel::onPauseSession,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(
                            imageVector = if (activeSession.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (activeSession.isPaused) "Resume Session" else "Pause Session",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showStopConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stop Session",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else {
                val summary = uiState.lastFeedingSummary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Start a feeding session",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (summary is LastFeedingSummaryState.Populated) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Start ${summary.nextRecommendedSide.displayName()} breast first",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    SideSelector(
                        selectedSide = uiState.selectedSide,
                        onSideSelected = viewModel::onSideSelected
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { onStartSessionWithPermission() },
                        enabled = uiState.selectedSide != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text("Start Session", style = MaterialTheme.typography.labelLarge)
                    }

                    if (summary is LastFeedingSummaryState.Populated) {
                        Spacer(modifier = Modifier.height(24.dp))
                        LastFeedingSummaryCard(
                            summary = summary,
                            onEditSession = viewModel::onEditSessionClick,
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "End this session?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Session will be saved to history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
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
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("End session", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showStopConfirmation = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text("Keep going", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun isNotificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun LastFeedingSummaryCard(
    summary: LastFeedingSummaryState.Populated,
    onEditSession: (BreastfeedingSession) -> Unit,
) {
    val session = summary.lastSession
    val secondSide = if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "LAST FEEDING",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = summary.elapsedLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        HistoryCard(
            title = "${session.startingSide.displayName()} breast",
            subtitle = "First side · ${session.startTime.formatTime12h()}",
            trailing = summary.firstSideDuration.formatDuration(),
            badgeEmoji = "🍼",
            badgeColor = MaterialTheme.colorScheme.primaryContainer,
            onClick = { onEditSession(session) },
            trailingIcon = Icons.Default.Edit,
            trailingIconDescription = "Edit session",
        )

        if (summary.secondSideDuration != null) {
            HistoryCard(
                title = "${secondSide.displayName()} breast",
                subtitle = "Second side",
                trailing = summary.secondSideDuration.formatDuration(),
                badgeEmoji = "🍼",
                badgeColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = { onEditSession(session) },
                trailingIcon = Icons.Default.Edit,
                trailingIconDescription = "Edit session",
            )
        }
    }
}

internal enum class StartSessionAction {
    NoOp,
    StartOnly,
    RequestPermissionAndStart,
}

internal fun resolveStartSessionAction(
    hasSelectedSide: Boolean,
    shouldRequestNotificationPermission: Boolean,
    notificationPermissionGranted: Boolean
): StartSessionAction = when {
    !hasSelectedSide -> StartSessionAction.NoOp
    shouldRequestNotificationPermission && !notificationPermissionGranted -> StartSessionAction.RequestPermissionAndStart
    else -> StartSessionAction.StartOnly
}
