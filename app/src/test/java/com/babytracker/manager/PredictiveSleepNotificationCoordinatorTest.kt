package com.babytracker.manager

import android.app.NotificationManager
import android.content.Context
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class PredictiveSleepNotificationCoordinatorTest {

    private fun windowState(bestEstimate: Instant): SleepPredictionState.Window =
        SleepPredictionState.Window(
            window = SleepWindow(
                windowStart = bestEstimate.minusSeconds(1800),
                windowEnd = bestEstimate.plusSeconds(1800),
                bestEstimate = bestEstimate,
                confidence = Confidence.MEDIUM,
                reasons = emptyList(),
                feedPrompt = null,
                safetyPrompt = "Safe to sleep",
            ),
        )

    private fun needMoreData(): SleepPredictionState.NeedMoreData =
        SleepPredictionState.NeedMoreData(
            progress = EvidenceProgress(
                completedIntervals = 1,
                requiredIntervals = 3,
                localDays = 1,
                requiredLocalDays = 2,
                hint = "Need more data",
            ),
        )

    @Test
    fun `schedules alarm when enabled and state is Window in the future`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(3600)
        val state = windowState(bestEstimate)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(state),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate) }
        verify(exactly = 0) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `cancels alarm when toggle is off`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val enabled = MutableStateFlow(true)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(windowState(Instant.now().plusSeconds(3600))),
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
    fun `cancels alarm when state is NeedMoreData (not Window)`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val stateFlow = MutableStateFlow<SleepPredictionState>(windowState(Instant.now().plusSeconds(3600)))
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = stateFlow,
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        stateFlow.value = needMoreData()
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `cancels alarm when state is CurrentlySleeping`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(SleepPredictionState.CurrentlySleeping),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any()) }
        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
    }

    @Test
    fun `skips scheduling and cancels when trigger is in the past`() = runTest {
        // bestEstimate = now + 5m, leadMinutes = 15 → triggerAt = now − 10m → cancel
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(windowState(Instant.now().plusSeconds(300))),
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
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(3600)
        val lead = MutableStateFlow(15)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
            enabledFlow = MutableStateFlow(true),
            leadFlow = lead,
        )
        coordinator.start()
        advanceTimeBy(300)
        lead.value = 30
        advanceTimeBy(300)
        verify(exactly = 2) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate) }
    }

    @Test
    fun `debounces rapid emissions inside 200ms`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val stateFlow = MutableStateFlow<SleepPredictionState>(windowState(Instant.now().plusSeconds(3600)))
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = stateFlow,
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
        )
        coordinator.start()
        advanceTimeBy(50)
        stateFlow.value = windowState(Instant.now().plusSeconds(3700))
        advanceTimeBy(50)
        stateFlow.value = windowState(Instant.now().plusSeconds(3800))
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), any()) }
    }

    @Test
    fun `cancels alarm when trigger falls inside quiet hours`() = runTest {
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(3600)
        val leadMinutes = 15
        val triggerAt = bestEstimate.minusSeconds(leadMinutes * 60L)
        val triggerMinuteOfDay = triggerAt.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            .let { it.hour * 60 + it.minute }
        val quietStart = triggerMinuteOfDay
        val quietEnd = (triggerMinuteOfDay + 60) % 1440
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
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
        val scheduler = mockk<PredictiveSleepScheduler>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(3600)
        val coordinator = buildCoordinator(
            scheduler = scheduler,
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
            quietStartFlow = MutableStateFlow(0),
            quietEndFlow = MutableStateFlow(0),
        )
        coordinator.start()
        advanceTimeBy(300)
        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate) }
        verify(exactly = 0) { scheduler.cancelPredictiveReminder() }
    }

    private fun TestScope.buildCoordinator(
        scheduler: PredictiveSleepScheduler,
        stateFlow: MutableStateFlow<SleepPredictionState>,
        enabledFlow: MutableStateFlow<Boolean>,
        leadFlow: MutableStateFlow<Int>,
        quietStartFlow: MutableStateFlow<Int> = MutableStateFlow(0),
        quietEndFlow: MutableStateFlow<Int> = MutableStateFlow(0),
    ): PredictiveSleepNotificationCoordinator {
        val useCase = mockk<PredictSleepWindowUseCase>().also {
            every { it.invoke() } returns stateFlow
        }
        val settings = mockk<SettingsRepository>().also {
            every { it.getPredictiveSleepEnabled() } returns enabledFlow
            every { it.getPredictiveSleepLeadMinutes() } returns leadFlow
            every { it.getQuietHoursStartMinute() } returns quietStartFlow
            every { it.getQuietHoursEndMinute() } returns quietEndFlow
        }
        val nm = mockk<NotificationManager>(relaxed = true)
        val context = mockk<Context>(relaxed = true).also {
            every { it.getSystemService(NotificationManager::class.java) } returns nm
        }
        return PredictiveSleepNotificationCoordinator(
            predictSleepWindow = useCase,
            settingsRepository = settings,
            scheduler = scheduler,
            context = context,
            applicationScope = backgroundScope,
        )
    }
}
