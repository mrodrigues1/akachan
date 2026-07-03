package com.babytracker.manager

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SleepSessionControllerTest {

    private val now = Instant.parse("2026-01-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var sleepRepository: SleepRepository
    private lateinit var stopSleepRecordUseCase: StopSleepRecordUseCase
    private lateinit var sleepNotificationScheduler: SleepNotificationScheduler
    private lateinit var napReminderScheduler: NapReminderScheduler
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var syncedWrite: SyncedWrite
    private lateinit var controller: SleepSessionController

    @BeforeEach
    fun setup() {
        sleepRepository = mockk(relaxed = true)
        stopSleepRecordUseCase = mockk()
        sleepNotificationScheduler = mockk(relaxed = true)
        napReminderScheduler = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        syncedWrite = mockk(relaxed = true)
        controller = SleepSessionController(
            sleepRepository = sleepRepository,
            stopSleepRecordUseCase = stopSleepRecordUseCase,
            sleepNotificationScheduler = sleepNotificationScheduler,
            napReminderScheduler = napReminderScheduler,
            settingsRepository = settingsRepository,
            syncedWrite = syncedWrite,
            clock = clock,
        )
    }

    @Test
    fun `start cancels the pending nap reminder, persists via startRecordIfNone, notifies, and syncs`() = runTest {
        val saved = slot<SleepRecord>()
        coEvery { sleepRepository.startRecordIfNone(capture(saved)) } returns 7L

        val record = controller.start(SleepType.NAP)

        assertNotNull(record)
        assertEquals(7L, record?.id)
        assertEquals(SleepType.NAP, saved.captured.sleepType)
        assertEquals(now, saved.captured.startTime)
        verify { napReminderScheduler.cancel() }
        coVerify { sleepNotificationScheduler.show(7L, SleepType.NAP, now) }
        coVerify { syncedWrite.sync(any()) }
    }

    @Test
    fun `start returns null and skips notification and sync when a record is already active`() = runTest {
        coEvery { sleepRepository.startRecordIfNone(any()) } returns null

        val record = controller.start(SleepType.NAP)

        assertNull(record)
        coVerify(exactly = 0) { sleepNotificationScheduler.show(any(), any(), any()) }
        coVerify(exactly = 0) { syncedWrite.sync(any()) }
    }

    @Test
    fun `start still returns the record and syncs when the notification throws`() = runTest {
        coEvery { sleepRepository.startRecordIfNone(any()) } returns 3L
        coEvery { sleepNotificationScheduler.show(any(), any(), any()) } throws RuntimeException("scheduler dead")

        val record = controller.start(SleepType.NIGHT_SLEEP)

        assertNotNull(record)
        assertEquals(3L, record?.id)
        coVerify { syncedWrite.sync(any()) }
    }

    @Test
    fun `stop returns null and skips side effects when the use case returns null`() = runTest {
        coEvery { stopSleepRecordUseCase(1L) } returns null

        val stopped = controller.stop(1L)

        assertNull(stopped)
        verify(exactly = 0) { sleepNotificationScheduler.cancel() }
        coVerify(exactly = 0) { syncedWrite.sync(any()) }
    }

    @Test
    fun `stop cancels the sleep notification and syncs`() = runTest {
        val stopped = SleepRecord(
            id = 2L, startTime = now.minusSeconds(1800), endTime = now, sleepType = SleepType.NAP
        )
        coEvery { stopSleepRecordUseCase(2L) } returns stopped

        val result = controller.stop(2L)

        assertEquals(stopped, result)
        verify { sleepNotificationScheduler.cancel() }
        coVerify { syncedWrite.sync(any()) }
    }

    @Test
    fun `stop with NAP schedules the nap reminder via scheduleIfEnabled using the stopped endTime`() = runTest {
        val stopped = SleepRecord(
            id = 2L, startTime = now.minusSeconds(1800), endTime = now, sleepType = SleepType.NAP
        )
        coEvery { stopSleepRecordUseCase(2L) } returns stopped

        controller.stop(2L)

        coVerify { napReminderScheduler.scheduleIfEnabled(now) }
    }

    @Test
    fun `stop with NIGHT_SLEEP never touches the nap reminder`() = runTest {
        val stopped = SleepRecord(
            id = 3L, startTime = now.minusSeconds(28800), endTime = now, sleepType = SleepType.NIGHT_SLEEP
        )
        coEvery { stopSleepRecordUseCase(3L) } returns stopped

        controller.stop(3L)

        coVerify(exactly = 0) { napReminderScheduler.scheduleIfEnabled(any()) }
        verify(exactly = 0) { napReminderScheduler.schedule(any(), any()) }
    }

    @Test
    fun `stop with NAP does not set wake time`() = runTest {
        val stopped = SleepRecord(
            id = 3L, startTime = now.minusSeconds(1800), endTime = now, sleepType = SleepType.NAP
        )
        coEvery { stopSleepRecordUseCase(3L) } returns stopped

        controller.stop(3L)

        coVerify(exactly = 0) { settingsRepository.setWakeTime(any()) }
    }

    @Test
    fun `stop with NIGHT_SLEEP ended today sets wake time`() = runTest {
        val zone = ZoneId.systemDefault()
        val todayEnd = LocalDate.now(zone).atTime(6, 30).atZone(zone).toInstant()
        val stopped = SleepRecord(
            id = 4L, startTime = todayEnd.minusSeconds(28800), endTime = todayEnd, sleepType = SleepType.NIGHT_SLEEP
        )
        coEvery { stopSleepRecordUseCase(4L) } returns stopped

        controller.stop(4L)

        val expectedWakeTime = todayEnd.atZone(zone).toLocalTime()
        coVerify { settingsRepository.setWakeTime(expectedWakeTime) }
    }

    @Test
    fun `stop with NIGHT_SLEEP ended on another day does not set wake time`() = runTest {
        val zone = ZoneId.systemDefault()
        val yesterdayEnd = LocalDate.now(zone).minusDays(1).atTime(6, 30).atZone(zone).toInstant()
        val stopped = SleepRecord(
            id = 5L,
            startTime = yesterdayEnd.minusSeconds(28800),
            endTime = yesterdayEnd,
            sleepType = SleepType.NIGHT_SLEEP,
        )
        coEvery { stopSleepRecordUseCase(5L) } returns stopped

        controller.stop(5L)

        coVerify(exactly = 0) { settingsRepository.setWakeTime(any()) }
    }

    @Test
    fun `stop still returns the record and syncs when the notification cancel throws`() = runTest {
        val stopped = SleepRecord(
            id = 6L, startTime = now.minusSeconds(1800), endTime = now, sleepType = SleepType.NAP
        )
        coEvery { stopSleepRecordUseCase(6L) } returns stopped
        every { sleepNotificationScheduler.cancel() } throws RuntimeException("cancel dead")

        val result = controller.stop(6L)

        assertEquals(stopped, result)
        coVerify { syncedWrite.sync(any()) }
    }
}
