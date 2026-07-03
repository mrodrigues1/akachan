package com.babytracker.manager

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StaleSleepNotificationCancellerTest {

    private val activeRecord = MutableStateFlow<SleepRecord?>(null)
    private val sleepRepository = mockk<SleepRepository> { every { observeActiveRecord() } returns activeRecord }
    private val sleepNotificationScheduler = mockk<SleepNotificationScheduler>(relaxed = true)

    private fun record(id: Long) = SleepRecord(
        id = id,
        startTime = Instant.now(),
        sleepType = SleepType.NAP,
    )

    private fun canceller(scope: TestScope) =
        StaleSleepNotificationCanceller(sleepRepository, sleepNotificationScheduler, scope)

    @Test
    fun `does not cancel on the initial null emission`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val canceller = canceller(scope)

        canceller.start()
        advanceUntilIdle()

        verify(exactly = 0) { sleepNotificationScheduler.cancel() }
    }

    @Test
    fun `cancels exactly once when the active record transitions to null`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val canceller = canceller(scope)

        canceller.start()
        advanceUntilIdle()

        activeRecord.value = record(id = 1)
        advanceUntilIdle()
        activeRecord.value = null
        advanceUntilIdle()

        verify(exactly = 1) { sleepNotificationScheduler.cancel() }
    }

    @Test
    fun `does not cancel on a field update of the same active record`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val canceller = canceller(scope)

        canceller.start()
        advanceUntilIdle()

        activeRecord.value = record(id = 1)
        advanceUntilIdle()
        // Same id, different field (e.g. notes edited while still active) — id-based distinctUntilChanged
        // must not treat this as a fresh emission that resets tracking.
        activeRecord.value = record(id = 1).copy(notes = "woke briefly")
        advanceUntilIdle()

        verify(exactly = 0) { sleepNotificationScheduler.cancel() }
    }

    @Test
    fun `cancels twice across two separate active-to-null transitions`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val canceller = canceller(scope)

        canceller.start()
        advanceUntilIdle()

        activeRecord.value = record(id = 1)
        advanceUntilIdle()
        activeRecord.value = null
        advanceUntilIdle()
        activeRecord.value = record(id = 2)
        advanceUntilIdle()
        activeRecord.value = null
        advanceUntilIdle()

        verify(exactly = 2) { sleepNotificationScheduler.cancel() }
    }
}
