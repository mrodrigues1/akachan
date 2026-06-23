package com.babytracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepRecord
import java.time.Instant
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.ui.component.BreastfeedingIcon
import com.babytracker.ui.component.SleepIcon
import com.babytracker.ui.component.labelRes
import com.babytracker.ui.sleep.SleepPredictionCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

internal fun HomeTile.isFullWidth(): Boolean = when (this) {
    HomeTile.SLEEP_PREDICTION, HomeTile.TIP, HomeTile.PARTNER -> true
    else -> false
}

/**
 * Minimum height floor for a tile in the staggered grid. Full-width tiles size to content.
 * The compact action tiles (pumping, inventory, bottle feed, feeding history) carry only a
 * one-line subtitle, so they get no floor and shrink to their natural height; richer tiles
 * (breastfeeding, sleep timers, growth/milestone copy) keep the taller floor for balance.
 */
internal fun HomeTile.minTileHeightDp(): Dp = when {
    isFullWidth() -> 0.dp
    this == HomeTile.BREASTFEEDING || this == HomeTile.SLEEP -> 168.dp
    this == HomeTile.PUMPING || this == HomeTile.INVENTORY ||
        this == HomeTile.BOTTLE_FEED || this == HomeTile.DIAPER ||
        this == HomeTile.VACCINE || this == HomeTile.DOCTOR_VISIT ||
        this == HomeTile.FEEDING_HISTORY -> 0.dp
    else -> 140.dp
}

/**
 * Move [fromKey] to [toKey]'s slot among the currently [visible] tiles, then merge the result back
 * into the full [order]. [fromKey]/[toKey] are visible tile keys from the drag gesture. Hidden
 * (conditional) tiles act as fixed anchors: they keep their absolute index in [order] regardless of
 * how the visible tiles are dragged, so revealing one later never moves it to an unexpected slot.
 * Returns [order] unchanged if the keys are equal or either is not currently visible.
 */
internal fun reorderByKey(
    order: List<HomeTile>,
    visible: List<HomeTile>,
    fromKey: HomeTile,
    toKey: HomeTile,
): List<HomeTile> {
    if (fromKey == toKey) return order
    val newVisible = visible.toMutableList()
    val fromIdx = newVisible.indexOf(fromKey)
    val toIdx = newVisible.indexOf(toKey)
    if (fromIdx < 0 || toIdx < 0) return order
    newVisible.add(toIdx, newVisible.removeAt(fromIdx))
    val visibleSet = visible.toSet()
    val next = newVisible.iterator()
    return order.map { tile -> if (tile in visibleSet) next.next() else tile }
}

/**
 * The feature a tile belongs to, or null for derived tiles (FEEDING_HISTORY, TRENDS — see
 * [isVisible]) and always-on tiles (PARTNER). Single source of truth for feature gating.
 */
internal fun HomeTile.requiredFeature(): AppFeature? = when (this) {
    HomeTile.BREASTFEEDING -> AppFeature.BREASTFEEDING
    HomeTile.BOTTLE_FEED -> AppFeature.BOTTLE_FEED
    HomeTile.PUMPING -> AppFeature.PUMPING
    HomeTile.INVENTORY -> AppFeature.INVENTORY
    HomeTile.SLEEP, HomeTile.SLEEP_PREDICTION -> AppFeature.SLEEP
    HomeTile.DIAPER -> AppFeature.DIAPERS
    HomeTile.GROWTH -> AppFeature.GROWTH
    HomeTile.MILESTONES -> AppFeature.MILESTONES
    HomeTile.TIP -> AppFeature.BREASTFEEDING
    HomeTile.FEEDING_HISTORY -> null
    HomeTile.TRENDS -> null
    HomeTile.PARTNER -> null
    // Always-on: vaccine ships outside the feature-toggle set (Feature Selection project).
    HomeTile.VACCINE -> null
    // Always-on: doctor visits ship outside the feature-toggle set.
    HomeTile.DOCTOR_VISIT -> null
}

