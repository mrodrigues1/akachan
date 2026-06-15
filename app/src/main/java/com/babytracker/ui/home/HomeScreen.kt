package com.babytracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.TodayFeedingSummary
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.ui.breastfeeding.PredictionCopy
import com.babytracker.ui.theme.growthColors
import com.babytracker.ui.theme.milestoneColors
import com.babytracker.util.formatDuration
import com.babytracker.util.formatElapsedAgo
import com.babytracker.util.formatMinutesSeconds
import com.babytracker.util.formatVolume
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

internal val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBreastfeeding: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToConnectPartner: () -> Unit = {},
    onNavigateToPumping: () -> Unit = {},
    onNavigateToInventory: () -> Unit = {},
    onNavigateToBottleFeed: () -> Unit = {},
    onNavigateToFeedingHistory: () -> Unit = {},
    onNavigateToGrowth: () -> Unit = {},
    onNavigateToMilestones: () -> Unit = {},
    onNavigateToTrends: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val todayLabel = remember {
        Instant.now()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.baby?.let { "Hi, ${it.name} 👋" } ?: "Akachan",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = todayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.tileOrder != HomeTile.DEFAULT_ORDER) {
                        Box {
                            var menuExpanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Reset layout") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.onResetTileOrder()
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
    ) { padding ->
        val callbacks = remember(
            onNavigateToBreastfeeding,
            onNavigateToSleep,
            onNavigateToPumping,
            onNavigateToInventory,
            onNavigateToBottleFeed,
            onNavigateToFeedingHistory,
            onNavigateToConnectPartner,
            onNavigateToGrowth,
            onNavigateToMilestones,
        ) {
            HomeTileCallbacks(
                onBreastfeeding = onNavigateToBreastfeeding,
                onSleep = onNavigateToSleep,
                onPumping = onNavigateToPumping,
                onInventory = onNavigateToInventory,
                onBottleFeed = onNavigateToBottleFeed,
                onFeedingHistory = onNavigateToFeedingHistory,
                onConnectPartner = onNavigateToConnectPartner,
                onGrowth = onNavigateToGrowth,
                onMilestones = onNavigateToMilestones,
                onTrends = onNavigateToTrends,
            )
        }
        HomeContent(
            uiState = uiState,
            callbacks = callbacks,
            onReorder = viewModel::onTilesReordered,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
internal fun FeedingPredictionSubtitle(
    prediction: FeedPrediction,
    modifier: Modifier = Modifier,
) {
    val subtitle = remember(prediction) { PredictionCopy.forPrediction(prediction) }
    val primaryColor = if (prediction.isOverdue && prediction.minutesUntil <= -5)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = subtitle.contentDescription
            },
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subtitle.primary,
                style = MaterialTheme.typography.bodyMedium,
                color = primaryColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = buildString {
                subtitle.secondary?.let { append(it) }
                if (subtitle.lowConfidence) {
                    if (isNotEmpty()) append(" · ")
                    append("low confidence")
                }
            }
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun PumpingHomeCard(
    active: PumpingSession?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPumping = active != null
    val containerColor by animateColorAsState(
        targetValue = if (isPumping) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiaryContainer,
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "pumpingContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isPumping) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer,
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "pumpingContentColor",
    )
    val pumpingElevation by animateDpAsState(
        targetValue = if (isPumping) 6.dp else 1.dp,
        animationSpec = tween(durationMillis = 240, easing = EaseOutQuart),
        label = "pumpingElevation",
    )
    Card(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 120.dp)
            .semantics {
                contentDescription = if (isPumping)
                    "Pumping, session active. Open pumping screen."
                else
                    "Pumping. Open pumping screen."
        },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = pumpingElevation),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "🥛",
                    style = MaterialTheme.typography.headlineMedium,
                    color = contentColor,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                AnimatedVisibility(
                    visible = isPumping,
                    enter = fadeIn(tween(180, easing = EaseOutQuart)) +
                        scaleIn(initialScale = 0.82f, animationSpec = tween(180, easing = EaseOutQuart)),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.82f, animationSpec = tween(120)),
                ) {
                    ActiveStatusBadge(
                        paused = active?.isPaused == true,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pumping",
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (active != null) {
                ActivePumpingTimer(session = active, color = contentColor)
            } else {
                Text(
                    text = "Tap to log",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
internal fun InventoryHomeCard(
    summary: InventorySummary,
    volumeUnit: VolumeUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasBags = summary.bagCount > 0
    val volumeText = formatVolume(summary.totalMl, volumeUnit)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .semantics {
                contentDescription = if (hasBags)
                    "Milk inventory, ${summary.bagCount} bags, $volumeText. Open inventory screen."
                else
                    "Milk inventory, no bags stored. Open inventory screen."
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Text(
                text = "🧊",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Inventory",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasBags) "$volumeText · ${summary.bagCount} bags" else "No bags stored",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun BottleFeedHomeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .semantics { contentDescription = "Log a bottle feed. Open bottle feed screen." },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Text(
                text = "🍼",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bottle feed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to log",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
internal fun GrowthHomeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val growth = growthColors()
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .semantics { contentDescription = "Growth tracking. Open growth charts." },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = growth.container,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Text(
                text = "📈",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Growth",
                style = MaterialTheme.typography.titleMedium,
                color = growth.onContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Weight, length & percentiles",
                style = MaterialTheme.typography.bodyMedium,
                color = growth.onContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrendsHomeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .semantics { contentDescription = "Trends. Open feeding and sleep charts." },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Text(
                text = "📊",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Trends",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Feeding & sleep patterns",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
internal fun MilestonesHomeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = milestoneColors()
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .semantics { contentDescription = "Milestones. Capture special moments." },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = colors.container,
            contentColor = colors.onContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Text(
                text = "🎉",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Milestones",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Capture your baby's special moments",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onContainer,
            )
        }
    }
}

@Composable
internal fun FeedingHistoryHomeCard(
    summary: TodayFeedingSummary,
    volumeUnit: VolumeUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedsLabel = if (summary.totalFeedCount == 1) "feed" else "feeds"
    val summaryText = when {
        !summary.hasAny -> "No feeds today"
        summary.bottleVolumeMl > 0 ->
            "${formatVolume(summary.bottleVolumeMl, volumeUnit)} · ${summary.totalFeedCount} $feedsLabel today"
        else -> "${summary.totalFeedCount} $feedsLabel today"
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .semantics {
                contentDescription = "Feeding history. $summaryText. Open combined feeding history."
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Text(
                text = "📋",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Feeding history",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ActiveStatusBadge(
    paused: Boolean,
    containerColor: Color,
    contentColor: Color,
) {
    val pulseTransition = rememberInfiniteTransition(label = "activeStatusPulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = EaseOutQuart),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "activeStatusDotScale",
    )
    val dotAlpha by pulseTransition.animateFloat(
        initialValue = 0.58f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = EaseOutQuart),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "activeStatusDotAlpha",
    )
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Surface(
                color = contentColor.copy(alpha = if (paused) 1f else dotAlpha),
                shape = CircleShape,
                modifier = Modifier
                    .size(7.dp)
                    .scale(if (paused) 1f else dotScale),
            ) {}
            Icon(
                imageVector = if (paused) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = if (paused) "Paused" else "Live",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ActivePumpingTimer(session: PumpingSession, color: Color) {
    val elapsedSeconds by produceState(
        initialValue = computePumpingElapsed(session, Instant.now()),
        key1 = session,
    ) {
        if (session.isPaused) return@produceState
        while (true) {
            delay(1_000L)
            value = computePumpingElapsed(session, Instant.now())
        }
    }
    Text(
        text = Duration.ofSeconds(elapsedSeconds).formatDuration(),
        style = MaterialTheme.typography.titleLarge,
        color = color,
    )
}

private fun computePumpingElapsed(session: PumpingSession, now: Instant): Long {
    return if (session.isPaused) {
        val pausedAt = session.pausedAt!!
        (pausedAt.toEpochMilli() - session.startTime.toEpochMilli() - session.pausedDurationMs) / 1000
    } else {
        (now.toEpochMilli() - session.startTime.toEpochMilli() - session.pausedDurationMs) / 1000
    }.coerceAtLeast(0L)
}

@Composable
internal fun ActiveFeedingTimer(session: BreastfeedingSession, color: Color) {
    val elapsedSeconds by produceState(
        initialValue = computeFeedingElapsed(session, Instant.now()),
        key1 = session,
    ) {
        if (session.isPaused) return@produceState
        while (true) {
            delay(1_000L)
            value = computeFeedingElapsed(session, Instant.now())
        }
    }
    Text(
        text = elapsedSeconds.formatMinutesSeconds(),
        style = MaterialTheme.typography.titleLarge,
        color = color,
    )
}

private fun computeFeedingElapsed(session: BreastfeedingSession, now: Instant): Long {
    return if (session.isPaused) {
        val pausedAt = session.pausedAt!!
        (pausedAt.toEpochMilli() - session.startTime.toEpochMilli() - session.pausedDurationMs) / 1000
    } else {
        (now.toEpochMilli() - session.startTime.toEpochMilli() - session.pausedDurationMs) / 1000
    }.coerceAtLeast(0L)
}

@Composable
internal fun LastFeedingAgoText(lastStart: Instant) {
    val now by produceState(initialValue = Instant.now(), key1 = lastStart) {
        while (true) {
            delay(60_000L)
            value = Instant.now()
        }
    }
    Text(
        text = Duration.between(lastStart, now).formatElapsedAgo(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
internal fun LastSleepAgoText(endTime: Instant) {
    val now by produceState(initialValue = Instant.now(), key1 = endTime) {
        while (true) {
            delay(60_000L)
            value = Instant.now()
        }
    }
    Text(
        text = Duration.between(endTime, now).formatElapsedAgo(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
internal fun ActiveSleepTimer(record: SleepRecord, color: Color) {
    val elapsedSeconds by produceState(
        initialValue = ((Instant.now().toEpochMilli() - record.startTime.toEpochMilli()) / 1000).coerceAtLeast(0L),
        key1 = record.startTime,
    ) {
        while (true) {
            delay(1_000L)
            value = ((Instant.now().toEpochMilli() - record.startTime.toEpochMilli()) / 1000).coerceAtLeast(0L)
        }
    }
    Text(
        text = Duration.ofSeconds(elapsedSeconds).formatDuration(),
        style = MaterialTheme.typography.titleLarge,
        color = color,
    )
}
