package com.babytracker.manager

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.domain.usecase.sleep.shouldUpdateWakeTimeFor
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner of the sleep session control operations (start, stop): persists the change, keeps
 * the sleep notification and nap-reminder alarm in step, propagates a night-sleep end into the
 * wake-time setting, and pushes the result to the partner snapshot.
 * Called from [com.babytracker.ui.sleep.SleepViewModel] (in-app buttons),
 * [com.babytracker.receiver.SleepActionReceiver] (notification quick-actions), and
 * [com.babytracker.tile.TileToggleHandler] (quick-settings tile), which used to carry diverging
 * copies of this choreography.
 */
@Singleton
class SleepSessionController @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val stopSleepRecordUseCase: StopSleepRecordUseCase,
    private val sleepNotificationScheduler: SleepNotificationScheduler,
    private val napReminderScheduler: NapReminderScheduler,
    private val settingsRepository: SettingsRepository,
    private val syncedWrite: SyncedWrite,
    private val clock: Clock,
) {

    /**
     * Starts a new record of [sleepType] via [SleepRepository.startRecordIfNone], which guards
     * against a concurrent start with a DB-level unique-active-record constraint. Returns null
     * without side effects when a record was already active — a benign race, not an error. A
     * notification failure does not fail the start; the record is still persisted and synced.
     */
    suspend fun start(sleepType: SleepType): SleepRecord? {
        runCatching { napReminderScheduler.cancel() }
        val record = SleepRecord(
            startTime = clock.instant(),
            sleepType = sleepType,
            timezoneId = clock.zone.id,
        )
        val id = sleepRepository.startRecordIfNone(record) ?: return null
        val created = record.copy(id = id)
        runCatching { sleepNotificationScheduler.show(created.id, created.sleepType, created.startTime) }
        syncedWrite.sync(SyncType.SLEEP_RECORDS)
        return created
    }

    /**
     * Ends the active record identified by [sessionId] via [StopSleepRecordUseCase], which
     * re-reads the active record and only stops it when its id still matches — a benign race,
     * not an error, returns null with no side effects. A completed nap re-arms the nap-reminder
     * alarm (subject to the feature gate and the enabled/delay settings, both read inside
     * [NapReminderScheduler.scheduleIfEnabled]); a completed night sleep that ended today updates
     * the wake-time setting so the schedule reflects when the baby actually woke up.
     */
    suspend fun stop(sessionId: Long): SleepRecord? {
        val stopped = stopSleepRecordUseCase(sessionId) ?: return null
        runCatching { sleepNotificationScheduler.cancel() }
        when (stopped.sleepType) {
            SleepType.NAP -> runCatching { napReminderScheduler.scheduleIfEnabled(checkNotNull(stopped.endTime)) }
            SleepType.NIGHT_SLEEP -> propagateWakeTime(stopped)
        }
        syncedWrite.sync(SyncType.SLEEP_RECORDS)
        return stopped
    }

    private suspend fun propagateWakeTime(stopped: SleepRecord) {
        val endTime = stopped.endTime ?: return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday = today.atStartOfDay(zone).toInstant()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant()
        // Between (not since) startOfToday: a manually logged night sleep that started yesterday
        // and ended after midnight would be missed by a start-time-bounded query, letting an
        // earlier-ending record incorrectly win the "latest end today" comparison below.
        val candidates = sleepRepository.getCompletedRecordsBetween(startOfToday, startOfTomorrow)
        val shouldUpdate = shouldUpdateWakeTimeFor(
            endTime = endTime,
            sleepType = stopped.sleepType,
            existingRecords = candidates,
            zone = zone,
            today = today,
            excludingId = stopped.id,
        )
        if (shouldUpdate) {
            settingsRepository.setWakeTime(endTime.atZone(zone).toLocalTime())
        }
    }
}
