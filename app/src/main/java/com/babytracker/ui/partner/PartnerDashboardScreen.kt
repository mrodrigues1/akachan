package com.babytracker.ui.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.AllergyType
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.ui.component.HistoryCard
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.WarningAmber
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.util.formatDuration
import com.babytracker.util.formatElapsedAgo
import java.time.Duration
import java.time.Instant

private const val STALE_SYNC_THRESHOLD_MINUTES = 30L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDashboardScreen(
    modifier: Modifier = Modifier,
    onDisconnected: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: PartnerDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isDisconnected) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Partner access ended") },
            text = { Text("Ask the primary parent for a new sharing code to reconnect.") },
            confirmButton = {
                TextButton(onClick = onDisconnected) { Text("Go to settings") }
            },
        )
    }

    val snapshot = uiState.snapshot
    val babyName = snapshot?.baby?.name?.takeIf { it.isNotEmpty() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = babyName ?: "Baby Tracker",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        BabyAgeSubtitle(snapshot = snapshot, babyName = babyName)
                    }
                },
                actions = {
                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Check for shared updates")
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading && snapshot == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                snapshot != null -> {
                    DashboardContent(
                        snapshot = snapshot,
                        error = uiState.error,
                        onClearError = viewModel::clearError,
                        lastRefreshAt = uiState.lastRefreshAt,
                    )
                }
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error!!,
                        onRetry = viewModel::refresh,
                    )
                }
                else -> {
                    EmptyState(
                        babyName = babyName,
                        onRefresh = viewModel::refresh,
                    )
                }
            }
        }
    }
}

