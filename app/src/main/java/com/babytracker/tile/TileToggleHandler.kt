package com.babytracker.tile

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.manager.SleepSessionNotificationCoordinator
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
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
    private val breastfeedingNotifications: BreastfeedingSessionNotificationCoordinator,
    private val sleepNotifications: SleepSessionNotificationCoordinator,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val widgetUpdater: WidgetUpdater,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun toggleFeed(): TileToggleResult = mutex.withLock {
        runCatching {
            val now = clock.instant()
            val active = breastfeedingRepository.getActiveSession().first()
            val changed = if (active != null) {
                val stopped = breastfeedingRepository.stopActiveSession(now)
                if (stopped) {
                    breastfeedingNotifications.cancelScheduled()
                    breastfeedingNotifications.cancelPostedSessionNotifications()
                    runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
                }
                stopped
            } else {
                val side = alternateSide(breastfeedingRepository.getLastSession())
                val session = BreastfeedingSession(startTime = now, startingSide = side)
                val id = breastfeedingRepository.startSessionIfNone(session)
                if (id != null) {
                    val created = session.copy(id = id)
                    runCatching { breastfeedingNotifications.scheduleInitial(created) }
                    runCatching { breastfeedingNotifications.showRunning(created) }
                    runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
                }
                id != null
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
            sleepNotifications.cancelNotification()
            if (latest.sleepType == SleepType.NAP) {
                runCatching { sleepNotifications.scheduleNapReminderIfEnabled(now) }
            }
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
        }
        return stopped
    }

    private suspend fun startSleepRecord(now: Instant): Boolean {
        val record = SleepRecord(startTime = now, sleepType = sleepTypeFor(now, clock.zone))
        val id = sleepRepository.startRecordIfNone(record)
        if (id != null) {
            val created = record.copy(id = id)
            runCatching { sleepNotifications.cancelNapReminder() }
            runCatching { sleepNotifications.showNotification(created.id, created.sleepType, created.startTime) }
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
        }
        return id != null
    }
}
