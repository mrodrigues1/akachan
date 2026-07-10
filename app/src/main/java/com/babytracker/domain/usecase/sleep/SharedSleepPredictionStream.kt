package com.babytracker.domain.usecase.sleep

import com.babytracker.di.ApplicationScope
import com.babytracker.domain.model.SleepPredictionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * De-duplicates the sleep-prediction pipeline across every consumer. [PredictSleepWindowUseCase]
 * observes four Room flows plus a per-minute recompute ticker and runs the feature extractor on each
 * tick; before this each consumer (tracking screen, Home, the predictive-notification coordinator)
 * started its own cold copy, so up to four identical pipelines ran at once.
 *
 * A single [shareIn] on the application scope collapses them to one hot upstream: the pipeline attaches
 * on the first collector and detaches [STOP_TIMEOUT_MS] after the last one leaves, and `replay = 1`
 * hands a late subscriber the current prediction immediately. The predictive-notification coordinator
 * collects this stream for the whole time notifications are enabled, so in practice exactly one
 * pipeline stays hot and every UI consumer reuses it.
 *
 * Modelled on `SharedSleepOpStream`, minus the per-key map: there is a single global prediction, not
 * one per share code. No retry wrapper is added because [PredictSleepWindowUseCase] does not throw in
 * normal operation and the pre-existing consumers collected it directly without one, so wrapping it
 * would change failure behaviour rather than preserve it.
 */
@Singleton
class SharedSleepPredictionStream @Inject constructor(
    predictSleepWindow: PredictSleepWindowUseCase,
    @param:ApplicationScope appScope: CoroutineScope,
) {
    private val stream: Flow<SleepPredictionState> =
        predictSleepWindow().shareIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** The single shared, app-scoped stream of sleep-prediction state. */
    fun observe(): Flow<SleepPredictionState> = stream

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
