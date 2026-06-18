package com.babytracker.ui.partner

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.babytracker.R
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.ui.bottlefeed.BottleFeedSheet
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
import com.babytracker.util.formatLength
import com.babytracker.util.formatVolume
import com.babytracker.util.formatWeight
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val STALE_SYNC_THRESHOLD_MINUTES = 30L
private const val ONE_MINUTE_MS = 60_000L
private const val MINUTES_PER_HOUR = 60

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
    onNavigateToFeedHistory: () -> Unit = {},
    nowProvider: () -> Long = System::currentTimeMillis,
    viewModel: PartnerDashboardViewModel = hiltViewModel(),
    bottleFeedViewModel: PartnerBottleFeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bottleFeedState by bottleFeedViewModel.uiState.collectAsStateWithLifecycle()
    var showBottleFeedSheet by remember { mutableStateOf(false) }

    if (uiState.isDisconnected) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.partner_ended_title)) },
            text = { Text(stringResource(R.string.partner_ended_message)) },
            confirmButton = {
                TextButton(onClick = onDisconnected) { Text(stringResource(R.string.partner_go_settings)) }
            },
        )
    }

    val snapshot = uiState.snapshot
    LaunchedEffect(snapshot?.milkBags) {
        snapshot?.let { bottleFeedViewModel.onBagsAvailable(it.milkBags) }
    }
    LaunchedEffect(bottleFeedState.saved) {
        if (bottleFeedState.saved) {
            showBottleFeedSheet = false
            viewModel.refresh()
        }
    }

    val babyName = snapshot?.baby?.name?.takeIf { it.isNotBlank() }
    val isRefreshingExistingDashboard = uiState.isLoading && snapshot != null

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
                            text = babyName ?: stringResource(R.string.partner_default_name),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        BabyAgeSubtitle(
                            snapshot = snapshot,
                            babyName = babyName,
                            now = Instant.ofEpochMilli(nowProvider()),
                        )
                    }
                },
                actions = {
                    if (isRefreshingExistingDashboard) {
                        val checkingDescription = stringResource(R.string.partner_checking_updates)
                        val checkingState = stringResource(R.string.partner_checking)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    contentDescription = checkingDescription
                                    stateDescription = checkingState
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
                        IconButton(
                            onClick = viewModel::refresh,
                            enabled = !uiState.isLoading,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.partner_check_updates_cd))
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings_content_description))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        val pullRefreshDescription = stringResource(R.string.partner_pull_refresh)
        val loadingDescription = stringResource(R.string.partner_loading)
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .semantics {
                    contentDescription = pullRefreshDescription
                },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading && snapshot == null -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .semantics {
                                    contentDescription = loadingDescription
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
                            onLogBottle = {
                                bottleFeedViewModel.startLogging()
                                showBottleFeedSheet = true
                            },
                            onNavigateToFeedHistory = onNavigateToFeedHistory,
                            nowProvider = nowProvider,
                            volumeUnit = uiState.volumeUnit,
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

    if (showBottleFeedSheet) {
        BottleFeedSheet(
            state = bottleFeedState,
            onTypeChange = bottleFeedViewModel::onTypeChange,
            onVolumeChange = bottleFeedViewModel::onVolumeChange,
            onTimeChange = bottleFeedViewModel::onTimeChange,
            onBagSelect = bottleFeedViewModel::onBagSelect,
            onNotesChange = bottleFeedViewModel::onNotesChange,
            onConfirm = bottleFeedViewModel::onConfirm,
            onDismiss = { if (!bottleFeedState.isSaving) showBottleFeedSheet = false },
        )
    }
}

