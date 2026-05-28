package com.babytracker.widget

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class WidgetSyncManagerTest {

    private val feed = BreastfeedingSession(
        id = 1, startTime = Instant.parse("2026-05-27T10:00:00Z"),
        startingSide = BreastSide.LEFT,
    )
    private val sleep = SleepRecord(
        id = 1, startTime = Instant.parse("2026-05-27T11:00:00Z"),
        sleepType = SleepType.NAP,
    )
    private val baby = Baby(name = "Aria", birthDate = LocalDate.of(2026, 1, 1))

    private fun buildManager(
        feedFlow: MutableSharedFlow<BreastfeedingSession?>,
        sleepFlow: MutableSharedFlow<SleepRecord?>,
        babyFlow: MutableSharedFlow<Baby?>,
        updater: WidgetUpdater,
        scope: CoroutineScope,
    ): WidgetSyncManager {
        val feedRepo: BreastfeedingRepository = mockk { every { observeLatestSession() } returns feedFlow }
        val sleepRepo: SleepRepository = mockk { every { observeLatestRecord() } returns sleepFlow }
        val babyRepo: BabyRepository = mockk { every { getBabyProfile() } returns babyFlow }
        return WidgetSyncManager(feedRepo, sleepRepo, babyRepo, updater, scope)
    }

    @Test
    fun `emits updateAll after debounce on single feed change`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val feedFlow = MutableSharedFlow<BreastfeedingSession?>(replay = 1)
        val sleepFlow = MutableSharedFlow<SleepRecord?>(replay = 1)
        val babyFlow = MutableSharedFlow<Baby?>(replay = 1)
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }

        buildManager(feedFlow, sleepFlow, babyFlow, updater, scope).start()
        feedFlow.emit(feed)
        advanceTimeBy(600)

        coVerify(exactly = 1) { updater.updateAll() }
    }

    @Test
    fun `debounce coalesces back-to-back emissions to one updateAll`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val feedFlow = MutableSharedFlow<BreastfeedingSession?>(replay = 1)
        val sleepFlow = MutableSharedFlow<SleepRecord?>(replay = 1)
        val babyFlow = MutableSharedFlow<Baby?>(replay = 1)
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }

        buildManager(feedFlow, sleepFlow, babyFlow, updater, scope).start()
        feedFlow.emit(null)
        advanceTimeBy(100)
        sleepFlow.emit(null)
        advanceTimeBy(100)
        feedFlow.emit(feed)
        advanceTimeBy(600)

        coVerify(exactly = 1) { updater.updateAll() }
    }

    @Test
    fun `first updateAll exception does not stop later emissions from refreshing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val feedFlow = MutableSharedFlow<BreastfeedingSession?>(replay = 1)
        val sleepFlow = MutableSharedFlow<SleepRecord?>(replay = 1)
        val babyFlow = MutableSharedFlow<Baby?>(replay = 1)
        val updater: WidgetUpdater = mockk {
            coEvery { updateAll() } throws IllegalStateException("glance host gone") andThen Unit
        }

        buildManager(feedFlow, sleepFlow, babyFlow, updater, scope).start()
        feedFlow.emit(feed)
        advanceTimeBy(600)
        feedFlow.emit(null)
        advanceTimeBy(600)

        coVerify(exactly = 2) { updater.updateAll() }
    }

    @Test
    fun `sleep changes also trigger updateAll`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val feedFlow = MutableSharedFlow<BreastfeedingSession?>(replay = 1)
        val sleepFlow = MutableSharedFlow<SleepRecord?>(replay = 1)
        val babyFlow = MutableSharedFlow<Baby?>(replay = 1)
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }

        buildManager(feedFlow, sleepFlow, babyFlow, updater, scope).start()
        sleepFlow.emit(sleep)
        advanceTimeBy(600)

        coVerify(exactly = 1) { updater.updateAll() }
    }

    @Test
    fun `baby profile change triggers updateAll`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val feedFlow = MutableSharedFlow<BreastfeedingSession?>(replay = 1)
        val sleepFlow = MutableSharedFlow<SleepRecord?>(replay = 1)
        val babyFlow = MutableSharedFlow<Baby?>(replay = 1)
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }

        buildManager(feedFlow, sleepFlow, babyFlow, updater, scope).start()
        babyFlow.emit(baby)
        advanceTimeBy(600)

        coVerify(exactly = 1) { updater.updateAll() }
    }
}
