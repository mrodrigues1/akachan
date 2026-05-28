package com.babytracker.widget

import android.util.Log
import com.babytracker.di.ApplicationScope
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

private const val DEBOUNCE_MS = 500L
private const val TAG = "WidgetSyncManager"

@OptIn(FlowPreview::class)
@Singleton
class WidgetSyncManager @Inject constructor(
    private val feedRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val babyRepository: BabyRepository,
    private val updater: WidgetUpdater,
    @ApplicationScope private val scope: CoroutineScope,
) {

    fun start() {
        val feedTrigger = feedRepository.observeLatestSession().map { Unit }
        val sleepTrigger = sleepRepository.observeLatestRecord().map { Unit }
        val babyTrigger = babyRepository.getBabyProfile().map { Unit }

        merge(feedTrigger, sleepTrigger, babyTrigger)
            .debounce(DEBOUNCE_MS)
            .onEach { safeUpdate() }
            .catch { e -> Log.w(TAG, "Widget sync flow terminated", e) }
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
