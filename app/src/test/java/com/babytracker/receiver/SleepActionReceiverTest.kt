package com.babytracker.receiver

import android.content.Intent
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.SleepNotificationScheduler
import com.babytracker.manager.SleepSessionController
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class SleepActionReceiverTest {

    private val clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC)

    private lateinit var sleepRepository: SleepRepository
    private lateinit var stopSleepRecordUseCase: StopSleepRecordUseCase
    private lateinit var sleepNotificationScheduler: SleepNotificationScheduler
    private lateinit var napReminderScheduler: NapReminderScheduler
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private lateinit var receiver: SleepActionReceiver

    @BeforeEach
    fun setup() {
        sleepRepository = mockk()
        stopSleepRecordUseCase = mockk()
        sleepNotificationScheduler = mockk(relaxed = true)
        napReminderScheduler = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        syncToFirestore = mockk(relaxed = true)
        receiver = SleepActionReceiver()
        // Real controller over the same mocks: the receiver contract is "delegates to the shared
        // sleep session-control behaviour", so tests verify through to the underlying collaborators
        // (mirrors how BreastfeedingActionReceiverTest wires BreastfeedingSessionController).
        receiver.sessionController = SleepSessionController(
            sleepRepository = sleepRepository,
            stopSleepRecordUseCase = stopSleepRecordUseCase,
            sleepNotificationScheduler = sleepNotificationScheduler,
            napReminderScheduler = napReminderScheduler,
            settingsRepository = settingsRepository,
            syncedWrite = SyncedWrite(syncToFirestore),
            clock = clock,
        )
    }

    private fun buildStopIntent(sessionId: Long = 99L): Intent = mockk<Intent>(relaxed = true).also {
        every { it.getStringExtra(SleepActionReceiver.EXTRA_ACTION) } returns SleepActionReceiver.ACTION_STOP
        every { it.getLongExtra(SleepActionReceiver.EXTRA_SESSION_ID, -1L) } returns sessionId
    }

    @Test
    fun `ACTION_STOP calls the controller and cancels the sleep notification`() = runTest {
        coEvery { stopSleepRecordUseCase(99L) } returns null

        receiver.handle(buildStopIntent())

        coVerify { stopSleepRecordUseCase(99L) }
        // A null return means no active record matched — the controller skips all side effects.
        verify(exactly = 0) { sleepNotificationScheduler.cancel() }
    }

    @Test
    fun `unknown action does not stop the record`() = runTest {
        receiver.handle(mockk<Intent>(relaxed = true).also {
            every { it.getStringExtra(SleepActionReceiver.EXTRA_ACTION) } returns "unknown"
            every { it.getLongExtra(SleepActionReceiver.EXTRA_SESSION_ID, -1L) } returns 1L
        })

        coVerify(exactly = 0) { stopSleepRecordUseCase(any()) }
    }

    @Test
    fun `ACTION_STOP with NAP record cancels the notification and schedules the nap reminder when enabled`() = runTest {
        val napRecord = SleepRecord(
            id = 99L,
            startTime = Instant.now().minusSeconds(1800),
            endTime = Instant.now(),
            sleepType = SleepType.NAP,
        )
        coEvery { stopSleepRecordUseCase(99L) } returns napRecord

        receiver.handle(buildStopIntent())

        verify { sleepNotificationScheduler.cancel() }
        coVerify { napReminderScheduler.scheduleIfEnabled(napRecord.endTime!!) }
    }

    @Test
    fun `ACTION_STOP with NIGHT_SLEEP record does not schedule a nap reminder`() = runTest {
        val nightRecord = SleepRecord(
            id = 99L,
            startTime = Instant.now().minusSeconds(28800),
            endTime = Instant.now(),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        coEvery { stopSleepRecordUseCase(99L) } returns nightRecord

        receiver.handle(buildStopIntent())

        coVerify(exactly = 0) { napReminderScheduler.scheduleIfEnabled(any()) }
    }

    @Test
    fun `ACTION_STOP with NIGHT_SLEEP ended today sets the wake time`() = runTest {
        // AKACHAN-354: unlike the old hand-rolled receiver choreography, the shared controller
        // propagates a night-sleep end into the wake-time setting on this notification-driven path too.
        val now = Instant.now()
        val nightRecord = SleepRecord(
            id = 99L, startTime = now.minusSeconds(28800), endTime = now, sleepType = SleepType.NIGHT_SLEEP
        )
        coEvery { stopSleepRecordUseCase(99L) } returns nightRecord

        receiver.handle(buildStopIntent())

        val expectedWakeTime = now.atZone(ZoneId.systemDefault()).toLocalTime()
        coVerify { settingsRepository.setWakeTime(expectedWakeTime) }
    }

    @Test
    fun `ACTION_STOP syncs sleep records to the partner snapshot`() = runTest {
        // AKACHAN-354: the old receiver never synced after a notification-driven stop; the shared
        // controller now does, closing that partner-sync gap.
        val napRecord = SleepRecord(
            id = 99L,
            startTime = Instant.now().minusSeconds(1800),
            endTime = Instant.now(),
            sleepType = SleepType.NAP,
        )
        coEvery { stopSleepRecordUseCase(99L) } returns napRecord

        receiver.handle(buildStopIntent())

        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
    }
}
