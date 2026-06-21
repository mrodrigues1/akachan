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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.DoctorVisitSummary
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.TodayDiaperSummary
import com.babytracker.domain.model.TodayFeedingSummary
import com.babytracker.domain.model.VaccineSummary
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.ui.breastfeeding.PredictionCopy
import com.babytracker.ui.breastfeeding.contentDescriptionText
import com.babytracker.ui.breastfeeding.detailText
import com.babytracker.ui.breastfeeding.primaryText
import com.babytracker.ui.component.BottleFeedIcon
import com.babytracker.ui.component.DiaperIcon
import com.babytracker.ui.component.InventoryIcon
import com.babytracker.ui.component.PumpingIcon
import com.babytracker.ui.component.labelRes
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark
import com.babytracker.ui.theme.diaperColors
import com.babytracker.ui.theme.doctorVisitColors
import com.babytracker.ui.theme.growthColors
import com.babytracker.ui.theme.milestoneColors
import com.babytracker.ui.theme.vaccineColors
import com.babytracker.util.formatDuration
import com.babytracker.util.formatElapsedAgo
import com.babytracker.util.formatMinutesSeconds
import com.babytracker.util.formatTime
import com.babytracker.util.formatVolume
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
    onNavigateToDiaper: () -> Unit = {},
    onNavigateToVaccine: () -> Unit = {},
    onNavigateToDoctorVisit: () -> Unit = {},
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
                            text = uiState.baby?.let { stringResource(R.string.home_greeting, it.name) }
                                ?: stringResource(R.string.app_name),
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
                            contentDescription = stringResource(R.string.home_settings_content_description),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.tileOrder != HomeTile.DEFAULT_ORDER) {
                        Box {
                            var menuExpanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_options),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.home_reset_layout)) },
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
            onNavigateToDiaper,
            onNavigateToVaccine,
            onNavigateToDoctorVisit,
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
                onDiaper = onNavigateToDiaper,
                onVaccine = onNavigateToVaccine,
                onDoctorVisit = onNavigateToDoctorVisit,
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
    val timeLabel = remember(prediction) { prediction.predictedAt.formatTime() }
    val primaryText = subtitle.primaryText(timeLabel)
    val detailText = subtitle.detailText()
    val subtitleDescription = subtitle.contentDescriptionText(timeLabel)
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
                contentDescription = subtitleDescription
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
                text = primaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = primaryColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detailText.isNotEmpty()) {
                Text(
                    text = detailText,
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
    val pumpingDescription = if (isPumping) {
        stringResource(R.string.home_pumping_active_content_description)
    } else {
        stringResource(R.string.home_pumping_content_description)
    }
    val containerColor by animateColorAsState(
        targetValue = if (isPumping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "pumpingContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isPumping) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
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
            .heightIn(min = 96.dp)
            .semantics {
                contentDescription = pumpingDescription
        },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = pumpingElevation),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                PumpingIcon(modifier = Modifier.size(40.dp))
                AnimatedVisibility(
                    visible = isPumping,
                    enter = fadeIn(tween(180, easing = EaseOutQuart)) +
                        scaleIn(initialScale = 0.82f, animationSpec = tween(180, easing = EaseOutQuart)),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.82f, animationSpec = tween(120)),
                ) {
                    ActiveStatusBadge(
                        paused = active?.isPaused == true,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_pumping_title),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (active != null) {
                ActivePumpingTimer(session = active, color = contentColor)
            } else {
                Text(
                    text = stringResource(R.string.home_tap_to_log),
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
    val inventoryDescription = if (hasBags) {
        pluralStringResource(
            R.plurals.home_inventory_content_description,
            summary.bagCount,
            summary.bagCount,
            volumeText,
        )
    } else {
        stringResource(R.string.home_inventory_empty_content_description)
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics {
                contentDescription = inventoryDescription
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            InventoryIcon(modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_inventory_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasBags) {
                    pluralStringResource(
                        R.plurals.home_inventory_summary,
                        summary.bagCount,
                        volumeText,
                        summary.bagCount,
                    )
                } else {
                    stringResource(R.string.home_inventory_empty)
                },
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
    val bottleFeedDescription = stringResource(R.string.home_bottle_feed_content_description)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics { contentDescription = bottleFeedDescription },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            BottleFeedIcon(modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.bottle_feed_quick_action),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_tap_to_log),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
internal fun DiaperHomeCard(
    summary: TodayDiaperSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diaper = diaperColors()
    val countText = pluralStringResource(R.plurals.home_diaper_count_today, summary.count, summary.count)
    val diaperCd = if (summary.hasAny) {
        stringResource(R.string.home_diaper_cd, countText)
    } else {
        stringResource(R.string.home_diaper_cd_empty)
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics {
                contentDescription = diaperCd
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = diaper.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            DiaperIcon(modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_diaper_title),
                style = MaterialTheme.typography.titleMedium,
                color = diaper.onContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (summary.hasAny || summary.lastChangeAt != null) {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = diaper.onContainer,
                )
                summary.lastChangeAt?.let { LastDiaperAgoText(it) }
            } else {
                Text(
                    text = stringResource(R.string.home_tap_to_log),
                    style = MaterialTheme.typography.bodyMedium,
                    color = diaper.onContainer,
                )
            }
        }
    }
}

@Composable
internal fun LastDiaperAgoText(lastChangeAt: Instant) {
    val now by produceState(initialValue = Instant.now(), key1 = lastChangeAt) {
        while (true) {
            delay(60_000L)
            value = Instant.now()
        }
    }
    Text(
        text = stringResource(
            R.string.home_diaper_last_ago,
            Duration.between(lastChangeAt, now).formatElapsedAgo(LocalContext.current),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = diaperColors().onContainer,
    )
}

@Composable
internal fun VaccineHomeCard(
    summary: VaccineSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vaccine = vaccineColors()
    val isDark = LocalDarkTheme.current
    val overdue = summary.overdueCount > 0
    val next = summary.nextUpcoming
    val last = summary.lastAdministered

    // Overdue borrows the warning-amber surface; otherwise the section Indigo.
    val containerColor = when {
        !overdue -> vaccine.container
        isDark -> WarningContainerAmberDark
        else -> WarningContainerAmber
    }
    val contentColor = when {
        !overdue -> vaccine.onContainer
        isDark -> OnWarningContainerAmberDark
        else -> OnWarningContainerAmber
    }

    val title = stringResource(R.string.vaccine_tile_label)
    val subtitle = when {
        overdue -> stringResource(R.string.vaccine_tile_overdue)
        next != null -> stringResource(R.string.vaccine_tile_next, next.name)
        last != null -> stringResource(R.string.vaccine_tile_last_given, last.name)
        else -> stringResource(R.string.vaccine_tile_none)
    }
    val countdown = next?.scheduledDate?.takeIf { !overdue }?.let { daysUntilLabel(it) }
    val cardCd = listOfNotNull(title, subtitle, countdown).joinToString(", ")

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics { contentDescription = cardCd },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Text(
                text = "💉",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            countdown?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
        }
    }
}

private val visitDateFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
internal fun DoctorVisitHomeCard(
    summary: DoctorVisitSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = doctorVisitColors()
    val zone = ZoneId.systemDefault()
    val next = summary.nextUpcoming
    val last = summary.lastPast

    val title = stringResource(R.string.doctor_visit_tile_label)
    val subtitle = when {
        next != null -> {
            val days = ChronoUnit.DAYS.between(LocalDate.now(zone), next.date.atZone(zone).toLocalDate())
            if (days <= 0L) {
                stringResource(R.string.doctor_visit_tile_today)
            } else {
                pluralStringResource(R.plurals.doctor_visit_tile_in_days, days.toInt(), days.toInt())
            }
        }
        last != null -> stringResource(
            R.string.doctor_visit_tile_last,
            visitDateFormatter.format(last.date.atZone(zone).toLocalDate()),
        )
        else -> stringResource(R.string.doctor_visit_tile_empty)
    }
    val provider = (next ?: last)?.providerName?.takeIf { it.isNotBlank() }
    val questionsLine = summary.openQuestionCount.takeIf { it > 0 }?.let {
        pluralStringResource(R.plurals.doctor_visit_tile_open_questions, it, it)
    }
    val cardCd = listOfNotNull(title, subtitle, provider, questionsLine).joinToString(", ")

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics { contentDescription = cardCd },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Text(
                text = "🩺",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.onContainer)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onContainer)
            provider?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.onContainer)
            }
            questionsLine?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.onContainer)
            }
        }
    }
}

