package com.babytracker.ui.partner

import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.PartnerDataFetchException
import com.babytracker.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * True when an op that was pending in [previous] is no longer in [current] — the primary consumed
 * it and pushed an updated snapshot, so the stale one-shot snapshot must be re-fetched.
 */
internal fun hasConsumedPendingOps(
    previous: Set<String>,
    current: Set<String>,
): Boolean = previous.any { it !in current }

/**
 * Shared refresh body for [PartnerFeedHistoryViewModel] and [PartnerSleepHistoryViewModel]: fetch
 * the partner snapshot, observe the merged history, and apply the identical 4-branch error handling
 * (with the [widgetUpdater] poke on revoke). The view models differ only in which observe use case
 * runs and which typed UI-state fields they write, so those are passed as lambdas.
 *
 * @param onSnapshotLoaded runs once the snapshot is fetched, before observing (feed sets milk bags).
 * @param onMerged runs per merged emission — set entries and handle pending-op refresh here.
 */
internal suspend fun <M> refreshPartnerHistory(
    fetchPartnerData: FetchPartnerDataUseCase,
    widgetUpdater: WidgetUpdater,
    observe: suspend (ShareSnapshot) -> Flow<M>,
    onLoadingStart: () -> Unit,
    onSnapshotLoaded: (ShareSnapshot) -> Unit,
    onMerged: (M) -> Unit,
    onAccessRevoked: () -> Unit,
    onLoadFailed: () -> Unit,
) {
    onLoadingStart()
    try {
        val snapshot = fetchPartnerData()
        onSnapshotLoaded(snapshot)
        observe(snapshot).collect { merged -> onMerged(merged) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: PartnerAccessRevokedException) {
        onAccessRevoked()
        widgetUpdater.updateAll()
    } catch (_: PartnerDataFetchException) {
        onLoadFailed()
    } catch (_: IllegalStateException) {
        onLoadFailed()
    }
}
