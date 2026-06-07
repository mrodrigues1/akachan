package com.babytracker.receiver

import android.content.Context
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRecommendationRepository
import com.babytracker.domain.repository.SleepRecommendationSnapshot
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.manager.PredictiveSleepScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class PredictiveSleepBootReceiverTest {

    private lateinit var settings: SettingsRepository
    private lateinit var useCase: PredictSleepWindowUseCase
    private lateinit var scheduler: PredictiveSleepScheduler
    private lateinit var sleepRepository: SleepRepository
    private lateinit var recommendationRepository: SleepRecommendationRepository
    private lateinit var context: Context
    private lateinit var receiver: PredictiveSleepBootReceiver

    @BeforeEach
    fun setup() {
        settings = mockk()
        useCase = mockk()
        scheduler = mockk(relaxed = true)
        sleepRepository = mockk(relaxed = true)
        coEvery { sleepRepository.getLatestRecord() } returns null
        recommendationRepository = mockk(relaxed = true)
        coEvery { recommendationRepository.getLatestScheduledRecommendation() } returns null
        context = mockk(relaxed = true)
        receiver = PredictiveSleepBootReceiver().apply {
            settingsRepository = settings
            predictSleepWindow = useCase
            this.scheduler = this@PredictiveSleepBootReceiverTest.scheduler
            this.sleepRepository = this@PredictiveSleepBootReceiverTest.sleepRepository
            sleepRecommendationRepository = this@PredictiveSleepBootReceiverTest.recommendationRepository
        }
        every { settings.getPredictiveSleepLeadMinutes() } returns flowOf(15)
        every { settings.getQuietHoursStartMinute() } returns flowOf(0)
        every { settings.getQuietHoursEndMinute() } returns flowOf(0)
    }

    @Test
    fun `reschedules when enabled and valid future window`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(3600)
        val window = SleepPredictionState.Window(
            SleepWindow(
                windowStart = bestEstimate.minusSeconds(900),
                windowEnd = bestEstimate.plusSeconds(900),
                bestEstimate = bestEstimate,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedPrompt = null,
                safetyPrompt = "",
            )
        )
        every { settings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(window)

        receiver.handle(context)

        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate, any()) }
    }

    @Test
    fun `does not reschedule when feature is disabled`() = runTest {
        every { settings.getPredictiveSleepEnabled() } returns flowOf(false)

        receiver.handle(context)

        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any(), any()) }
    }

    @Test
    fun `cancels when state is not a window`() = runTest {
        every { settings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(SleepPredictionState.Unavailable("no data"))

        receiver.handle(context)

        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any(), any()) }
    }

    @Test
    fun `cancels when window is stale (past bestEstimate plus tolerance)`() = runTest {
        val bestEstimate = Instant.now().minusSeconds(PredictiveSleepReceiver.MAX_STALE_MINUTES * 60 + 1)
        val window = SleepPredictionState.Window(
            SleepWindow(
                windowStart = bestEstimate.minusSeconds(900),
                windowEnd = bestEstimate.plusSeconds(900),
                bestEstimate = bestEstimate,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedPrompt = null,
                safetyPrompt = "",
            )
        )
        every { settings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(window)

        receiver.handle(context)

        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any(), any()) }
    }

    @Test
    fun `fires immediately when rebooted inside lead window but bestEstimate is still upcoming`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(600)
        val window = SleepPredictionState.Window(
            SleepWindow(
                windowStart = bestEstimate.minusSeconds(900),
                windowEnd = bestEstimate.plusSeconds(900),
                bestEstimate = bestEstimate,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedPrompt = null,
                safetyPrompt = "",
            )
        )
        every { settings.getPredictiveSleepLeadMinutes() } returns flowOf(20)
        every { settings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(window)

        receiver.handle(context)

        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate, any()) }
        verify(exactly = 0) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `passes recommendation ID when snapshot anchor matches current sleep record`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(3600)
        val window = SleepPredictionState.Window(
            SleepWindow(
                windowStart = bestEstimate.minusSeconds(900),
                windowEnd = bestEstimate.plusSeconds(900),
                bestEstimate = bestEstimate,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedPrompt = null,
                safetyPrompt = "",
            )
        )
        val snapshot = SleepRecommendationSnapshot(
            id = 42L,
            anchorSleepId = 7L,
            windowStartMs = bestEstimate.minusSeconds(900).toEpochMilli(),
            windowEndMs = bestEstimate.plusSeconds(900).toEpochMilli(),
            bestEstimateMs = bestEstimate.toEpochMilli(),
        )
        val anchorRecord = mockk<com.babytracker.domain.model.SleepRecord> { every { id } returns 7L }
        every { settings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(window)
        coEvery { sleepRepository.getLatestRecord() } returns anchorRecord
        coEvery { recommendationRepository.getLatestScheduledRecommendation() } returns snapshot

        receiver.handle(context)

        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate, 42L) }
    }

    @Test
    fun `passes 0L when snapshot anchor does not match current sleep record (stale recommendation)`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(3600)
        val window = SleepPredictionState.Window(
            SleepWindow(
                windowStart = bestEstimate.minusSeconds(900),
                windowEnd = bestEstimate.plusSeconds(900),
                bestEstimate = bestEstimate,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedPrompt = null,
                safetyPrompt = "",
            )
        )
        val snapshot = SleepRecommendationSnapshot(
            id = 42L,
            anchorSleepId = 5L,
            windowStartMs = bestEstimate.minusSeconds(900).toEpochMilli(),
            windowEndMs = bestEstimate.plusSeconds(900).toEpochMilli(),
            bestEstimateMs = bestEstimate.toEpochMilli(),
        )
        // Current latest record has id=9 — different anchor than snapshot's anchorSleepId=5
        val anchorRecord = mockk<com.babytracker.domain.model.SleepRecord> { every { id } returns 9L }
        every { settings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(window)
        coEvery { sleepRepository.getLatestRecord() } returns anchorRecord
        coEvery { recommendationRepository.getLatestScheduledRecommendation() } returns snapshot

        receiver.handle(context)

        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate, 0L) }
    }

    @Test
    fun `passes 0L recommendation ID when no scheduled recommendation exists`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(3600)
        val window = SleepPredictionState.Window(
            SleepWindow(
                windowStart = bestEstimate.minusSeconds(900),
                windowEnd = bestEstimate.plusSeconds(900),
                bestEstimate = bestEstimate,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedPrompt = null,
                safetyPrompt = "",
            )
        )
        every { settings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(window)
        coEvery { recommendationRepository.getLatestScheduledRecommendation() } returns null

        receiver.handle(context)

        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate, 0L) }
    }

    @Test
    fun `shouldHandle accepts boot and time-set actions`() {
        assertTrue(receiver.shouldHandle("android.intent.action.BOOT_COMPLETED"))
        assertTrue(receiver.shouldHandle("android.intent.action.TIME_SET"))
    }

    @Test
    fun `shouldHandle ignores unrelated and null actions`() {
        assertFalse(receiver.shouldHandle("android.intent.action.AIRPLANE_MODE"))
        assertFalse(receiver.shouldHandle(null))
    }
}