@Composable
private fun BabyAgeSubtitle(
    snapshot: ShareSnapshot?,
    babyName: String?,
    now: Instant,
) {
    if (babyName != null && snapshot != null) {
        val ageWeeks = remember(snapshot.baby.birthDateMs, now) {
            babyAgeWeeks(
                birthDateMs = snapshot.baby.birthDateMs,
                now = now,
            )
        }
        Text(
            text = babyAgeSubtitleText(ageWeeks, LocalContext.current),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            text = stringResource(R.string.partner_readonly),
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
    onLogBottle: () -> Unit,
    onNavigateToFeedHistory: () -> Unit,
    nowProvider: () -> Long,
    volumeUnit: VolumeUnit,
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
    val activeSleep = snapshot.sleepRecords.firstOrNull { it.endTime == null }
    val completedSessions = snapshot.sessions.filter { it.endTime != null }.take(3)
    val lastSleep = snapshot.sleepRecords.firstOrNull()
    val hasSharedRecords = snapshot.sessions.isNotEmpty() ||
        snapshot.sleepRecords.isNotEmpty() ||
        snapshot.bottleFeeds.isNotEmpty() ||
        snapshot.growth.isNotEmpty() ||
        snapshot.milestones.isNotEmpty() ||
        snapshot.diapers.isNotEmpty()
    val context = LocalContext.current
    val lastSharedText = remember(snapshot.lastSyncAt, nowMs, context) {
        Duration.between(snapshot.lastSyncAt, now).coerceAtLeast(Duration.ZERO).formatElapsedAgo(context)
    }
    val lastCheckedText = remember(lastRefreshAt, nowMs, context) {
        if (lastRefreshAt == 0L) {
            null
        } else {
            Duration.between(Instant.ofEpochMilli(lastRefreshAt), now)
                .coerceAtLeast(Duration.ZERO)
                .formatElapsedAgo(context)
        }
    }
    val isShareStale = remember(snapshot.lastSyncAt, nowMs) {
        Duration.between(snapshot.lastSyncAt, now).toMinutes() >= STALE_SYNC_THRESHOLD_MINUTES
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideLayout = maxWidth >= 720.dp
        val horizontalPadding = if (isWideLayout) 24.dp else 16.dp
        val topPadding = if (isWideLayout) 12.dp else 8.dp
        val bottomPadding = if (isWideLayout) 32.dp else 24.dp

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (isWideLayout) {
                WideDashboardContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 1120.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = horizontalPadding,
                            vertical = 0.dp,
                        )
                        .padding(top = topPadding, bottom = bottomPadding),
                    activeSession = activeSession,
                    activeSleep = activeSleep,
                    completedSessions = completedSessions,
                    lastSleep = lastSleep,
                    hasSharedRecords = hasSharedRecords,
                    snapshot = snapshot,
                    lastSharedText = lastSharedText,
                    lastCheckedText = lastCheckedText,
                    isShareStale = isShareStale,
                    error = error,
                    onClearError = onClearError,
                    isLoading = isLoading,
                    onRefresh = onRefresh,
                    onLogBottle = onLogBottle,
                    onNavigateToFeedHistory = onNavigateToFeedHistory,
                    now = now,
                    volumeUnit = volumeUnit,
                )
            } else {
                CompactDashboardContent(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        top = topPadding,
                        end = horizontalPadding,
                        bottom = bottomPadding,
                    ),
                    activeSession = activeSession,
                    activeSleep = activeSleep,
                    completedSessions = completedSessions,
                    lastSleep = lastSleep,
                    hasSharedRecords = hasSharedRecords,
                    snapshot = snapshot,
                    lastSharedText = lastSharedText,
                    lastCheckedText = lastCheckedText,
                    isShareStale = isShareStale,
                    error = error,
                    onClearError = onClearError,
                    isLoading = isLoading,
                    onRefresh = onRefresh,
                    onLogBottle = onLogBottle,
                    onNavigateToFeedHistory = onNavigateToFeedHistory,
                    now = now,
                    volumeUnit = volumeUnit,
                )
            }
        }
    }
}

