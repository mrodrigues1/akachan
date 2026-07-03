package com.babytracker.tile

import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.manager.BreastfeedingSessionController
import com.babytracker.manager.SleepSessionController
import com.babytracker.widget.WidgetUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

enum class TileToggleResult {
    CHANGED,
    NO_OP,
    FAILED,
}

@Singleton
class TileToggleHandler @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val sessionController: BreastfeedingSessionController,
    private val sleepSessionController: SleepSessionController,
    private val widgetUpdater: WidgetUpdater,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun toggleFeed(): TileToggleResult = mutex.withLock {
        runCatching {
            val active = breastfeedingRepository.getActiveSession().first()
            val changed = if (active != null) {
                sessionController.stop()
            } else {
                val side = alternateSide(breastfeedingRepository.getLastSession())
                sessionController.start(side) != null
            }
            if (changed) runCatching { widgetUpdater.updateAll() }
            if (changed) TileToggleResult.CHANGED else TileToggleResult.NO_OP
        }.getOrElse {
            TileToggleResult.FAILED
        }
    }

    suspend fun toggleSleep(): TileToggleResult = mutex.withLock {
        runCatching {
            val now = clock.instant()
            val latest = sleepRepository.getLatestRecord()
            val changed = if (latest?.isInProgress == true) {
                // The get-latest-then-stop check above only decides which branch to take; the
                // actual stop is id-guarded by the controller's use case, so a race that clears
                // the active record between the two calls just yields a no-op, not a bad write.
                sleepSessionController.stop(latest.id) != null
            } else {
                sleepSessionController.start(sleepTypeFor(now, clock.zone)) != null
            }
            if (changed) runCatching { widgetUpdater.updateAll() }
            if (changed) TileToggleResult.CHANGED else TileToggleResult.NO_OP
        }.getOrElse {
            TileToggleResult.FAILED
        }
    }
}
