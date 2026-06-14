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
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.ui.sleep.SleepPredictionCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

internal fun HomeTile.isFullWidth(): Boolean = when (this) {
    HomeTile.SLEEP_PREDICTION, HomeTile.TIP, HomeTile.PARTNER -> true
    else -> false
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

internal fun HomeTile.isVisible(uiState: HomeUiState): Boolean = when (this) {
    HomeTile.TIP -> uiState.nextRecommendedSide != null
    HomeTile.PARTNER -> uiState.appMode == AppMode.NONE
    HomeTile.SLEEP_PREDICTION -> uiState.sleepPrediction !is SleepPredictionState.Unavailable
    else -> true
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = visibleTiles,
                key = { it.name },
                span = { tile ->
                    if (tile.isFullWidth()) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane
                },
            ) { tile ->
                val index = visibleTiles.indexOf(tile)
                val moveActions = buildList {
                    if (index > 0) {
                        add(
                            CustomAccessibilityAction("Move earlier") {
                                val moved = reorderByKey(orderState, visibleTiles, tile, visibleTiles[index - 1])
                                orderState = moved
                                onReorder(moved)
                                true
                            },
                        )
                    }
                    if (index < visibleTiles.lastIndex) {
                        add(
                            CustomAccessibilityAction("Move later") {
                                val moved = reorderByKey(orderState, visibleTiles, tile, visibleTiles[index + 1])
                                orderState = moved
                                onReorder(moved)
                                true
                            },
                        )
                    }
                }
                ReorderableItem(reorderableState, key = tile.name) { isDragging ->
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 0.dp,
                        label = "tileDragElevation",
                    )
                    HomeTileContent(
                        tile = tile,
                        uiState = uiState,
                        callbacks = callbacks,
                        modifier = Modifier
                            .heightIn(min = if (tile.isFullWidth()) 0.dp else 140.dp)
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
        HomeTile.BREASTFEEDING -> BreastfeedingHomeCard(uiState, callbacks.onBreastfeeding, modifier)
        HomeTile.SLEEP -> SleepHomeCard(uiState, callbacks.onSleep, modifier)
        HomeTile.PUMPING -> PumpingHomeCard(uiState.pumpingActive, callbacks.onPumping, modifier)
        HomeTile.INVENTORY ->
            InventoryHomeCard(uiState.inventorySummary, uiState.volumeUnit, callbacks.onInventory, modifier)
        HomeTile.BOTTLE_FEED -> BottleFeedHomeCard(callbacks.onBottleFeed, modifier)
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
    val onFeedingHistory: () -> Unit,
    val onConnectPartner: () -> Unit,
    val onGrowth: () -> Unit,
    val onMilestones: () -> Unit,
    val onTrends: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BreastfeedingHomeCard(
    uiState: HomeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSession = uiState.activeSession
    val isActiveFeeding = activeSession != null
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
    Card(
        onClick = onClick,
        modifier = modifier
            .semantics {
                contentDescription = if (isActiveFeeding) {
                    "Breastfeeding, session active. Open feeding screen."
                } else {
                    "Breastfeeding. Open feeding screen."
                }
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = feedingContainerColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = feedingElevation),
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
                    text = "🍼",
                    style = MaterialTheme.typography.headlineMedium,
                    color = feedingContentColor,
                    modifier = Modifier.clearAndSetSemantics {},
                )
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
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Breastfeeding",
                style = MaterialTheme.typography.titleMedium,
                color = feedingContentColor,
            )
            if (activeSession != null) {
                Spacer(modifier = Modifier.height(4.dp))
                ActiveFeedingTimer(session = activeSession, color = feedingContentColor)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepHomeCard(
    uiState: HomeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSleepRecord = uiState.activeSleepRecord
    val isActiveSleep = activeSleepRecord != null
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
    Card(
        onClick = onClick,
        modifier = modifier
            .semantics {
                contentDescription = if (isActiveSleep) {
                    "Sleep, session active. Open sleep screen."
                } else {
                    "Sleep. Open sleep screen."
                }
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = sleepContainerColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = sleepElevation),
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
                    text = "🌙",
                    style = MaterialTheme.typography.headlineMedium,
                    color = sleepContentColor,
                    modifier = Modifier.clearAndSetSemantics {},
                )
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
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sleep",
                style = MaterialTheme.typography.titleMedium,
                color = sleepContentColor,
            )
            if (activeSleepRecord != null) {
                Spacer(modifier = Modifier.height(4.dp))
                ActiveSleepTimer(record = activeSleepRecord, color = sleepContentColor)
            } else {
                val lastEnd = uiState.lastSleepEndTime
                if (lastEnd != null) {
                    LastSleepAgoText(endTime = lastEnd)
                } else {
                    Text(
                        text = "Ready when you are",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
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
    val nextSideName = nextRecommendedSide
        ?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
    Card(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Try $nextSideName breast next, used less last session.",
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
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Partner View. Connect to see shared baby data."
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