@Composable
private fun BabyAgeSubtitle(
    snapshot: ShareSnapshot?,
    babyName: String?,
) {
    if (babyName != null && snapshot != null) {
        val ageWeeks = remember(snapshot.baby.birthDateMs) {
            Duration.between(
                Instant.ofEpochMilli(snapshot.baby.birthDateMs),
                Instant.now(),
            ).toDays() / 7
        }
        Text(
            text = "${ageWeeks}w old, read-only partner view",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            text = "Read-only partner view",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardContent(
    snapshot: ShareSnapshot,
    error: String?,
    onClearError: () -> Unit,
    lastRefreshAt: Long,
) {
    val activeSession = snapshot.sessions.firstOrNull { it.endTime == null }
    val completedSessions = snapshot.sessions.filter { it.endTime != null }.take(3)
    val lastSleep = snapshot.sleepRecords.firstOrNull()
    val hasSharedRecords = snapshot.sessions.isNotEmpty() || snapshot.sleepRecords.isNotEmpty()
    val lastSharedText = remember(snapshot.lastSyncAt, lastRefreshAt) {
        Duration.between(snapshot.lastSyncAt, Instant.now()).formatElapsedAgo()
    }
    val isShareStale = remember(snapshot.lastSyncAt, lastRefreshAt) {
        Duration.between(snapshot.lastSyncAt, Instant.now()).toMinutes() >= STALE_SYNC_THRESHOLD_MINUTES
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        PartnerStatusPanel(
            activeSession = activeSession,
            lastSharedText = lastSharedText,
            isShareStale = isShareStale,
            error = error,
            onClearError = onClearError,
            lastSyncAt = snapshot.lastSyncAt,
            lastRefreshAt = lastRefreshAt,
        )

        if (!hasSharedRecords) {
            Spacer(modifier = Modifier.height(18.dp))
            SharedRecordsEmptyState(babyName = snapshot.baby.name.takeIf { it.isNotEmpty() })
        }

        Spacer(modifier = Modifier.height(28.dp))
        DashboardSection(title = "RECENT FEEDINGS") {
            if (completedSessions.isEmpty()) {
                EmptySectionMessage(
                    title = "No feeding history shared",
                    body = "Completed feedings from the primary device will appear here.",
                )
            } else {
                completedSessions.forEach { session ->
                    FeedingHistoryRow(session = session, lastRefreshAt = lastRefreshAt)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        DashboardSection(
            title = "LAST SLEEP",
            color = MaterialTheme.colorScheme.secondary,
        ) {
            if (lastSleep == null) {
                EmptySectionMessage(
                    title = "No sleep record shared",
                    body = "The latest nap or night sleep will appear after the next sync.",
                )
            } else {
                SleepHistoryRow(sleep = lastSleep, lastRefreshAt = lastRefreshAt)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        AllergySection(baby = snapshot.baby)
    }
}

@Composable
private fun PartnerStatusPanel(
    activeSession: SessionSnapshot?,
    lastSharedText: String,
    isShareStale: Boolean,
    error: String?,
    onClearError: () -> Unit,
    lastSyncAt: Instant,
    lastRefreshAt: Long,
) {
    val hasActiveSession = activeSession != null
    val containerColor = if (hasActiveSession) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = if (hasActiveSession) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text = "SHARED STATUS",
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
            )
            Text(
                text = "Primary device last shared: $lastSharedText",
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (activeSession == null) {
                NoActiveSessionStatus()
            } else {
                ActiveSessionSummary(
                    session = activeSession,
                    lastSyncAt = lastSyncAt,
                    lastRefreshAt = lastRefreshAt,
                )
            }

            if (isShareStale) {
                Spacer(modifier = Modifier.height(14.dp))
                SyncWarning()
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onClearError) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun NoActiveSessionStatus() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "No active feeding shared",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Active feedings appear here when the primary device syncs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActiveSessionSummary(
    session: SessionSnapshot,
    lastSyncAt: Instant,
    lastRefreshAt: Long,
) {
    val elapsedAtLastSync = remember(session.startTime, session.pausedDurationMs, lastSyncAt, lastRefreshAt) {
        Duration.between(Instant.ofEpochMilli(session.startTime), lastSyncAt)
            .minusMillis(session.pausedDurationMs)
            .let { if (it.isNegative) Duration.ZERO else it }
    }
    val sideLabel = remember(session.startingSide, session.switchTime) {
        val started = if (session.startingSide == "LEFT") "Left" else "Right"
        if (session.switchTime != null) {
            val current = if (session.startingSide == "LEFT") "Right" else "Left"
            "$started to $current breast"
        } else {
            "$started breast"
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "\uD83E\uDD31", style = MaterialTheme.typography.headlineSmall)
            }
            Column {
                Text(
                    text = "ACTIVE WHEN LAST SHARED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = sideLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = elapsedAtLastSync.formatDuration(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Read-only estimate from the primary device's last sync",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SyncWarning() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = WarningContainerAmber,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "This read-only view may be behind. " +
                "Ask the primary parent to open Akachan if something is missing.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnWarningContainerAmber,
        )
    }
}

@Composable
private fun SharedRecordsEmptyState(babyName: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(
                text = "No shared records yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (babyName != null) {
                    "When the primary parent tracks $babyName, shared feedings and sleep will appear here."
                } else {
                    "When the primary parent tracks a feeding or sleep, it will appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DashboardSection(
    title: String,
    color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(text = title, color = color)
        content()
    }
}

@Composable
private fun EmptySectionMessage(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedingHistoryRow(session: SessionSnapshot, lastRefreshAt: Long) {
    val startInstant = Instant.ofEpochMilli(session.startTime)
    val endInstant = Instant.ofEpochMilli(session.endTime!!)
    val duration = remember(session.startTime, session.endTime, session.pausedDurationMs) {
        Duration.between(startInstant, endInstant)
            .minusMillis(session.pausedDurationMs)
            .let { if (it.isNegative) Duration.ZERO else it }
    }
    val timeAgo = remember(session.startTime, lastRefreshAt) {
        Duration.between(startInstant, Instant.now()).formatElapsedAgo()
    }
    val sideText = remember(session.startingSide, session.switchTime) {
        val from = if (session.startingSide == "LEFT") "Left" else "Right"
        if (session.switchTime != null) {
            val to = if (session.startingSide == "LEFT") "Right" else "Left"
            "$from to $to"
        } else {
            "$from breast"
        }
    }

    HistoryCard(
        title = duration.formatDuration(),
        subtitle = sideText,
        trailing = timeAgo,
        badgeEmoji = "\uD83E\uDD31",
        badgeColor = MaterialTheme.colorScheme.primaryContainer,
    )
}

@Composable
private fun SleepHistoryRow(sleep: SleepSnapshot, lastRefreshAt: Long) {
    val startInstant = Instant.ofEpochMilli(sleep.startTime)
    val endInstant = sleep.endTime?.let { Instant.ofEpochMilli(it) }
    val duration = remember(sleep.startTime, sleep.endTime) {
        endInstant?.let { Duration.between(startInstant, it) }
    }
    val timeAgo = remember(sleep.endTime, lastRefreshAt) {
        endInstant?.let { Duration.between(it, Instant.now()).formatElapsedAgo() }
    }
    val typeLabel = if (sleep.sleepType == "NAP") "Nap" else "Night sleep"

    HistoryCard(
        title = duration?.formatDuration() ?: "In progress",
        subtitle = typeLabel,
        trailing = timeAgo ?: "",
        badgeEmoji = "\uD83D\uDCA4",
        badgeColor = MaterialTheme.colorScheme.secondaryContainer,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllergySection(baby: BabySnapshot) {
    Column {
        SectionHeader(text = "ALLERGIES", color = WarningAmber)
        Spacer(modifier = Modifier.height(8.dp))
        if (baby.allergies.isEmpty()) {
            EmptySectionMessage(
                title = "No allergies shared",
                body = "Allergy notes from the primary device will appear here.",
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                baby.allergies.forEach { allergyName ->
                    val label = AllergyType.entries.find { it.name == allergyName }?.label ?: allergyName
                    SuggestionChip(
                        onClick = {},
                        label = { Text(label) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = WarningContainerAmber,
                            labelColor = OnWarningContainerAmber,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    babyName: String?,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "\uD83D\uDC76",
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No shared records yet",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (babyName != null) {
                "When the primary parent tracks $babyName, shared feedings and sleep will appear here."
            } else {
                "When the primary parent tracks a feeding or sleep, it will appear here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRefresh) { Text("Check for shared updates") }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Check again") }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}
