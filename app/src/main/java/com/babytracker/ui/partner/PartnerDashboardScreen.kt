package com.babytracker.ui.partner

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
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
import com.babytracker.ui.component.BottleFeedIcon
import com.babytracker.ui.component.BreastfeedingIcon
import com.babytracker.ui.component.DiaperIcon
import com.babytracker.ui.component.DoctorVisitIcon
import com.babytracker.ui.component.GrowthIcon
import com.babytracker.ui.component.HistoryCard
import com.babytracker.ui.component.InventoryIcon
import com.babytracker.ui.component.MilestoneIcon
import com.babytracker.ui.component.NapIcon
import com.babytracker.ui.component.SleepIcon
import com.babytracker.ui.component.labelRes
import com.babytracker.ui.home.ActiveStatusBadge
import com.babytracker.ui.home.EaseOutQuart
import com.babytracker.ui.home.HomeTileStatusText
import com.babytracker.ui.home.HomeTrackerTile
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.WarningAmber
import com.babytracker.ui.theme.WarningAmberDark
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark
import com.babytracker.ui.theme.diaperColors
import com.babytracker.ui.theme.doctorVisitColors
import com.babytracker.ui.theme.growthColors
import com.babytracker.ui.theme.milestoneColors
import com.babytracker.util.formatDuration
import com.babytracker.util.formatElapsedAgo
import com.babytracker.util.formatLength
import com.babytracker.util.formatVolume
import com.babytracker.util.formatWeight
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val STALE_SYNC_THRESHOLD_MINUTES = 30L
private const val ONE_MINUTE_MS = 60_000L
private const val MAX_CONTENT_WIDTH_DP = 600

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
    onNavigateToSleepHistory: () -> Unit = {},
    nowProvider: () -> Long = System::currentTimeMillis,
    viewModel: PartnerDashboardViewModel = hiltViewModel(),
    bottleFeedViewModel: PartnerBottleFeedViewModel = hiltViewModel(),
    sleepViewModel: PartnerSleepViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bottleFeedState by bottleFeedViewModel.uiState.collectAsStateWithLifecycle()
    val sleepState by sleepViewModel.uiState.collectAsStateWithLifecycle()
    var showBottleFeedSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }

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
    LaunchedEffect(snapshot?.sleepRecords) {
        sleepViewModel.onSleepRecordsAvailable(snapshot?.sleepRecords ?: emptyList())
    }
    LaunchedEffect(bottleFeedState.saved) {
        if (bottleFeedState.saved) {
            showBottleFeedSheet = false
            viewModel.refresh()
        }
    }
    // A revoked partner is detected globally by the dashboard refresh, which shows the "ended" dialog.
    LaunchedEffect(sleepState.accessRevoked) {
        if (sleepState.accessRevoked) {
            sleepViewModel.onAccessRevokedHandled()
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
                            onOpenSleepSheet = { showSleepSheet = true },
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

    if (showSleepSheet) {
        PartnerSleepSheet(
            state = sleepState,
            onStartNap = sleepViewModel::onStartNap,
            onStartNightSleep = sleepViewModel::onStartNightSleep,
            onStop = sleepViewModel::onStop,
            onEdit = {
                showSleepSheet = false
                sleepViewModel.onEditActive()
            },
            onViewHistory = {
                showSleepSheet = false
                onNavigateToSleepHistory()
            },
            onDismiss = { showSleepSheet = false },
        )
    }

    sleepState.editor?.let { editor ->
        PartnerSleepEditorSheet(
            editor = editor,
            onTypeChange = sleepViewModel::onEditorTypeChange,
            onStartChange = sleepViewModel::onEditorStartChange,
            onEndChange = sleepViewModel::onEditorEndChange,
            onNotesChange = sleepViewModel::onEditorNotesChange,
            onConfirm = sleepViewModel::onConfirmEdit,
            onDismiss = sleepViewModel::onDismissEditor,
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
    onOpenSleepSheet: () -> Unit,
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
    // These derive only from the snapshot, but nowMs ticks every minute and is read here, so without
    // remember the whole body — and these list scans — re-ran every minute. Key on the snapshot so
    // they recompute on data change only; let `now` feed just the elapsed-time formatters below.
    val activeSession = remember(snapshot.sessions) { snapshot.sessions.firstOrNull { it.endTime == null } }
    val activeSleep = remember(snapshot.sleepRecords) { snapshot.sleepRecords.firstOrNull { it.endTime == null } }
    val lastFeeding = remember(snapshot.sessions) { snapshot.sessions.firstOrNull { it.endTime != null } }
    val completedSessions =
        remember(snapshot.sessions) { snapshot.sessions.filter { it.endTime != null }.take(3) }
    val lastSleep = remember(snapshot.sleepRecords) { snapshot.sleepRecords.firstOrNull() }
    val lastCompletedSleep = remember(snapshot.sleepRecords) {
        snapshot.sleepRecords.firstOrNull { it.endTime != null }
    }
    val lastBottle = remember(snapshot.bottleFeeds) { snapshot.bottleFeeds.maxByOrNull { it.timestamp } }
    val hasSharedRecords = remember(snapshot) {
        snapshot.sessions.isNotEmpty() ||
            snapshot.sleepRecords.isNotEmpty() ||
            snapshot.bottleFeeds.isNotEmpty() ||
            snapshot.growth.isNotEmpty() ||
            snapshot.milestones.isNotEmpty() ||
            snapshot.diapers.isNotEmpty() ||
            snapshot.doctorVisits.isNotEmpty()
    }
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = MAX_CONTENT_WIDTH_DP.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PartnerSyncStrip(
                activeSession = activeSession,
                activeSleep = activeSleep,
                lastSharedText = lastSharedText,
                lastCheckedText = lastCheckedText,
                isShareStale = isShareStale,
                error = error,
                onClearError = onClearError,
            )

            PartnerTileGrid(
                snapshot = snapshot,
                activeSession = activeSession,
                activeSleep = activeSleep,
                lastFeeding = lastFeeding,
                lastCompletedSleep = lastCompletedSleep,
                lastBottle = lastBottle,
                now = now,
                volumeUnit = volumeUnit,
                onOpenSleepSheet = onOpenSleepSheet,
                onLogBottle = onLogBottle,
                onNavigateToFeedHistory = onNavigateToFeedHistory,
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

            if (!hasSharedRecords) {
                SharedRecordsEmptyState(babyName = snapshot.baby.name.takeIf { it.isNotBlank() })
            }

            Spacer(modifier = Modifier.height(4.dp))
            DashboardTimelineSections(
                completedSessions = completedSessions,
                lastSleep = lastSleep,
                bottleFeeds = snapshot.bottleFeeds,
                baby = snapshot.baby,
                now = now,
                volumeUnit = volumeUnit,
            )

            Spacer(modifier = Modifier.height(8.dp))
            RefreshSharedUpdatesButton(
                isLoading = isLoading,
                onRefresh = onRefresh,
            )
        }
    }
}

// --- Tile grid (mirrors the parent Home screen) -----------------------------------------------

@Composable
private fun PartnerTileGrid(
    snapshot: ShareSnapshot,
    activeSession: SessionSnapshot?,
    activeSleep: SleepSnapshot?,
    lastFeeding: SessionSnapshot?,
    lastCompletedSleep: SleepSnapshot?,
    lastBottle: BottleFeedSnapshot?,
    now: Instant,
    volumeUnit: VolumeUnit,
    onOpenSleepSheet: () -> Unit,
    onLogBottle: () -> Unit,
    onNavigateToFeedHistory: () -> Unit,
) {
    val inventoryTotalMl = snapshot.inventoryTotalMl
    val inventoryBagCount = snapshot.inventoryBagCount
    val showInventory = inventoryTotalMl != null && inventoryBagCount != null && inventoryBagCount > 0

    val tiles = buildList<@Composable (Modifier) -> Unit> {
        add { tileModifier ->
            PartnerSleepTile(
                activeSleep = activeSleep,
                lastCompletedSleep = lastCompletedSleep,
                lastSyncAt = snapshot.lastSyncAt,
                now = now,
                onClick = onOpenSleepSheet,
                modifier = tileModifier,
            )
        }
        add { tileModifier ->
            PartnerBreastfeedingTile(
                activeSession = activeSession,
                lastFeeding = lastFeeding,
                lastSyncAt = snapshot.lastSyncAt,
                now = now,
                onClick = onNavigateToFeedHistory,
                modifier = tileModifier,
            )
        }
        add { tileModifier ->
            PartnerBottleTile(
                lastBottle = lastBottle,
                now = now,
                volumeUnit = volumeUnit,
                onClick = onLogBottle,
                modifier = tileModifier,
            )
        }
        if (showInventory) {
            add { tileModifier ->
                PartnerInventoryTile(
                    totalMl = inventoryTotalMl,
                    bagCount = inventoryBagCount,
                    updatedAtMs = snapshot.inventoryUpdatedAt,
                    now = now,
                    volumeUnit = volumeUnit,
                    modifier = tileModifier,
                )
            }
        }
        if (snapshot.diapers.isNotEmpty()) {
            add { tileModifier -> PartnerDiaperTile(diapers = snapshot.diapers, now = now, modifier = tileModifier) }
        }
        if (snapshot.growth.isNotEmpty()) {
            add { tileModifier -> PartnerGrowthTile(growth = snapshot.growth, modifier = tileModifier) }
        }
        if (snapshot.milestones.isNotEmpty()) {
            add { tileModifier -> PartnerMilestoneTile(milestones = snapshot.milestones, modifier = tileModifier) }
        }
        if (snapshot.doctorVisits.isNotEmpty()) {
            add { tileModifier -> PartnerDoctorVisitTile(visits = snapshot.doctorVisits, modifier = tileModifier) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                rowTiles.forEach { tile ->
                    Box(modifier = Modifier.weight(1f)) { tile(Modifier.fillMaxWidth()) }
                }
                if (rowTiles.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PartnerSleepTile(
    activeSleep: SleepSnapshot?,
    lastCompletedSleep: SleepSnapshot?,
    lastSyncAt: Instant,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = activeSleep != null
    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "partnerSleepContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "partnerSleepContent",
    )
    val elevation by animateDpAsState(
        targetValue = if (isActive) 6.dp else 1.dp,
        animationSpec = tween(durationMillis = 240, easing = EaseOutQuart),
        label = "partnerSleepElevation",
    )
    val context = LocalContext.current
    HomeTrackerTile(
        title = stringResource(R.string.home_sleep_title),
        contentDescription = stringResource(R.string.home_sleep_content_description),
        containerColor = containerColor,
        contentColor = contentColor,
        onClick = onClick,
        icon = { SleepIcon(modifier = it) },
        modifier = modifier,
        minHeight = if (isActive) 188.dp else 168.dp,
        elevation = elevation,
        iconSize = if (isActive) 68.dp else 60.dp,
        titleStyle = if (isActive) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        trailing = {
            AnimatedVisibility(
                visible = isActive,
                enter = fadeIn(tween(180, easing = EaseOutQuart)) +
                    scaleIn(initialScale = 0.82f, animationSpec = tween(180, easing = EaseOutQuart)),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.82f, animationSpec = tween(120)),
            ) {
                ActiveStatusBadge(
                    paused = false,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
    ) {
        if (activeSleep != null) {
            val elapsed = remember(activeSleep.startTime, lastSyncAt, now) {
                activeSleepElapsedDuration(sleep = activeSleep, lastSyncAt = lastSyncAt, now = now)
            }
            Text(
                text = stringResource(R.string.partner_sleeping_when_shared),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
            Text(
                text = elapsed.formatDuration(),
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
            )
            Text(
                text = stringResource(R.string.partner_estimate),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
        } else if (lastCompletedSleep != null) {
            HomeTileStatusText(
                text = "${lastCompletedSleep.sleepVerb(context)} ${lastCompletedSleep.sleepAgoText(now, context)}",
                color = contentColor,
            )
        } else {
            HomeTileStatusText(
                text = stringResource(R.string.home_sleep_ready),
                color = contentColor,
            )
        }
    }
}

@Composable
private fun PartnerBreastfeedingTile(
    activeSession: SessionSnapshot?,
    lastFeeding: SessionSnapshot?,
    lastSyncAt: Instant,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = activeSession != null
    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "partnerFeedContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "partnerFeedContent",
    )
    val elevation by animateDpAsState(
        targetValue = if (isActive) 6.dp else 1.dp,
        animationSpec = tween(durationMillis = 240, easing = EaseOutQuart),
        label = "partnerFeedElevation",
    )
    val context = LocalContext.current
    HomeTrackerTile(
        title = stringResource(R.string.home_breastfeeding_title),
        contentDescription = stringResource(R.string.home_breastfeeding_content_description),
        containerColor = containerColor,
        contentColor = contentColor,
        onClick = onClick,
        icon = { BreastfeedingIcon(modifier = it) },
        modifier = modifier,
        minHeight = if (isActive) 188.dp else 168.dp,
        elevation = elevation,
        iconSize = if (isActive) 68.dp else 60.dp,
        titleStyle = if (isActive) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        trailing = {
            AnimatedVisibility(
                visible = isActive,
                enter = fadeIn(tween(180, easing = EaseOutQuart)) +
                    scaleIn(initialScale = 0.82f, animationSpec = tween(180, easing = EaseOutQuart)),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.82f, animationSpec = tween(120)),
            ) {
                ActiveStatusBadge(
                    paused = false,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) {
        if (activeSession != null) {
            val elapsed = remember(activeSession.startTime, activeSession.pausedDurationMs, lastSyncAt, now) {
                activeSessionElapsedDuration(session = activeSession, lastSyncAt = lastSyncAt, now = now)
            }
            Text(
                text = stringResource(R.string.partner_feeding_when_shared),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
            Text(
                text = elapsed.formatDuration(),
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
            )
            Text(
                text = feedingSideLabel(activeSession, context),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.partner_estimate),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
        } else if (lastFeeding != null) {
            HomeTileStatusText(
                text = stringResource(R.string.partner_fed, lastFeeding.feedingAgoText(now, context)),
                color = contentColor,
            )
        } else {
            HomeTileStatusText(
                text = stringResource(R.string.partner_no_feeding_yet),
                color = contentColor,
            )
        }
    }
}

@Composable
private fun PartnerBottleTile(
    lastBottle: BottleFeedSnapshot?,
    now: Instant,
    volumeUnit: VolumeUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    HomeTrackerTile(
        title = stringResource(R.string.partner_log_bottle),
        contentDescription = stringResource(R.string.home_bottle_feed_content_description),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = onClick,
        icon = { BottleFeedIcon(modifier = it) },
        modifier = modifier,
        minHeight = 140.dp,
    ) {
        if (lastBottle != null) {
            val ago = remember(lastBottle.timestamp, now, context) {
                Duration.between(Instant.ofEpochMilli(lastBottle.timestamp), now)
                    .coerceAtLeast(Duration.ZERO)
                    .formatElapsedAgo(context)
            }
            HomeTileStatusText(
                text = "${formatVolume(lastBottle.volumeMl, volumeUnit)} · ${ago.lowercaseFirstChar()}",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            HomeTileStatusText(
                text = stringResource(R.string.home_tap_to_log),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PartnerInventoryTile(
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

    HomeTrackerTile(
        title = stringResource(R.string.partner_milk_stash),
        contentDescription = stashSummary + updatedSuffix,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = null,
        icon = { InventoryIcon(modifier = it) },
        modifier = modifier,
        minHeight = 140.dp,
    ) {
        HomeTileStatusText(
            text = pluralStringResource(R.plurals.partner_stash_summary, bagCount, volumeText, bagCount),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun PartnerDiaperTile(
    diapers: List<com.babytracker.sharing.domain.model.DiaperSnapshot>,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val diaper = diaperColors()
    val zone = remember { ZoneId.systemDefault() }
    // Drive "today" from the dashboard's ticking `now`; keying on the resolved local date keeps the
    // count from recomputing every minute, but rolls it over when the screen stays open past midnight.
    val today = remember(now, zone) { now.atZone(zone).toLocalDate() }
    val todayCount = remember(diapers, today, zone) { diaperCountForDay(diapers, today, zone) }
    val countText = pluralStringResource(R.plurals.partner_diaper_count_today, todayCount, todayCount)
    HomeTrackerTile(
        title = stringResource(R.string.home_diaper_title),
        contentDescription = "${stringResource(R.string.home_diaper_title)}, $countText",
        containerColor = diaper.container,
        contentColor = diaper.onContainer,
        onClick = null,
        icon = { DiaperIcon(modifier = it) },
        modifier = modifier,
        minHeight = 140.dp,
    ) {
        HomeTileStatusText(text = countText, color = diaper.onContainer)
    }
}

@Composable
private fun PartnerGrowthTile(
    growth: List<com.babytracker.sharing.domain.model.GrowthSnapshot>,
    modifier: Modifier = Modifier,
) {
    val colors = growthColors()
    val context = LocalContext.current
    // Latest value for every measurement category, matching what the old summary card surfaced — the
    // tile is read-only, so dropping categories would hide data the partner can no longer reach.
    val lines = remember(growth, context) {
        GrowthType.entries.mapNotNull { type ->
            val latest = growth.filter { it.type == type.name }.maxByOrNull { it.takenAtMs }
            latest?.let {
                context.getString(
                    R.string.partner_growth_label,
                    type.partnerLabel(context),
                    formatGrowthValue(type, it.valueCanonical),
                )
            }
        }
    }
    HomeTrackerTile(
        title = stringResource(R.string.partner_growth),
        contentDescription = (listOf(stringResource(R.string.partner_growth)) + lines).joinToString(", "),
        containerColor = colors.container,
        contentColor = colors.onContainer,
        onClick = null,
        icon = { GrowthIcon(modifier = it) },
        modifier = modifier,
        minHeight = 140.dp,
    ) {
        lines.forEach { HomeTileStatusText(text = it, color = colors.onContainer, maxLines = 1) }
    }
}

@Composable
private fun PartnerMilestoneTile(
    milestones: List<com.babytracker.sharing.domain.model.MilestoneSnapshot>,
    modifier: Modifier = Modifier,
) {
    val colors = milestoneColors()
    val latest = remember(milestones) { milestones.maxByOrNull { it.dateEpochDay } }
    HomeTrackerTile(
        title = stringResource(R.string.partner_milestones),
        contentDescription = listOfNotNull(stringResource(R.string.partner_milestones), latest?.title).joinToString(", "),
        containerColor = colors.container,
        contentColor = colors.onContainer,
        onClick = null,
        icon = { MilestoneIcon(modifier = it) },
        modifier = modifier,
        minHeight = 140.dp,
    ) {
        latest?.let {
            HomeTileStatusText(text = it.title, color = colors.onContainer)
        }
    }
}

private val tileVisitDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

@Composable
private fun PartnerDoctorVisitTile(
    visits: List<com.babytracker.sharing.domain.model.DoctorVisitSnapshot>,
    modifier: Modifier = Modifier,
) {
    val colors = doctorVisitColors()
    val zone = remember { ZoneId.systemDefault() }
    val shown = remember(visits) {
        val nowMs = Instant.now().toEpochMilli()
        visits.filter { it.date > nowMs }.minByOrNull { it.date }
            ?: visits.maxByOrNull { it.date }
    }
    val line = shown?.let { visit ->
        val dateLabel = tileVisitDateFormatter.format(Instant.ofEpochMilli(visit.date).atZone(zone).toLocalDate())
        visit.providerName?.takeIf { it.isNotBlank() }?.let { "$dateLabel · $it" } ?: dateLabel
    }
    HomeTrackerTile(
        title = stringResource(R.string.doctor_visit_tile_label),
        contentDescription = listOfNotNull(stringResource(R.string.doctor_visit_tile_label), line).joinToString(", "),
        containerColor = colors.container,
        contentColor = colors.onContainer,
        onClick = null,
        icon = { DoctorVisitIcon(modifier = it) },
        modifier = modifier,
        minHeight = 140.dp,
    ) {
        if (line != null) {
            HomeTileStatusText(text = line, color = colors.onContainer)
        } else {
            HomeTileStatusText(
                text = stringResource(R.string.partner_doctor_visits_empty),
                color = colors.onContainer,
            )
        }
    }
}

// --- Sleep action sheet -----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartnerSleepSheet(
    state: PartnerSleepUiState,
    onStartNap: () -> Unit,
    onStartNightSleep: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onViewHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.home_sleep_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            PartnerSleepControls(
                state = state,
                onStartNap = onStartNap,
                onStartNightSleep = onStartNightSleep,
                onStop = onStop,
                onEdit = onEdit,
                onViewHistory = onViewHistory,
            )
        }
    }
}

// --- Sync status strip ------------------------------------------------------------------------

@Composable
private fun PartnerSyncStrip(
    activeSession: SessionSnapshot?,
    activeSleep: SleepSnapshot?,
    lastSharedText: String,
    lastCheckedText: String?,
    isShareStale: Boolean,
    error: String?,
    onClearError: () -> Unit,
) {
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = dashboardPanelElevation()),
        border = dashboardPanelBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clearAndSetSemantics {},
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.partner_shared_ago, lastSharedText.lowercaseFirstChar()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (lastCheckedText != null) {
                        Text(
                            text = stringResource(R.string.partner_checked_ago, lastCheckedText.lowercaseFirstChar()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isShareStale) {
                SyncWarning()
            }

            if (error != null) {
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

// --- Timeline sections ------------------------------------------------------------------------

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
            DashboardSection(title = stringResource(R.string.partner_recent_bottles)) {
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
        badgeColor = MaterialTheme.colorScheme.primaryContainer,
        badgeContent = { BreastfeedingIcon(modifier = Modifier.size(34.dp)) },
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
        badgeColor = MaterialTheme.colorScheme.primaryContainer,
        badgeContent = { BottleFeedIcon(modifier = Modifier.size(34.dp)) },
        trailingColor = MaterialTheme.colorScheme.primary,
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
        badgeColor = MaterialTheme.colorScheme.secondaryContainer,
        badgeContent = {
            if (sleep.sleepType == "NIGHT_SLEEP") {
                SleepIcon(modifier = Modifier.size(34.dp))
            } else {
                NapIcon(modifier = Modifier.size(34.dp))
            }
        },
        trailingColor = MaterialTheme.colorScheme.secondary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
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
                    val allergyType = AllergyType.entries.find { it.name == allergyName }
                    val label = allergyType?.let { stringResource(it.labelRes()) } ?: allergyName
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
            text = "👶",
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

// --- shared helpers (kept stable for unit tests) ----------------------------------------------

private fun feedingSideLabel(session: SessionSnapshot, context: Context): String {
    val started = context.getString(
        if (session.startingSide == "LEFT") R.string.side_left else R.string.side_right,
    )
    return if (session.switchTime != null) {
        val current = context.getString(
            if (session.startingSide == "LEFT") R.string.side_right else R.string.side_left,
        )
        context.getString(R.string.partner_side_switch, started, current)
    } else {
        context.getString(R.string.partner_side_single, started)
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

private fun String.lowercaseFirstChar(): String =
    replaceFirstChar { it.lowercase() }

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

internal fun diaperCountForDay(
    diapers: List<com.babytracker.sharing.domain.model.DiaperSnapshot>,
    day: LocalDate,
    zone: ZoneId,
): Int =
    diapers.count { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == day }

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
