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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.AllergyType
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.ui.component.HistoryCard
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.WarningAmber
import com.babytracker.ui.theme.WarningAmberDark
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark
import com.babytracker.util.formatDuration
import com.babytracker.util.formatElapsedAgo
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

private const val STALE_SYNC_THRESHOLD_MINUTES = 30L
private const val ONE_MINUTE_MS = 60_000L

internal data class PartnerWarningColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val onSurfaceAccent: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDashboardScreen(
    modifier: Modifier = Modifier,
    onDisconnected: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    nowProvider: () -> Long = System::currentTimeMillis,
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = babyName ?: "Baby Tracker",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        BabyAgeSubtitle(snapshot = snapshot, babyName = babyName)
                    }
                },
                actions = {
                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    contentDescription = "Checking for shared updates"
                                    stateDescription = "Checking"
                                    liveRegion = LiveRegionMode.Polite
                                },
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
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .semantics {
                    contentDescription = "Pull down to check for shared updates"
                },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading && snapshot == null -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .semantics {
                                    contentDescription = "Loading shared partner data"
                                },
                        )
                    }
                    snapshot != null -> {
                        DashboardContent(
                            snapshot = snapshot,
                            error = uiState.error,
                            onClearError = viewModel::clearError,
                            lastRefreshAt = uiState.lastRefreshAt,
                            isLoading = uiState.isLoading,
                            onRefresh = viewModel::refresh,
                            nowProvider = nowProvider,
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            text = "Read-only partner view",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DashboardContent(
    snapshot: ShareSnapshot,
    error: String?,
    onClearError: () -> Unit,
    lastRefreshAt: Long,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    nowProvider: () -> Long,
) {
    var nowMs by remember(snapshot.lastSyncAt) { mutableLongStateOf(nowProvider()) }
    LaunchedEffect(snapshot.lastSyncAt) {
        while (true) {
            nowMs = nowProvider()
            delay(ONE_MINUTE_MS)
        }
    }

    val now = remember(nowMs) { Instant.ofEpochMilli(nowMs) }
    val activeSession = snapshot.sessions.firstOrNull { it.endTime == null }
    val completedSessions = snapshot.sessions.filter { it.endTime != null }.take(3)
    val lastSleep = snapshot.sleepRecords.firstOrNull()
    val hasSharedRecords = snapshot.sessions.isNotEmpty() || snapshot.sleepRecords.isNotEmpty()
    val lastSharedText = remember(snapshot.lastSyncAt, nowMs) {
        Duration.between(snapshot.lastSyncAt, now).coerceAtLeast(Duration.ZERO).formatElapsedAgo()
    }
    val lastCheckedText = remember(lastRefreshAt, nowMs) {
        if (lastRefreshAt == 0L) {
            null
        } else {
            Duration.between(Instant.ofEpochMilli(lastRefreshAt), now)
                .coerceAtLeast(Duration.ZERO)
                .formatElapsedAgo()
        }
    }
    val isShareStale = remember(snapshot.lastSyncAt, nowMs) {
        Duration.between(snapshot.lastSyncAt, now).toMinutes() >= STALE_SYNC_THRESHOLD_MINUTES
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
            lastCheckedText = lastCheckedText,
            isShareStale = isShareStale,
            error = error,
            onClearError = onClearError,
            lastSyncAt = snapshot.lastSyncAt,
            now = now,
        )

        Spacer(modifier = Modifier.height(16.dp))
        CareSummaryPanel(
            lastFeeding = snapshot.sessions.firstOrNull { it.endTime != null },
            lastSleep = lastSleep,
            allergyCount = snapshot.baby.allergies.size,
            now = now,
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
                    FeedingHistoryRow(session = session, now = now)
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
                SleepHistoryRow(sleep = lastSleep, now = now)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        AllergySection(baby = snapshot.baby)

        Spacer(modifier = Modifier.height(24.dp))
        RefreshSharedUpdatesButton(
            isLoading = isLoading,
            onRefresh = onRefresh,
        )
    }
}

@Composable
private fun PartnerStatusPanel(
    activeSession: SessionSnapshot?,
    lastSharedText: String,
    lastCheckedText: String?,
    isShareStale: Boolean,
    error: String?,
    onClearError: () -> Unit,
    lastSyncAt: Instant,
    now: Instant,
) {
    val hasActiveSession = activeSession != null
    val containerColor = if (hasActiveSession) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = if (hasActiveSession) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val stateText = if (isShareStale) "Shared update may be behind" else "Shared update is current"
    val statusDescription = buildString {
        append(stateText)
        append(". Shared ")
        append(lastSharedText.lowercaseFirstChar())
        if (lastCheckedText != null) {
            append(". Checked ")
            append(lastCheckedText.lowercaseFirstChar())
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = statusDescription
                stateDescription = stateText
                liveRegion = LiveRegionMode.Polite
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            if (activeSession == null) {
                NoActiveSessionStatus()
            } else {
                ActiveSessionSummary(
                    session = activeSession,
                    lastSyncAt = lastSyncAt,
                    now = now,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            SharedUpdateMeta(
                lastSharedText = lastSharedText,
                lastCheckedText = lastCheckedText,
                color = labelColor,
            )

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
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Assertive
                    },
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
            text = "Quiet right now",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "No active feeding was shared. Nothing needs attention.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SharedUpdateMeta(
    lastSharedText: String,
    lastCheckedText: String?,
    color: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Shared ${lastSharedText.lowercaseFirstChar()}",
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
        if (lastCheckedText != null) {
            Text(
                text = "Checked ${lastCheckedText.lowercaseFirstChar()}",
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

@Composable
private fun ActiveSessionSummary(
    session: SessionSnapshot,
    lastSyncAt: Instant,
    now: Instant,
) {
    val elapsedAtLastSync = remember(session.startTime, session.pausedDurationMs, lastSyncAt, now) {
        Duration.between(Instant.ofEpochMilli(session.startTime), lastSyncAt)
            .minusMillis(session.pausedDurationMs)
            .coerceAtLeast(Duration.ZERO)
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
                    .clearAndSetSemantics {}
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "\uD83E\uDD31", style = MaterialTheme.typography.headlineSmall)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Feeding when shared",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = sideLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
            text = "Estimate from the last shared update",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SyncWarning() {
    val warningColors = warningColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Stale shared data warning. This read-only view may be behind. " +
                    "Ask the primary parent to open Akachan if something is missing."
            }
            .background(
                color = warningColors.container,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "This read-only view may be behind. " +
                "Ask the primary parent to open Akachan if something is missing.",
            style = MaterialTheme.typography.bodyMedium,
            color = warningColors.onContainer,
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
private fun CareSummaryPanel(
    lastFeeding: SessionSnapshot?,
    lastSleep: SleepSnapshot?,
    allergyCount: Int,
    now: Instant,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Latest care",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SummaryLine(
            label = lastFeeding?.let { "Fed ${it.feedingAgoText(now)}" } ?: "No feeding shared yet",
            color = MaterialTheme.colorScheme.primary,
        )
        SummaryLine(
            label = lastSleep?.let { "${it.sleepVerb()} ${it.sleepAgoText(now)}" } ?: "No sleep shared yet",
            color = MaterialTheme.colorScheme.secondary,
        )
        SummaryLine(
            label = allergySummaryText(allergyCount),
            color = warningColors().accent,
        )
    }
}

@Composable
private fun SummaryLine(
    label: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = MaterialTheme.shapes.extraSmall),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun FeedingHistoryRow(session: SessionSnapshot, now: Instant) {
    val startInstant = Instant.ofEpochMilli(session.startTime)
    val endInstant = Instant.ofEpochMilli(session.endTime!!)
    val duration = remember(session.startTime, session.endTime, session.pausedDurationMs) {
        Duration.between(startInstant, endInstant)
            .minusMillis(session.pausedDurationMs)
            .coerceAtLeast(Duration.ZERO)
    }
    val timeAgo = remember(session.startTime, now) {
        Duration.between(startInstant, now).coerceAtLeast(Duration.ZERO).formatElapsedAgo()
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
private fun SleepHistoryRow(sleep: SleepSnapshot, now: Instant) {
    val startInstant = Instant.ofEpochMilli(sleep.startTime)
    val endInstant = sleep.endTime?.let { Instant.ofEpochMilli(it) }
    val duration = remember(sleep.startTime, sleep.endTime) {
        endInstant?.let { Duration.between(startInstant, it) }
    }
    val timeAgo = remember(sleep.endTime, now) {
        endInstant?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO).formatElapsedAgo() }
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
    val warningColors = warningColors()
    Column {
        SectionHeader(text = "ALLERGIES", color = warningColors.onSurfaceAccent)
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
                    AllergyChip(
                        label = label,
                        colors = warningColors,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllergyChip(
    label: String,
    colors: PartnerWarningColors,
) {
    Box(
        modifier = Modifier
            .background(
                color = colors.container,
                shape = MaterialTheme.shapes.small,
            )
            .semantics {
                contentDescription = "Allergy: $label"
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RefreshSharedUpdatesButton(
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    Button(
        onClick = onRefresh,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = if (isLoading) {
                    "Checking for shared updates"
                } else {
                    "Ready to check shared updates"
                }
            },
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(18.dp)
                    .clearAndSetSemantics {},
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "Checking",
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        } else {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "Check shared updates",
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun SessionSnapshot.feedingAgoText(now: Instant): String =
    Duration.between(Instant.ofEpochMilli(startTime), now)
        .coerceAtLeast(Duration.ZERO)
        .formatElapsedAgo()
        .lowercaseFirstChar()

private fun SleepSnapshot.sleepAgoText(now: Instant): String {
    val time = endTime ?: startTime
    return Duration.between(Instant.ofEpochMilli(time), now)
        .coerceAtLeast(Duration.ZERO)
        .formatElapsedAgo()
        .lowercaseFirstChar()
}

private fun SleepSnapshot.sleepVerb(): String =
    if (sleepType == "NAP") "Napped" else "Slept"

private fun allergySummaryText(count: Int): String =
    when (count) {
        0 -> "No allergies shared"
        1 -> "1 allergy shared"
        else -> "$count allergies shared"
    }

private fun String.lowercaseFirstChar(): String =
    replaceFirstChar { it.lowercase() }

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
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.clearAndSetSemantics {},
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
        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Check for shared updates",
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
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
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun warningColors(): PartnerWarningColors {
    return partnerWarningColors(isDark = LocalDarkTheme.current)
}

internal fun partnerWarningColors(isDark: Boolean): PartnerWarningColors =
    if (isDark) {
        PartnerWarningColors(
            accent = WarningAmberDark,
            container = WarningContainerAmberDark,
            onContainer = OnWarningContainerAmberDark,
            onSurfaceAccent = OnWarningContainerAmberDark,
        )
    } else {
        PartnerWarningColors(
            accent = WarningAmber,
            container = WarningContainerAmber,
            onContainer = OnWarningContainerAmber,
            onSurfaceAccent = OnWarningContainerAmber,
        )
    }
