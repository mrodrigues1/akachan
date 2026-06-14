package com.babytracker.manager

import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.repository.FeedSettingsRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class PredictiveFeedNotificationCoordinatorTest {

    private fun prediction(at: Instant) = FeedPrediction(
        predictedAt = at,
        averageIntervalMinutes = 180,
        sampleSize = 5,
        isOverdue = false,
        minutesUntil = 60,
    )

    @Test
    fun `schedules alarm when enabled and prediction is in the future`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val p = prediction(Instant.now().plusSeconds(3600))
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = MutableStateFlow(p),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), p.predictedAt) }
        verify(exactly = 0) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `cancels alarm when toggle is off`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val enabled = MutableStateFlow(true)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = MutableStateFlow(prediction(Instant.now().plusSeconds(3600))),
            enabledFlow = enabled,
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        enabled.value = false
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `cancels alarm when prediction becomes null`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val predictionFlow = MutableStateFlow<FeedPrediction?>(prediction(Instant.now().plusSeconds(3600)))
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = predictionFlow,
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        predictionFlow.value = null
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `skips scheduling and cancels when trigger is in the past`() = runTest {
        // prediction.predictedAt = now + 5m, leadMinutes = 15 -> trigger = now - 10m -> cancel.
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = MutableStateFlow(prediction(Instant.now().plusSeconds(300))),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any()) }
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `reschedules when lead minutes change`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val p = prediction(Instant.now().plusSeconds(3600))
        val lead = MutableStateFlow(15)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = MutableStateFlow(p),
            enabledFlow = MutableStateFlow(true),
            leadFlow = lead,
        )
        coordinator.start()
        advanceTimeBy(300)
        lead.value = 30
        advanceTimeBy(300)
        verify(exactly = 2) { scheduler.schedulePredictiveReminderAt(any(), p.predictedAt) }
    }

    @Test
    fun `debounces rapid emissions inside 200ms`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val predictionFlow = MutableStateFlow<FeedPrediction?>(prediction(Instant.now().plusSeconds(3600)))
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = predictionFlow,
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(50)
        predictionFlow.value = prediction(Instant.now().plusSeconds(3700))
        advanceTimeBy(50)
        predictionFlow.value = prediction(Instant.now().plusSeconds(3800))
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), any()) }
    }

    @Test
    fun `cancels alarm when trigger falls inside quiet hours`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val predictedAt = Instant.now().plusSeconds(3600)
        val leadMinutes = 15
        val triggerAt = predictedAt.minusSeconds(leadMinutes * 60L)
        val triggerMinuteOfDay = triggerAt.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            .let { it.hour * 60 + it.minute }
        // Quiet window starting at the trigger minute, 1 hour wide — always contains triggerMinuteOfDay.
        val quietStart = triggerMinuteOfDay
        val quietEnd = (triggerMinuteOfDay + 60) % 1440
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = MutableStateFlow(prediction(predictedAt)),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(leadMinutes),
            quietStartFlow = MutableStateFlow(quietStart),
            quietEndFlow = MutableStateFlow(quietEnd),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any()) }
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `schedules alarm when quiet hours are disabled (start equals end)`() = runTest {
        val scheduler = mockk<PredictiveFeedScheduler>(relaxed = true)
        val p = prediction(Instant.now().plusSeconds(3600))
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            predictionFlow = MutableStateFlow(p),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
            quietStartFlow = MutableStateFlow(0),
            quietEndFlow = MutableStateFlow(0),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), p.predictedAt) }
        verify(exactly = 0) { scheduler.cancelPredictiveReminder() }
    }

    private fun TestScope.buildCoordinator(
        scheduler: PredictiveFeedScheduler,
        predictionFlow: MutableStateFlow<FeedPrediction?>,
        enabledFlow: MutableStateFlow<Boolean>,
        leadFlow: MutableStateFlow<Int>,
        quietStartFlow: MutableStateFlow<Int> = MutableStateFlow(0),
        quietEndFlow: MutableStateFlow<Int> = MutableStateFlow(0),
    ): PredictiveFeedNotificationCoordinator {
        val useCase = mockk<PredictNextFeedUseCase>().also {
            every { it.invoke() } returns predictionFlow
        }
        val feedSettings = mockk<FeedSettingsRepository>().also {
            every { it.getPredictiveEnabled() } returns enabledFlow
            every { it.getPredictiveLeadMinutes() } returns leadFlow
        }
        val settings = mockk<SettingsRepository>().also {
            every { it.getQuietHoursStartMinute() } returns quietStartFlow
            every { it.getQuietHoursEndMinute() } returns quietEndFlow
        }
        return PredictiveFeedNotificationCoordinator(
            predictNextFeed = useCase,
            feedSettingsRepository = feedSettings,
            settingsRepository = settings,
            scheduler = scheduler,
            applicationScope = backgroundScope,
        )
    }
}