internal fun HomeTile.isVisible(uiState: HomeUiState): Boolean {
    val features = uiState.enabledFeatures
    return when (this) {
        HomeTile.FEEDING_HISTORY ->
            AppFeature.BREASTFEEDING in features || AppFeature.BOTTLE_FEED in features
        HomeTile.TRENDS -> features.isNotEmpty()
        HomeTile.TIP -> AppFeature.BREASTFEEDING in features && uiState.nextRecommendedSide != null
        HomeTile.SLEEP_PREDICTION ->
            AppFeature.SLEEP in features && uiState.sleepPrediction !is SleepPredictionState.Unavailable
        HomeTile.PARTNER -> uiState.appMode == AppMode.NONE
        else -> requiredFeature()?.let { it in features } ?: true
    }
}

@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    callbacks: HomeTileCallbacks,
    onReorder: (List<HomeTile>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // Persisted order arrives async via StateFlow; mirror locally for lag-free drag, re-sync on emit.
    var orderState by remember { mutableStateOf(uiState.tileOrder) }
    LaunchedEffect(uiState.tileOrder) { orderState = uiState.tileOrder }

    val visibleTiles = remember(orderState, uiState) {
        orderState.filter { it.isVisible(uiState) }
    }
    val gridState = rememberLazyStaggeredGridState()
    val reorderableState = rememberReorderableLazyStaggeredGridState(gridState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyStaggeredGridState
        val toKey = to.key as? String ?: return@rememberReorderableLazyStaggeredGridState
        val fromTile = HomeTile.entries.firstOrNull { it.name == fromKey } ?: return@rememberReorderableLazyStaggeredGridState
        val toTile = HomeTile.entries.firstOrNull { it.name == toKey } ?: return@rememberReorderableLazyStaggeredGridState
        orderState = reorderByKey(orderState, visibleTiles, fromTile, toTile)
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxHeight()
                .testTag("home_tiles_grid"),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = visibleTiles,
                key = { _, tile -> tile.name },
                span = { _, tile ->
                    if (tile.isFullWidth()) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane
                },
            ) { index, tile ->
                val moveEarlierLabel = stringResource(R.string.home_move_earlier)
                val moveLaterLabel = stringResource(R.string.home_move_later)
                // itemsIndexed gives `index` for free (was an O(n) visibleTiles.indexOf(tile) per item,
                // O(n^2) over the grid). Remember the action list so the CustomAccessibilityAction
                // objects aren't reallocated on every recomposition (e.g. each drag frame); the lambdas
                // read the latest orderState at fire time, so they don't need it as a key.
                val moveActions = remember(index, visibleTiles, moveEarlierLabel, moveLaterLabel) {
                    buildList {
                        if (index > 0) {
                            add(
                                CustomAccessibilityAction(moveEarlierLabel) {
                                    val moved = reorderByKey(orderState, visibleTiles, tile, visibleTiles[index - 1])
                                    orderState = moved
                                    onReorder(moved)
                                    true
                                },
                            )
                        }
                        if (index < visibleTiles.lastIndex) {
                            add(
                                CustomAccessibilityAction(moveLaterLabel) {
                                    val moved = reorderByKey(orderState, visibleTiles, tile, visibleTiles[index + 1])
                                    orderState = moved
                                    onReorder(moved)
                                    true
                                },
                            )
                        }
                    }
                }
                ReorderableItem(reorderableState, key = tile.name) { isDragging ->
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 6.dp else 0.dp,
                        label = "tileDragElevation",
                    )
                    HomeTileContent(
                        tile = tile,
                        uiState = uiState,
                        callbacks = callbacks,
                        modifier = Modifier
                            .heightIn(min = tile.minTileHeightDp())
                            .shadow(elevation, MaterialTheme.shapes.large)
                            .semantics { customActions = moveActions }
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (orderState != uiState.tileOrder) onReorder(orderState)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeTileContent(
    tile: HomeTile,
    uiState: HomeUiState,
    callbacks: HomeTileCallbacks,
    modifier: Modifier = Modifier,
) {
    when (tile) {
        HomeTile.BREASTFEEDING -> BreastfeedingHomeCard(
            activeSession = uiState.activeSession,
            lastSessionStartTime = uiState.lastSessionStartTime,
            nextFeedPrediction = uiState.nextFeedPrediction,
            onClick = callbacks.onBreastfeeding,
            modifier = modifier,
        )
        HomeTile.SLEEP -> SleepHomeCard(
            activeSleepRecord = uiState.activeSleepRecord,
            lastSleepEndTime = uiState.lastSleepEndTime,
            onClick = callbacks.onSleep,
            modifier = modifier,
        )
        HomeTile.PUMPING -> PumpingHomeCard(uiState.pumpingActive, callbacks.onPumping, modifier)
        HomeTile.INVENTORY ->
            InventoryHomeCard(uiState.inventorySummary, uiState.volumeUnit, callbacks.onInventory, modifier)
        HomeTile.BOTTLE_FEED -> BottleFeedHomeCard(callbacks.onBottleFeed, modifier)
        HomeTile.DIAPER -> DiaperHomeCard(uiState.todayDiaperSummary, callbacks.onDiaper, modifier)
        HomeTile.VACCINE -> VaccineHomeCard(uiState.vaccineSummary, callbacks.onVaccine, modifier)
        HomeTile.DOCTOR_VISIT ->
            DoctorVisitHomeCard(uiState.doctorVisitSummary, callbacks.onDoctorVisit, modifier)
        HomeTile.FEEDING_HISTORY ->
            FeedingHistoryHomeCard(uiState.todayFeedingSummary, uiState.volumeUnit, callbacks.onFeedingHistory, modifier)
        HomeTile.SLEEP_PREDICTION ->
            SleepPredictionCard(state = uiState.sleepPrediction, onClick = callbacks.onSleep, modifier = modifier)
        HomeTile.GROWTH -> GrowthHomeCard(callbacks.onGrowth, modifier)
        HomeTile.MILESTONES -> MilestonesHomeCard(callbacks.onMilestones, modifier)
        HomeTile.TRENDS -> TrendsHomeCard(callbacks.onTrends, modifier)
        HomeTile.TIP -> HomeTipCard(uiState.nextRecommendedSide, modifier)
        HomeTile.PARTNER -> PartnerViewCard(callbacks.onConnectPartner, modifier)
    }
}

internal data class HomeTileCallbacks(
    val onBreastfeeding: () -> Unit,
    val onSleep: () -> Unit,
    val onPumping: () -> Unit,
    val onInventory: () -> Unit,
    val onBottleFeed: () -> Unit,
    val onDiaper: () -> Unit,
    val onVaccine: () -> Unit,
    val onDoctorVisit: () -> Unit,
    val onFeedingHistory: () -> Unit,
    val onConnectPartner: () -> Unit,
    val onGrowth: () -> Unit,
    val onMilestones: () -> Unit,
    val onTrends: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BreastfeedingHomeCard(
    activeSession: BreastfeedingSession?,
    lastSessionStartTime: Instant?,
    nextFeedPrediction: FeedPrediction?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActiveFeeding = activeSession != null
    val breastfeedingDescription = if (isActiveFeeding) {
        stringResource(R.string.home_breastfeeding_active_content_description)
    } else {
        stringResource(R.string.home_breastfeeding_content_description)
    }
    val feedingContainerColor by animateColorAsState(
        targetValue = if (isActiveFeeding) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "feedingContainerColor",
    )
    val feedingContentColor by animateColorAsState(
        targetValue = if (isActiveFeeding) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "feedingContentColor",
    )
    val feedingElevation by animateDpAsState(
        targetValue = if (isActiveFeeding) 6.dp else 1.dp,
        animationSpec = tween(durationMillis = 240, easing = EaseOutQuart),
        label = "feedingElevation",
    )
    HomeTrackerTile(
        title = stringResource(R.string.home_breastfeeding_title),
        contentDescription = breastfeedingDescription,
        containerColor = feedingContainerColor,
        contentColor = feedingContentColor,
        onClick = onClick,
        icon = { BreastfeedingIcon(modifier = it) },
        modifier = modifier,
        minHeight = if (isActiveFeeding) 188.dp else 168.dp,
        elevation = feedingElevation,
        iconSize = if (isActiveFeeding) 68.dp else 60.dp,
        titleStyle = if (isActiveFeeding) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        trailing = {
            AnimatedVisibility(
                visible = activeSession != null,
                enter = fadeIn(tween(180, easing = EaseOutQuart)) +
                    scaleIn(initialScale = 0.82f, animationSpec = tween(180, easing = EaseOutQuart)),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.82f, animationSpec = tween(120)),
            ) {
                ActiveStatusBadge(
                    paused = activeSession?.isPaused == true,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) {
        if (activeSession != null) {
            ActiveFeedingTimer(session = activeSession, color = feedingContentColor)
            HomeTileStatusText(
                text = stringResource(activeSession.startingSide.labelRes()),
                color = feedingContentColor,
            )
        } else {
            if (lastSessionStartTime != null) {
                LastFeedingAgoText(lastStart = lastSessionStartTime)
            } else {
                HomeTileStatusText(
                    text = stringResource(R.string.home_tap_to_log),
                    color = feedingContentColor,
                )
            }
            nextFeedPrediction?.let { prediction ->
                FeedingPredictionSubtitle(prediction = prediction)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepHomeCard(
    activeSleepRecord: SleepRecord?,
    lastSleepEndTime: Instant?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActiveSleep = activeSleepRecord != null
    val sleepDescription = if (isActiveSleep) {
        stringResource(R.string.home_sleep_active_content_description)
    } else {
        stringResource(R.string.home_sleep_content_description)
    }
    val sleepContainerColor by animateColorAsState(
        targetValue = if (isActiveSleep) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "sleepContainerColor",
    )
    val sleepContentColor by animateColorAsState(
        targetValue = if (isActiveSleep) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuart),
        label = "sleepContentColor",
    )
    val sleepElevation by animateDpAsState(
        targetValue = if (isActiveSleep) 6.dp else 1.dp,
        animationSpec = tween(durationMillis = 240, easing = EaseOutQuart),
        label = "sleepElevation",
    )
    HomeTrackerTile(
        title = stringResource(R.string.home_sleep_title),
        contentDescription = sleepDescription,
        containerColor = sleepContainerColor,
        contentColor = sleepContentColor,
        onClick = onClick,
        icon = { SleepIcon(modifier = it) },
        modifier = modifier,
        minHeight = if (isActiveSleep) 188.dp else 168.dp,
        elevation = sleepElevation,
        iconSize = if (isActiveSleep) 68.dp else 60.dp,
        titleStyle = if (isActiveSleep) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        trailing = {
            AnimatedVisibility(
                visible = isActiveSleep,
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
        if (activeSleepRecord != null) {
            ActiveSleepTimer(record = activeSleepRecord, color = sleepContentColor)
        } else {
            if (lastSleepEndTime != null) {
                LastSleepAgoText(endTime = lastSleepEndTime)
            } else {
                HomeTileStatusText(
                    text = stringResource(R.string.home_sleep_ready),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTipCard(
    nextRecommendedSide: BreastSide?,
    modifier: Modifier = Modifier,
) {
    val nextSideName = nextRecommendedSide?.let { stringResource(it.labelRes()) } ?: ""
    Card(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.TipsAndUpdates,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .clearAndSetSemantics {},
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_tip_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.home_tip_message, nextSideName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PartnerViewCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val partnerDescription = stringResource(R.string.home_partner_content_description)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = partnerDescription
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_partner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.home_partner_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