@Composable
private fun daysUntilLabel(scheduledDate: Instant): String {
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(LocalDate.now(zone), scheduledDate.atZone(zone).toLocalDate())
    return if (days <= 0L) {
        stringResource(R.string.vaccine_tile_today)
    } else {
        pluralStringResource(R.plurals.vaccine_tile_in_days, days.toInt(), days.toInt())
    }
}

@Composable
internal fun GrowthHomeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val growth = growthColors()
    val growthDescription = stringResource(R.string.home_growth_content_description)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .semantics { contentDescription = growthDescription },
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
                text = stringResource(R.string.home_growth_title),
                style = MaterialTheme.typography.titleMedium,
                color = growth.onContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_growth_subtitle),
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
    val growth = growthColors()
    val trendsDescription = stringResource(R.string.home_trends_content_description)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .semantics { contentDescription = trendsDescription },
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
                text = "📊",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_trends_title),
                style = MaterialTheme.typography.titleMedium,
                color = growth.onContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_trends_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = growth.onContainer,
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
    val milestonesDescription = stringResource(R.string.home_milestones_content_description)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .semantics { contentDescription = milestonesDescription },
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
                text = stringResource(R.string.home_milestones_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_milestones_subtitle),
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
    val summaryText = when {
        !summary.hasAny -> stringResource(R.string.home_feeding_empty)
        summary.bottleVolumeMl > 0 ->
            pluralStringResource(
                R.plurals.home_feeding_summary_volume,
                summary.totalFeedCount,
                formatVolume(summary.bottleVolumeMl, volumeUnit),
                summary.totalFeedCount,
            )
        else ->
            pluralStringResource(
                R.plurals.home_feeding_summary_count,
                summary.totalFeedCount,
                summary.totalFeedCount,
            )
    }
    val feedingHistoryDescription =
        stringResource(R.string.home_feeding_history_content_description, summaryText)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics {
                contentDescription = feedingHistoryDescription
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Text(
                text = "📋",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.feeding_history_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                text = if (paused) stringResource(R.string.status_paused) else stringResource(R.string.status_live),
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
    val context = LocalContext.current
    val now by produceState(initialValue = Instant.now(), key1 = lastStart) {
        while (true) {
            delay(60_000L)
            value = Instant.now()
        }
    }
    Text(
        text = Duration.between(lastStart, now).formatElapsedAgo(context),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
internal fun LastSleepAgoText(endTime: Instant) {
    val context = LocalContext.current
    val now by produceState(initialValue = Instant.now(), key1 = endTime) {
        while (true) {
            delay(60_000L)
            value = Instant.now()
        }
    }
    Text(
        text = Duration.between(endTime, now).formatElapsedAgo(context),
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
