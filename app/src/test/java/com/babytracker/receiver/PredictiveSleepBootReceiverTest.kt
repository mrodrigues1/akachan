package com.babytracker.receiver

import android.content.Context
import android.content.Intent
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRecommendationRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.domain.repository.SleepRecommendationSnapshot
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.manager.PredictiveSleepScheduler
import com.babytracker.manager.SleepNotificationScheduler
import com.babytracker.util.PREDICTION_MAX_STALE_MINUTES
import io.mockk.coEvery
import io.mockk.coVerify
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
    private lateinit var sleepSettings: SleepSettingsRepository
    private lateinit var useCase: PredictSleepWindowUseCase
    private lateinit var scheduler: PredictiveSleepScheduler
    private lateinit var sleepRepository: SleepRepository
    private lateinit var recommendationRepository: SleepRecommendationRepository
    private lateinit var featureToggleRepository: FeatureToggleRepository
    private lateinit var sleepNotificationScheduler: SleepNotificationScheduler
    private lateinit var context: Context
    private lateinit var receiver: PredictiveSleepBootReceiver

    @BeforeEach
    fun setup() {
        settings = mockk()
        sleepSettings = mockk()
        useCase = mockk()
        scheduler = mockk(relaxed = true)
        sleepRepository = mockk(relaxed = true)
        coEvery { sleepRepository.getLatestRecord() } returns null
        coEvery { sleepRepository.getActiveRecord() } returns null
        recommendationRepository = mockk(relaxed = true)
        coEvery { recommendationRepository.getLatestScheduledRecommendation() } returns null
        featureToggleRepository = mockk()
        every { featureToggleRepository.getEnabledFeatures() } returns flowOf(AppFeature.ALL)
        sleepNotificationScheduler = mockk(relaxed = true)
        context = mockk(relaxed = true)
        receiver = PredictiveSleepBootReceiver().apply {
            settingsRepository = settings
            sleepSettingsRepository = sleepSettings
            predictSleepWindow = useCase
            this.scheduler = this@PredictiveSleepBootReceiverTest.scheduler
            this.sleepRepository = this@PredictiveSleepBootReceiverTest.sleepRepository
            sleepRecommendationRepository = this@PredictiveSleepBootReceiverTest.recommendationRepository
            this.featureToggleRepository = this@PredictiveSleepBootReceiverTest.featureToggleRepository
            this.sleepNotificationScheduler = this@PredictiveSleepBootReceiverTest.sleepNotificationScheduler
        }
        every { sleepSettings.getPredictiveSleepLeadMinutes() } returns flowOf(15)
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
                sleepType = SleepType.NAP,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedDue = false,
            )
        )
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(window)

        receiver.handle(context)

        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate, any()) }
    }

    @Test
    fun `does not reschedule when feature is disabled`() = runTest {
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(false)

        receiver.handle(context)

        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any(), any()) }
    }

    @Test
    fun `restores active sleep notification when a session is in progress and SLEEP feature enabled`() = runTest {
        val startTime = Instant.now().minusSeconds(1800)
        val active = SleepRecord(id = 11L, startTime = startTime, sleepType = SleepType.NIGHT_SLEEP)
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(false)
        coEvery { sleepRepository.getActiveRecord() } returns active

        receiver.handle(context)

        coVerify(exactly = 1) { sleepNotificationScheduler.show(11L, SleepType.NIGHT_SLEEP, startTime) }
    }

    @Test
    fun `does not restore a notification when no sleep record is active`() = runTest {
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(false)
        coEvery { sleepRepository.getActiveRecord() } returns null

        receiver.handle(context)

        coVerify(exactly = 0) { sleepNotificationScheduler.show(any(), any(), any()) }
    }

    @Test
    fun `does not restore notification when SLEEP feature is disabled even if a session is active`() = runTest {
        val active = SleepRecord(id = 11L, startTime = Instant.now().minusSeconds(1800), sleepType = SleepType.NIGHT_SLEEP)
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(false)
        every { featureToggleRepository.getEnabledFeatures() } returns flowOf(emptySet())
        coEvery { sleepRepository.getActiveRecord() } returns active

        receiver.handle(context)

        coVerify(exactly = 0) { sleepNotificationScheduler.show(any(), any(), any()) }
    }

    @Test
    fun `restores notification even when predictive-sleep setting is disabled (not gated on the predictive toggle)`() = runTest {
        val startTime = Instant.now().minusSeconds(1800)
        val active = SleepRecord(id = 11L, startTime = startTime, sleepType = SleepType.NAP)
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(false)
        every { featureToggleRepository.getEnabledFeatures() } returns flowOf(setOf(AppFeature.SLEEP))
        coEvery { sleepRepository.getActiveRecord() } returns active

        receiver.handle(context)

        coVerify(exactly = 1) { sleepNotificationScheduler.show(11L, SleepType.NAP, startTime) }
    }

    @Test
    fun `does not restore notification on TIME_SET or TIMEZONE_CHANGED, only on BOOT_COMPLETED`() = runTest {
        val active = SleepRecord(id = 11L, startTime = Instant.now().minusSeconds(1800), sleepType = SleepType.NIGHT_SLEEP)
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(false)
        coEvery { sleepRepository.getActiveRecord() } returns active

        receiver.handle(context, Intent.ACTION_TIME_CHANGED)
        receiver.handle(context, Intent.ACTION_TIMEZONE_CHANGED)

        coVerify(exactly = 0) { sleepNotificationScheduler.show(any(), any(), any()) }
    }

    @Test
    fun `does not reschedule when SLEEP app feature is disabled even if predictive setting is enabled`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(3600)
        val window = SleepPredictionState.Window(
            SleepWindow(
                windowStart = bestEstimate.minusSeconds(900),
                windowEnd = bestEstimate.plusSeconds(900),
                bestEstimate = bestEstimate,
                sleepType = SleepType.NAP,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedDue = false,
            )
        )
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(window)
        every { featureToggleRepository.getEnabledFeatures() } returns flowOf(emptySet())

        receiver.handle(context)

        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any(), any()) }
        verify(exactly = 0) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `reschedules when predictive setting enabled and SLEEP feature enabled`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(3600)
        val window = SleepPredictionState.Window(
            SleepWindow(
                windowStart = bestEstimate.minusSeconds(900),
                windowEnd = bestEstimate.plusSeconds(900),
                bestEstimate = bestEstimate,
                sleepType = SleepType.NAP,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedDue = false,
            )
        )
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(window)
        every { featureToggleRepository.getEnabledFeatures() } returns flowOf(setOf(AppFeature.SLEEP))

        receiver.handle(context)

        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate, any()) }
    }

    @Test
    fun `cancels when state is not a window`() = runTest {
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(SleepPredictionState.Unavailable("no data"))

        receiver.handle(context)

        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any(), any()) }
    }

    @Test
    fun `cancels when window is stale (past bestEstimate plus tolerance)`() = runTest {
        val bestEstimate = Instant.now().minusSeconds(PREDICTION_MAX_STALE_MINUTES * 60 + 1)
        val window = SleepPredictionState.Window(
            SleepWindow(
                windowStart = bestEstimate.minusSeconds(900),
                windowEnd = bestEstimate.plusSeconds(900),
                bestEstimate = bestEstimate,
                sleepType = SleepType.NAP,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedDue = false,
            )
        )
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(true)
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
                sleepType = SleepType.NAP,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedDue = false,
            )
        )
        every { sleepSettings.getPredictiveSleepLeadMinutes() } returns flowOf(20)
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(true)
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
                sleepType = SleepType.NAP,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedDue = false,
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
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(true)
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
                sleepType = SleepType.NAP,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedDue = false,
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
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(true)
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
                sleepType = SleepType.NAP,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
                feedDue = false,
            )
        )
        every { sleepSettings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(window)
        coEvery { recommendationRepository.getLatestScheduledRecommendation() } returns null

        receiver.handle(context)

        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate, 0L) }
    }

    @Test
    fun `shouldHandle accepts boot, time-set and timezone-change actions`() {
        assertTrue(receiver.shouldHandle("android.intent.action.BOOT_COMPLETED"))
        assertTrue(receiver.shouldHandle("android.intent.action.TIME_SET"))
        assertTrue(receiver.shouldHandle("android.intent.action.TIMEZONE_CHANGED"))
    }

    @Test
    fun `shouldHandle ignores unrelated and null actions`() {
        assertFalse(receiver.shouldHandle("android.intent.action.AIRPLANE_MODE"))
        assertFalse(receiver.shouldHandle(null))
    }
}