@Composable
private fun CompactDashboardContent(
    contentPadding: PaddingValues,
    activeSession: SessionSnapshot?,
    activeSleep: SleepSnapshot?,
    completedSessions: List<SessionSnapshot>,
    lastSleep: SleepSnapshot?,
    hasSharedRecords: Boolean,
    snapshot: ShareSnapshot,
    lastSharedText: String,
    lastCheckedText: String?,
    isShareStale: Boolean,
    error: String?,
    onClearError: () -> Unit,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onLogBottle: () -> Unit,
    onNavigateToFeedHistory: () -> Unit,
    now: Instant,
    volumeUnit: VolumeUnit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        PartnerStatusPanel(
            activeSession = activeSession,
            activeSleep = activeSleep,
            lastSharedText = lastSharedText,
            lastCheckedText = lastCheckedText,
            isShareStale = isShareStale,
            error = error,
            onClearError = onClearError,
            lastSyncAt = snapshot.lastSyncAt,
            now = now,
        )

        Spacer(modifier = Modifier.height(16.dp))
        PartnerBottleActions(
            onLogBottle = onLogBottle,
            onNavigateToFeedHistory = onNavigateToFeedHistory,
        )

        Spacer(modifier = Modifier.height(16.dp))
        CareSummaryPanel(
            lastFeeding = snapshot.sessions.firstOrNull { it.endTime != null },
            lastSleep = lastSleep,
            allergyCount = snapshot.baby.allergies.size,
            now = now,
        )

        snapshot.sleepPrediction?.let { prediction ->
            Spacer(modifier = Modifier.height(16.dp))
            PartnerSleepPredictionCard(
                prediction = prediction,
                now = now,
                activeSleepType = activeSleep?.sleepType,
                hasActiveSleep = snapshot.sleepRecords.any { it.endTime == null },
                hasActiveFeeding = activeSession != null,
            )
        }

        val inventoryTotalMl = snapshot.inventoryTotalMl
        val inventoryBagCount = snapshot.inventoryBagCount
        if (inventoryTotalMl != null && inventoryBagCount != null && inventoryBagCount > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            PartnerInventoryCard(
                totalMl = inventoryTotalMl,
                bagCount = inventoryBagCount,
                updatedAtMs = snapshot.inventoryUpdatedAt,
                now = now,
                volumeUnit = volumeUnit,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (snapshot.growth.isNotEmpty() || snapshot.milestones.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PartnerGrowthMilestonesCard(snapshot = snapshot, modifier = Modifier.fillMaxWidth())
        }

        if (snapshot.diapers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PartnerDiaperCard(diapers = snapshot.diapers, modifier = Modifier.fillMaxWidth())
        }

        if (!hasSharedRecords) {
            Spacer(modifier = Modifier.height(18.dp))
            SharedRecordsEmptyState(babyName = snapshot.baby.name.takeIf { it.isNotBlank() })
        }

        Spacer(modifier = Modifier.height(28.dp))
        DashboardTimelineSections(
            completedSessions = completedSessions,
            lastSleep = lastSleep,
            bottleFeeds = snapshot.bottleFeeds,
            baby = snapshot.baby,
            now = now,
            volumeUnit = volumeUnit,
        )

        Spacer(modifier = Modifier.height(24.dp))
        RefreshSharedUpdatesButton(
            isLoading = isLoading,
            onRefresh = onRefresh,
        )
    }
}

@Composable
private fun WideDashboardContent(
    activeSession: SessionSnapshot?,
    activeSleep: SleepSnapshot?,
    completedSessions: List<SessionSnapshot>,
    lastSleep: SleepSnapshot?,
    hasSharedRecords: Boolean,
    snapshot: ShareSnapshot,
    lastSharedText: String,
    lastCheckedText: String?,
    isShareStale: Boolean,
    error: String?,
    onClearError: () -> Unit,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onLogBottle: () -> Unit,
    onNavigateToFeedHistory: () -> Unit,
    now: Instant,
    volumeUnit: VolumeUnit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(0.9f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PartnerStatusPanel(
                activeSession = activeSession,
                activeSleep = activeSleep,
                lastSharedText = lastSharedText,
                lastCheckedText = lastCheckedText,
                isShareStale = isShareStale,
                error = error,
                onClearError = onClearError,
                lastSyncAt = snapshot.lastSyncAt,
                now = now,
            )
            PartnerBottleActions(
                onLogBottle = onLogBottle,
                onNavigateToFeedHistory = onNavigateToFeedHistory,
            )
            CareSummaryPanel(
                lastFeeding = snapshot.sessions.firstOrNull { it.endTime != null },
                lastSleep = lastSleep,
                allergyCount = snapshot.baby.allergies.size,
                now = now,
            )
            snapshot.sleepPrediction?.let { prediction ->
                PartnerSleepPredictionCard(
                    prediction = prediction,
                    now = now,
                    activeSleepType = activeSleep?.sleepType,
                    hasActiveSleep = snapshot.sleepRecords.any { it.endTime == null },
                    hasActiveFeeding = activeSession != null,
                )
            }
            val inventoryTotalMl = snapshot.inventoryTotalMl
            val inventoryBagCount = snapshot.inventoryBagCount
            if (inventoryTotalMl != null && inventoryBagCount != null && inventoryBagCount > 0) {
                PartnerInventoryCard(
                    totalMl = inventoryTotalMl,
                    bagCount = inventoryBagCount,
                    updatedAtMs = snapshot.inventoryUpdatedAt,
                    now = now,
                    volumeUnit = volumeUnit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (snapshot.growth.isNotEmpty() || snapshot.milestones.isNotEmpty()) {
                PartnerGrowthMilestonesCard(snapshot = snapshot, modifier = Modifier.fillMaxWidth())
            }
            if (!hasSharedRecords) {
                SharedRecordsEmptyState(babyName = snapshot.baby.name.takeIf { it.isNotBlank() })
            }
            RefreshSharedUpdatesButton(
                isLoading = isLoading,
                onRefresh = onRefresh,
            )
        }

        DashboardTimelineSections(
            modifier = Modifier.weight(1.1f),
            completedSessions = completedSessions,
            lastSleep = lastSleep,
            bottleFeeds = snapshot.bottleFeeds,
            baby = snapshot.baby,
            now = now,
            volumeUnit = volumeUnit,
        )
    }
}

@Composable
private fun DashboardTimelineSections(
    completedSessions: List<SessionSnapshot>,
    lastSleep: SleepSnapshot?,
    bottleFeeds: List<BottleFeedSnapshot>,
    baby: BabySnapshot,
    now: Instant,
    volumeUnit: VolumeUnit,
    modifier: Modifier = Modifier,
) {
    val recentBottles = remember(bottleFeeds) {
        bottleFeeds.sortedByDescending { it.timestamp }.take(3)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        DashboardSection(title = stringResource(R.string.partner_recent_feedings)) {
            if (completedSessions.isEmpty()) {
                EmptySectionMessage(
                    title = stringResource(R.string.partner_no_feeding_history),
                    body = stringResource(R.string.partner_empty_feeding_body),
                )
            } else {
                completedSessions.forEach { session ->
                    FeedingHistoryRow(session = session, now = now)
                }
            }
        }

        if (recentBottles.isNotEmpty()) {
            DashboardSection(
                title = stringResource(R.string.partner_recent_bottles),
                color = MaterialTheme.colorScheme.tertiary,
            ) {
                recentBottles.forEach { feed ->
                    BottleHistoryRow(feed = feed, now = now, volumeUnit = volumeUnit)
                }
            }
        }

        DashboardSection(
            title = stringResource(R.string.partner_last_sleep),
            color = MaterialTheme.colorScheme.secondary,
        ) {
            if (lastSleep == null) {
                EmptySectionMessage(
                    title = stringResource(R.string.partner_no_sleep_record),
                    body = stringResource(R.string.partner_empty_sleep_body),
                )
            } else {
                SleepHistoryRow(sleep = lastSleep, now = now)
            }
        }

        AllergySection(baby = baby)
    }
}

@Composable
private fun PartnerBottleActions(
    onLogBottle: () -> Unit,
    onNavigateToFeedHistory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onLogBottle,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.partner_log_bottle),
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
        OutlinedButton(
            onClick = onNavigateToFeedHistory,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.partner_feeding_history),
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PartnerStatusPanel(
    activeSession: SessionSnapshot?,
    activeSleep: SleepSnapshot?,
    lastSharedText: String,
    lastCheckedText: String?,
    isShareStale: Boolean,
    error: String?,
    onClearError: () -> Unit,
    lastSyncAt: Instant,
    now: Instant,
) {
    val containerColor = when {
        activeSession != null -> MaterialTheme.colorScheme.primaryContainer
        activeSleep != null -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = when {
        activeSession != null -> MaterialTheme.colorScheme.onPrimaryContainer
        activeSleep != null -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val context = LocalContext.current
    val activeStateText = when {
        activeSession != null -> stringResource(R.string.partner_status_feeding)
        activeSleep != null -> stringResource(
            R.string.partner_status_in_progress,
            sleepTypeLabel(activeSleep.sleepType, context),
        )
        else -> stringResource(R.string.partner_status_quiet)
    }
    val stateText = if (isShareStale) {
        stringResource(R.string.partner_share_behind)
    } else {
        stringResource(R.string.partner_share_current)
    }
    val statusDescription = if (lastCheckedText != null) {
        stringResource(
            R.string.partner_status_cd_checked,
            activeStateText,
            stateText,
            lastSharedText.lowercaseFirstChar(),
            lastCheckedText.lowercaseFirstChar(),
        )
    } else {
        stringResource(
            R.string.partner_status_cd,
            activeStateText,
            stateText,
            lastSharedText.lowercaseFirstChar(),
        )
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
        elevation = CardDefaults.cardElevation(defaultElevation = dashboardPanelElevation()),
        border = dashboardPanelBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            if (activeSession == null && activeSleep == null) {
                NoActiveSessionStatus()
            } else {
                activeSession?.let {
                    ActiveSessionSummary(
                        session = it,
                        lastSyncAt = lastSyncAt,
                        now = now,
                    )
                }
                if (activeSession != null && activeSleep != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                activeSleep?.let {
                    ActiveSleepSummary(
                        sleep = it,
                        lastSyncAt = lastSyncAt,
                        now = now,
                    )
                }
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
                TextButton(onClick = onClearError) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
}

@Composable
private fun NoActiveSessionStatus() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.partner_quiet),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.partner_quiet_desc),
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
            text = stringResource(R.string.partner_shared_ago, lastSharedText.lowercaseFirstChar()),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
        if (lastCheckedText != null) {
            Text(
                text = stringResource(R.string.partner_checked_ago, lastCheckedText.lowercaseFirstChar()),
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
    val elapsed = remember(session.startTime, session.pausedDurationMs, lastSyncAt, now) {
        activeSessionElapsedDuration(
            session = session,
            lastSyncAt = lastSyncAt,
            now = now,
        )
    }
    val started = stringResource(
        if (session.startingSide == "LEFT") R.string.side_left else R.string.side_right,
    )
    val sideLabel = if (session.switchTime != null) {
        val current = stringResource(
            if (session.startingSide == "LEFT") R.string.side_right else R.string.side_left,
        )
        stringResource(R.string.partner_side_switch, started, current)
    } else {
        stringResource(R.string.partner_side_single, started)
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
                    text = stringResource(R.string.partner_feeding_when_shared),
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
            text = elapsed.formatDuration(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.partner_estimate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ActiveSleepSummary(
    sleep: SleepSnapshot,
    lastSyncAt: Instant,
    now: Instant,
) {
    val elapsed = remember(sleep.startTime, lastSyncAt, now) {
        activeSleepElapsedDuration(
            sleep = sleep,
            lastSyncAt = lastSyncAt,
            now = now,
        )
    }
    val context = LocalContext.current
    val typeLabel = remember(sleep.sleepType, context) { sleepTypeLabel(sleep.sleepType, context) }

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
                        color = MaterialTheme.colorScheme.secondary,
                        shape = MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "💤", style = MaterialTheme.typography.headlineSmall)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.partner_sleeping_when_shared),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = elapsed.formatDuration(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.partner_estimate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun SyncWarning() {
    val warningColors = warningColors()
    val syncWarningDescription = stringResource(R.string.partner_sync_warning_cd)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = syncWarningDescription
            }
            .background(
                color = warningColors.container,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
text = stringResource(R.string.partner_stale),
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
        elevation = CardDefaults.cardElevation(defaultElevation = dashboardPanelElevation()),
        border = dashboardPanelBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(
                text = stringResource(R.string.partner_no_shared_records),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (babyName != null) {
                    stringResource(R.string.partner_empty_records_named, babyName)
                } else {
                    stringResource(R.string.partner_empty_records)
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
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val context = LocalContext.current
        Text(
            text = stringResource(R.string.partner_latest_care),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SummaryLine(
            label = lastFeeding?.let { stringResource(R.string.partner_fed, it.feedingAgoText(now, context)) } ?: stringResource(R.string.partner_no_feeding_yet),
            color = MaterialTheme.colorScheme.primary,
        )
        SummaryLine(
            label = lastSleep?.let { "${it.sleepVerb(context)} ${it.sleepAgoText(now, context)}" }
                ?: stringResource(R.string.partner_no_sleep_shared),
            color = MaterialTheme.colorScheme.secondary,
        )
        SummaryLine(
            label = allergySummaryText(allergyCount, context),
            color = warningColors().accent,
        )
    }
}

@Composable
private fun PartnerInventoryCard(
    totalMl: Int,
    bagCount: Int,
    updatedAtMs: Long?,
    now: Instant,
    volumeUnit: VolumeUnit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val volumeText = formatVolume(totalMl, volumeUnit)
    val updatedText = remember(updatedAtMs, now, context) {
        updatedAtMs?.let {
            Duration.between(Instant.ofEpochMilli(it), now)
                .coerceAtLeast(Duration.ZERO)
                .formatElapsedAgo(context)
        }
    }
    val stashSummary = pluralStringResource(R.plurals.partner_stash_cd, bagCount, volumeText, bagCount)
    val updatedSuffix = updatedText?.let { stringResource(R.string.partner_stash_cd_updated, it) }.orEmpty()
    val cardDescription = stashSummary + updatedSuffix

    Card(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = cardDescription
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = dashboardPanelElevation()),
        border = dashboardPanelBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clearAndSetSemantics {}
                    .background(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🧊",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.partner_milk_stash),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Text(
                    text = pluralStringResource(R.plurals.partner_stash_summary, bagCount, volumeText, bagCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (updatedText != null) {
                    Text(
                        text = stringResource(R.string.partner_updated, updatedText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
    val context = LocalContext.current
    val timeAgo = remember(session.startTime, now, context) {
        Duration.between(startInstant, now).coerceAtLeast(Duration.ZERO).formatElapsedAgo(context)
    }
    val from = stringResource(
        if (session.startingSide == "LEFT") R.string.side_left else R.string.side_right,
    )
    val sideText = if (session.switchTime != null) {
        val to = stringResource(
            if (session.startingSide == "LEFT") R.string.side_right else R.string.side_left,
        )
        stringResource(R.string.partner_side_switch_short, from, to)
    } else {
        stringResource(R.string.partner_side_single, from)
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
private fun BottleHistoryRow(
    feed: BottleFeedSnapshot,
    now: Instant,
    volumeUnit: VolumeUnit,
) {
    val context = LocalContext.current
    val volumeText = remember(feed.volumeMl, volumeUnit) { formatVolume(feed.volumeMl, volumeUnit) }
    val typeLabel = stringResource(
        if (feed.type == "BREAST_MILK") R.string.bottle_feed_type_breast_milk else R.string.bottle_feed_type_formula,
    )
    val timeAgo = remember(feed.timestamp, now, context) {
        Duration.between(Instant.ofEpochMilli(feed.timestamp), now)
            .coerceAtLeast(Duration.ZERO)
            .formatElapsedAgo(context)
    }

    HistoryCard(
        title = volumeText,
        subtitle = typeLabel,
        trailing = timeAgo,
        badgeEmoji = "🍼",
        badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
        trailingColor = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
private fun SleepHistoryRow(sleep: SleepSnapshot, now: Instant) {
    val startInstant = Instant.ofEpochMilli(sleep.startTime)
    val endInstant = sleep.endTime?.let { Instant.ofEpochMilli(it) }
    val duration = remember(sleep.startTime, sleep.endTime) {
        endInstant?.let { Duration.between(startInstant, it) }
    }
    val context = LocalContext.current
    val timeAgo = remember(sleep.endTime, now, context) {
        endInstant?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO).formatElapsedAgo(context) }
    }
    val typeLabel = sleepTypeLabel(sleep.sleepType, context)

    HistoryCard(
        title = duration?.formatDuration() ?: stringResource(R.string.label_in_progress),
        subtitle = typeLabel,
        trailing = timeAgo ?: "",
        badgeEmoji = "\uD83D\uDCA4",
        badgeColor = MaterialTheme.colorScheme.secondaryContainer,
        trailingColor = MaterialTheme.colorScheme.secondary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartnerGrowthMilestonesCard(
    snapshot: ShareSnapshot,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = dashboardPanelElevation()),
        border = dashboardPanelBorder(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (snapshot.growth.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.partner_growth),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                GrowthType.entries.forEach { type ->
                    val latest = snapshot.growth.filter { it.type == type.name }.maxByOrNull { it.takenAtMs }
                    if (latest != null) {
                        SummaryLine(
                            label = stringResource(R.string.partner_growth_label, type.partnerLabel(LocalContext.current), formatGrowthValue(type, latest.valueCanonical)),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (snapshot.milestones.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.partner_milestones),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
                val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
                snapshot.milestones.forEach { snap ->
                    val whenLabel = buildString {
                        append(LocalDate.ofEpochDay(snap.dateEpochDay).format(formatter))
                        snap.timeMinuteOfDay?.let {
                            append(" · ${LocalTime.of(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR).format(timeFormatter)}")
                        }
                    }
                    SummaryLine(
                        label = stringResource(R.string.partner_milestone_label, snap.title, whenLabel),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

private fun GrowthType.partnerLabel(context: Context): String = when (this) {
    GrowthType.WEIGHT -> context.getString(R.string.growth_tab_weight)
    GrowthType.LENGTH -> context.getString(R.string.growth_tab_length)
    GrowthType.HEAD_CIRC -> context.getString(R.string.growth_tab_head)
}

private fun formatGrowthValue(type: GrowthType, valueCanonical: Long): String = when (type) {
    GrowthType.WEIGHT -> formatWeight(valueCanonical, MeasurementSystem.METRIC)
    GrowthType.LENGTH, GrowthType.HEAD_CIRC -> formatLength(valueCanonical, MeasurementSystem.METRIC)
}

@Composable
private fun AllergySection(baby: BabySnapshot) {
    val warningColors = warningColors()
    Column {
        SectionHeader(text = stringResource(R.string.partner_allergies), color = warningColors.onSurfaceAccent)
        Spacer(modifier = Modifier.height(8.dp))
        if (baby.allergies.isEmpty()) {
            EmptySectionMessage(
                title = stringResource(R.string.partner_no_allergies),
                body = stringResource(R.string.partner_empty_allergy_body),
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
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
    val allergyContentDescription = stringResource(R.string.partner_allergy_cd, label)
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .background(
                color = colors.container,
                shape = MaterialTheme.shapes.small,
            )
            .semantics {
                contentDescription = allergyContentDescription
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
    val checkingDescription = stringResource(R.string.partner_check_state_loading)
    val readyDescription = stringResource(R.string.partner_check_state_ready)
    Button(
        onClick = onRefresh,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                stateDescription = if (isLoading) checkingDescription else readyDescription
            },
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.partner_check_shared),
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

private fun SessionSnapshot.feedingAgoText(now: Instant, context: Context): String =
    Duration.between(Instant.ofEpochMilli(startTime), now)
        .coerceAtLeast(Duration.ZERO)
        .formatElapsedAgo(context)
        .lowercaseFirstChar()

private fun SleepSnapshot.sleepAgoText(now: Instant, context: Context): String {
    val time = endTime ?: startTime
    return Duration.between(Instant.ofEpochMilli(time), now)
        .coerceAtLeast(Duration.ZERO)
        .formatElapsedAgo(context)
        .lowercaseFirstChar()
}

private fun SleepSnapshot.sleepVerb(context: Context): String =
    if (sleepType == "NAP") {
        context.getString(R.string.partner_slept_nap)
    } else {
        context.getString(R.string.partner_slept_night)
    }

internal fun sleepTypeLabel(sleepType: String, context: Context): String =
    if (sleepType == "NAP") {
        context.getString(R.string.sleep_type_nap)
    } else {
        context.getString(R.string.sleep_type_night)
    }

private fun allergySummaryText(count: Int, context: Context): String =
    if (count == 0) {
        context.getString(R.string.partner_no_allergies_shared)
    } else {
        context.resources.getQuantityString(R.plurals.partner_allergies_shared, count, count)
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
            text = stringResource(R.string.partner_no_shared_records),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (babyName != null) {
                stringResource(R.string.partner_empty_records_named, babyName)
            } else {
                stringResource(R.string.partner_empty_records)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.partner_check_updates_cd),
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
        Button(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.partner_check_again))
        }
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

@Composable
private fun dashboardPanelElevation() = if (LocalDarkTheme.current) 0.dp else 1.dp

@Composable
private fun dashboardPanelBorder() =
    if (LocalDarkTheme.current) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
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

internal fun babyAgeWeeks(
    birthDateMs: Long,
    now: Instant,
): Int {
    val ageDays = Duration.between(Instant.ofEpochMilli(birthDateMs), now)
        .toDays()
        .coerceAtLeast(0L)

    return (ageDays / 7).toInt()
}

internal fun activeSessionElapsedDuration(
    session: SessionSnapshot,
    lastSyncAt: Instant,
    now: Instant,
): Duration =
    Duration.between(
        Instant.ofEpochMilli(session.startTime),
        activeSessionReferenceTime(lastSyncAt = lastSyncAt, now = now),
    )
        .minusMillis(session.pausedDurationMs)
        .coerceAtLeast(Duration.ZERO)

internal fun activeSleepElapsedDuration(
    sleep: SleepSnapshot,
    lastSyncAt: Instant,
    now: Instant,
): Duration =
    Duration.between(
        Instant.ofEpochMilli(sleep.startTime),
        activeSessionReferenceTime(lastSyncAt = lastSyncAt, now = now),
    ).coerceAtLeast(Duration.ZERO)

private fun activeSessionReferenceTime(
    lastSyncAt: Instant,
    now: Instant,
): Instant =
    if (Duration.between(lastSyncAt, now).toMinutes() >= STALE_SYNC_THRESHOLD_MINUTES) {
        lastSyncAt
    } else {
        now
    }

internal fun babyAgeSubtitleText(ageWeeks: Int, context: Context): String =
    if (ageWeeks == 0) {
        context.getString(R.string.partner_age_under_week)
    } else {
        context.resources.getQuantityString(R.plurals.partner_age_weeks, ageWeeks, ageWeeks)
    }
