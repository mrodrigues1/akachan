package com.babytracker.ui.breastfeeding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.BreastSide
import com.babytracker.ui.component.HistoryCard
import com.babytracker.ui.component.SideSelector
import com.babytracker.ui.component.TimerDisplay
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreastfeedingScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: BreastfeedingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeSession = uiState.activeSession
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var notificationPermissionGranted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }

    // Permission launcher for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
        if (!granted) {
            // Permission denied - sessions will still work, just no notifications
        }
    }

    // Live clock for per-side elapsed breakdown
    val now by produceState(initialValue = Instant.now(), key1 = activeSession?.id) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            value = Instant.now()
        }
    }

    // Helper to start session with permission check
    fun onStartSessionWithPermission() {
        if (uiState.selectedSide == null) return
        
        // Request permission if on Android 13+ and not yet granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        
        viewModel.onStartSession()
    }

    Scaffold(
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
                // Status pill
                val statusText = if (activeSession.isPaused) "⏸ Session paused" else "● Session in progress"
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = if (activeSession.isPaused)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (activeSession.isPaused)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Compute frozen elapsed seconds when paused so TimerDisplay shows the
                // correct value on cold-load.
                val frozenElapsedSeconds: Long? = if (activeSession.isPaused) {
                    val pausedAt = activeSession.pausedAt!!
                    (pausedAt.toEpochMilli() - activeSession.startTime.toEpochMilli() - activeSession.pausedDurationMs) / 1000L
                } else {
                    null
                }

                // Ring timer — effective start accounts for accumulated paused time.
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

                // Per-side breakdown
                // Freeze per-side breakdown at pausedAt when session is paused.
                val effectiveNow: Instant = if (activeSession.isPaused) activeSession.pausedAt!! else now

                val firstSideDuration: Duration = activeSession.switchTime
                    ?.let { Duration.between(activeSession.startTime, it) }
                    ?: Duration.between(activeSession.startTime, effectiveNow)
                        .minus(Duration.ofMillis(activeSession.pausedDurationMs))

                val secondSideDuration: Duration = activeSession.switchTime
                    ?.let {
                        Duration.between(it, effectiveNow)
                            .minus(Duration.ofMillis(activeSession.pausedDurationMs))
                    }
                    ?: Duration.ZERO

                val currentSide: BreastSide = activeSession.switchTime?.let {
                    if (activeSession.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
                } ?: activeSession.startingSide

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(BreastSide.LEFT, BreastSide.RIGHT).forEach { side ->
                        val isCurrentSide = side == currentSide
                        val duration = if (side == activeSession.startingSide) {
                            firstSideDuration
                        } else {
                            secondSideDuration
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
                                    text = if (isCurrentSide) "● ${side.name}" else side.name,
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

                // Switch side button (only shown if not switched yet and not paused)
                if (activeSession.switchTime == null && !activeSession.isPaused) {
                    val nextSide = if (activeSession.startingSide == BreastSide.LEFT) "Right" else "Left"
                    OutlinedCard(
                        onClick = viewModel::onSwitchSide,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "⇄  Switch to $nextSide",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Pause / Resume button (above Stop)
                OutlinedCard(
                    onClick = if (activeSession.isPaused) viewModel::onResumeSession else viewModel::onPauseSession,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (activeSession.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (activeSession.isPaused) "Resume Session" else "Pause Session",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stop button
                Button(
                    onClick = viewModel::onStopSession,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text("Stop Session", style = MaterialTheme.typography.titleSmall)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp))

                    Text(
                        text = "Start a feeding session",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(24.dp))

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
                        Text("Start Session", style = MaterialTheme.typography.titleSmall)
                    }

                    val summary = uiState.lastFeedingSummary
                    if (summary is LastFeedingSummaryState.Populated) {
                        Spacer(modifier = Modifier.height(24.dp))
                        LastFeedingSummaryCard(summary = summary)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Check if notification permission is already granted.
 * On Android 12L and below, notifications don't require runtime permission.
 */
private fun isNotificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true // No runtime permission needed on older versions
    }
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun BreastSide.displayName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun LastFeedingSummaryCard(summary: LastFeedingSummaryState.Populated) {
    val session = summary.lastSession
    val secondSide = if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "LAST FEEDING",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = summary.elapsedLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "Start with: ${summary.nextRecommendedSide.displayName()} breast",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HistoryCard(
                title = "${session.startingSide.displayName()} breast",
                subtitle = "First side · ${session.startTime.formatTime12h()}",
                trailing = summary.firstSideDuration.formatDuration(),
                badgeEmoji = "🍼",
                badgeColor = if (session.startingSide == BreastSide.LEFT) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            )

            if (summary.secondSideDuration != null) {
                HistoryCard(
                    title = "${secondSide.displayName()} breast",
                    subtitle = "Second side",
                    trailing = summary.secondSideDuration.formatDuration(),
                    badgeEmoji = "🍼",
                    badgeColor = if (secondSide == BreastSide.LEFT) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                )
            }
        }
    }
}
