package com.babytracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.ui.breastfeeding.PredictionCopy
import com.babytracker.util.formatDuration
import com.babytracker.util.formatElapsedAgo
import com.babytracker.util.formatMinutesSeconds
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onNavigateToBreastfeeding,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                    ) {
                        Text("Feeding", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onNavigateToSleep,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                    ) {
                        Text("Sleep", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            val activeSession = uiState.activeSession
            val activeSleepRecord = uiState.activeSleepRecord

            // Summary cards — 2×2 grid
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Breastfeeding card
                    val isActiveFeeding = activeSession != null
                    val feedingElevation by animateDpAsState(
                        targetValue = if (isActiveFeeding) 4.dp else 1.dp,
                        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                        label = "feedingElevation",
                    )
                    Card(
                        onClick = onNavigateToBreastfeeding,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 140.dp)
                            .semantics {
                                contentDescription = if (isActiveFeeding)
                                    "Breastfeeding, session active. Open feeding screen."
                                else
                                    "Breastfeeding. Open feeding screen."
                            },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = feedingElevation),
                    ) {
                        Column(modifier = Modifier.padding(20.dp).animateContentSize(animationSpec = tween(200, easing = LinearOutSlowInEasing))) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "🍼",
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.clearAndSetSemantics {},
                                )
                                AnimatedVisibility(
                                    visible = activeSession != null,
                                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.7f, animationSpec = tween(150)),
                                    exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.7f, animationSpec = tween(100)),
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.extraLarge,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (activeSession?.isPaused == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = if (activeSession?.isPaused == true) "Paused" else "Live",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Breastfeeding",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            if (activeSession != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                ActiveFeedingTimer(session = activeSession)
                            } else {
                                val lastStart = uiState.lastSessionStartTime
                                if (lastStart != null) {
                                    LastFeedingAgoText(lastStart = lastStart)
                                }
                                uiState.nextFeedPrediction?.let { prediction ->
                                    FeedingPredictionSubtitle(prediction = prediction)
                                }
                            }
                        }
                    }

                    // Sleep card
                    val isActiveSleep = activeSleepRecord != null
                    val sleepElevation by animateDpAsState(
                        targetValue = if (isActiveSleep) 4.dp else 1.dp,
                        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                        label = "sleepElevation",
                    )
                    Card(
                        onClick = onNavigateToSleep,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 140.dp)
                            .semantics {
                                contentDescription = if (isActiveSleep)
                                    "Sleep, session active. Open sleep screen."
                                else
                                    "Sleep. Open sleep screen."
                            },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = sleepElevation),
                    ) {
                        Column(modifier = Modifier.padding(20.dp).animateContentSize(animationSpec = tween(200, easing = LinearOutSlowInEasing))) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "🌙",
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.clearAndSetSemantics {},
                                )
                                AnimatedVisibility(
                                    visible = isActiveSleep,
                                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.7f, animationSpec = tween(150)),
                                    exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.7f, animationSpec = tween(100)),
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = MaterialTheme.shapes.extraLarge,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondary,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = "Live",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sleep",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            if (activeSleepRecord != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                ActiveSleepTimer(record = activeSleepRecord)
                            } else {
                                val lastEnd = uiState.lastSleepEndTime
                                if (lastEnd != null) {
                                    LastSleepAgoText(endTime = lastEnd)
                                } else {
                                    Text(
                                        text = "No sleep recorded yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                                val nightLabel = uiState.lastNightSleepDuration
                                    ?.formatDuration()
                                    ?.let { "$it last night" }
                                if (nightLabel != null) {
                                    Text(
                                        text = nightLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PumpingHomeCard(
                        active = uiState.pumpingActive,
                        onClick = onNavigateToPumping,
                        modifier = Modifier.weight(1f),
                    )
                    InventoryHomeCard(
                        summary = uiState.inventorySummary,
                        onClick = onNavigateToInventory,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Tip card — suggests which side to try next (based on the less-used side last session)
            AnimatedVisibility(
                visible = uiState.nextRecommendedSide != null,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            ) {
                val nextSideName = uiState.nextRecommendedSide
                    ?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
                Card(
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✨",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                text = "TIP",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Try $nextSideName breast next, used less last session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.appMode == AppMode.NONE,
                enter = fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 150)),
            ) {
                Card(
                    onClick = onNavigateToConnectPartner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Partner View. Connect to see shared baby data."
                        },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Partner View",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Connect to see shared baby data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
        }
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
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
    val pumpingElevation by animateDpAsState(
        targetValue = if (isPumping) 4.dp else 1.dp,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "pumpingElevation",
    )
    Card(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 140.dp)
            .semantics {
                contentDescription = if (isPumping)
                    "Pumping, session active. Open pumping screen."
                else
                    "Pumping. Open pumping screen."
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = pumpingElevation),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(animationSpec = tween(200, easing = LinearOutSlowInEasing)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "🥛",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                AnimatedVisibility(
                    visible = isPumping,
                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.7f, animationSpec = tween(150)),
                    exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.7f, animationSpec = tween(100)),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = if (active?.isPaused == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                text = if (active?.isPaused == true) "Paused" else "Live",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pumping",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (active != null) {
                ActivePumpingTimer(session = active)
            } else {
                Text(
                    text = "Tap to log",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun InventoryHomeCard(
    summary: InventorySummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasBags = summary.bagCount > 0
    Card(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 140.dp)
            .semantics {
                contentDescription = if (hasBags)
                    "Milk inventory, ${summary.bagCount} bags, ${summary.totalMl} milliliters. Open inventory screen."
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
                .animateContentSize(animationSpec = tween(200, easing = LinearOutSlowInEasing)),
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
                text = if (hasBags) "${summary.totalMl} mL · ${summary.bagCount} bags" else "No bags stored",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivePumpingTimer(session: PumpingSession) {
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
        color = MaterialTheme.colorScheme.onTertiaryContainer,
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
private fun ActiveFeedingTimer(session: BreastfeedingSession) {
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
        color = MaterialTheme.colorScheme.onPrimaryContainer,
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
private fun LastFeedingAgoText(lastStart: Instant) {
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
private fun LastSleepAgoText(endTime: Instant) {
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
private fun ActiveSleepTimer(record: SleepRecord) {
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
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
