package com.babytracker.ui.partner

import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Collects a partner-history flow with the shared 3-branch error handling: revoke -> onAccessRevoked +
 * widget poke; any other terminal error -> onLoadFailed. Cancellation propagates untouched. The flow
 * itself differs per view model (feed adds milk bags, sleep does not), so the per-item write is a lambda.
 */
internal suspend fun <T> Flow<T>.collectPartnerHistory(
    widgetUpdater: WidgetUpdater,
    onItem: (T) -> Unit,
    onAccessRevoked: () -> Unit,
    onLoadFailed: () -> Unit,
) {
    catch { error ->
        when (error) {
            is CancellationException -> throw error
            is PartnerAccessRevokedException -> {
                onAccessRevoked()
                widgetUpdater.updateAll()
            }
            else -> onLoadFailed() // PartnerDataFetchException, IllegalStateException, etc.
        }
    }.collect { onItem(it) }
}
