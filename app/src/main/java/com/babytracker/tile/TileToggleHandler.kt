package com.babytracker.tile

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.manager.BreastfeedingSessionController
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.SleepNotificationScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import com.babytracker.widget.WidgetUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
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
    private val sleepNotificationScheduler: SleepNotificationScheduler,
    private val napReminderScheduler: NapReminderScheduler,
    private val syncedWrite: SyncedWrite,
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
                stopSleepRecord(latest, now)
            } else {
                startSleepRecord(now)
            }
            if (changed) runCatching { widgetUpdater.updateAll() }
            if (changed) TileToggleResult.CHANGED else TileToggleResult.NO_OP
        }.getOrElse {
            TileToggleResult.FAILED
        }
    }

    private suspend fun stopSleepRecord(latest: SleepRecord, now: Instant): Boolean {
        val stopped = sleepRepository.stopActiveRecord(now)
        if (stopped) {
            sleepNotificationScheduler.cancel()
            if (latest.sleepType == SleepType.NAP) {
                runCatching { napReminderScheduler.scheduleIfEnabled(now) }
            }
            syncedWrite.sync(SyncType.SLEEP_RECORDS)
        }
        return stopped
    }

    private suspend fun startSleepRecord(now: Instant): Boolean {
        val record = SleepRecord(
            startTime = now,
            sleepType = sleepTypeFor(now, clock.zone),
            timezoneId = clock.zone.id,
        )
        val id = sleepRepository.startRecordIfNone(record)
        if (id != null) {
            val created = record.copy(id = id)
            runCatching { napReminderScheduler.cancel() }
            runCatching { sleepNotificationScheduler.show(created.id, created.sleepType, created.startTime) }
            syncedWrite.sync(SyncType.SLEEP_RECORDS)
        }
        return id != null
    }
}
