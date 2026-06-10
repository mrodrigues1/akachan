package com.babytracker.widget

import android.util.Log
import com.babytracker.di.ApplicationScope
import com.babytracker.domain.repository.InventoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

private const val DEBOUNCE_MS = 500L
private const val TAG = "MilkStashWidgetSync"

@OptIn(FlowPreview::class)
@Singleton
class MilkStashWidgetSyncManager @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val updater: MilkStashWidgetUpdater,
    @ApplicationScope private val scope: CoroutineScope,
) {

    fun start() {
        inventoryRepository.getSummary()
            .map { Unit }
            .debounce(DEBOUNCE_MS)
            .onEach { safeUpdate() }
            .catch { e -> Log.w(TAG, "Milk stash widget sync flow terminated", e) }
            .launchIn(scope)
    }

    private suspend fun safeUpdate() {
        runCatching { updater.updateAll() }
            .onFailure { t ->
                if (t is CancellationException) throw t
                Log.w(TAG, "updateAll() failed; will retry on next emission", t)
            }
    }
}
