package com.babytracker.manager

import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.CreateSleepRecommendationFeedbackUseCase
import com.babytracker.domain.usecase.sleep.PersistSleepRecommendationUseCase
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.domain.usecase.sleep.SleepRecommendationUseCases
import com.babytracker.domain.usecase.sleep.UpdateRecommendationLifecycleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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

    @Test
    fun `persists recommendation when Window state received and enabled`() = runTest {
        val persist = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
        coEvery { persist(any(), any(), any()) } returns 1L
        val bestEstimate = Instant.now().plusSeconds(3600)

        val coordinator = buildCoordinator(
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
            recommendation = SleepRecommendationUseCases(
                persist = persist,
                updateLifecycle = mockk(relaxed = true),
                createFeedback = mockk(relaxed = true),
            ),
        )
        coordinator.start()
        advanceTimeBy(300)

        coVerify(atLeast = 1) { persist(any(), any(), any()) }
    }

    @Test
    fun `writes SCHEDULED lifecycle when alarm is set`() = runTest {
        val persist = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
        coEvery { persist(any(), any(), any()) } returns 42L
        val updateLifecycle = mockk<UpdateRecommendationLifecycleUseCase>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(3600)

        val coordinator = buildCoordinator(
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(15),
            recommendation = SleepRecommendationUseCases(
                persist = persist,
                updateLifecycle = updateLifecycle,
                createFeedback = mockk(relaxed = true),
            ),
        )
        coordinator.start()
        advanceTimeBy(300)

        coVerify { updateLifecycle(42L, RecommendationLifecycle.SCHEDULED) }
    }

    @Test
    fun `writes SUPERSEDED lifecycle when feature is disabled`() = runTest {
        val persist = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
        coEvery { persist(any(), any(), any()) } returns 55L
        val updateLifecycle = mockk<UpdateRecommendationLifecycleUseCase>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(3600)
        val enabled = MutableStateFlow(true)

        val coordinator = buildCoordinator(
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
            enabledFlow = enabled,
            leadFlow = MutableStateFlow(15),
            recommendation = SleepRecommendationUseCases(
                persist = persist,
                updateLifecycle = updateLifecycle,
                createFeedback = mockk(relaxed = true),
            ),
        )
        coordinator.start()
        advanceTimeBy(300)
        enabled.value = false
        advanceTimeBy(300)

        coVerify { updateLifecycle(55L, RecommendationLifecycle.SUPERSEDED) }
    }

    @Test
    fun `creates QUIET_HOURS_SUPPRESSED feedback when trigger falls in quiet hours`() = runTest {
        val persist = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
        coEvery { persist(any(), any(), any()) } returns 77L
        val createFeedback = mockk<CreateSleepRecommendationFeedbackUseCase>(relaxed = true)
        // bestEstimate = now + 2h, lead = 60min → triggerAt = now + 1h (future, in full-day quiet hours)
        val bestEstimate = Instant.now().plusSeconds(7200)

        val coordinator = buildCoordinator(
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(60),
            quietStartFlow = MutableStateFlow(0),
            quietEndFlow = MutableStateFlow(1439),
            recommendation = SleepRecommendationUseCases(
                persist = persist,
                updateLifecycle = mockk(relaxed = true),
                createFeedback = createFeedback,
            ),
        )
        coordinator.start()
        advanceTimeBy(300)

        coVerify { createFeedback(77L, RecommendationOutcome.QUIET_HOURS_SUPPRESSED) }
    }

    @Test
    fun `quiet-hours suppressed feedback is created only once per recommendation across repeated reconciles`() = runTest {
        val persist = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
        coEvery { persist(any(), any(), any()) } returns 88L
        val createFeedback = mockk<CreateSleepRecommendationFeedbackUseCase>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(7200)
        val stateFlow = MutableStateFlow<SleepPredictionState>(windowState(bestEstimate))

        val coordinator = buildCoordinator(
            stateFlow = stateFlow,
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(60),
            quietStartFlow = MutableStateFlow(0),
            quietEndFlow = MutableStateFlow(1439),
            recommendation = SleepRecommendationUseCases(
                persist = persist,
                updateLifecycle = mockk(relaxed = true),
                createFeedback = createFeedback,
            ),
        )
        coordinator.start()
        advanceTimeBy(300)
        stateFlow.value = windowState(bestEstimate.plusSeconds(1))
        advanceTimeBy(300)

        coVerify(exactly = 1) { createFeedback(88L, RecommendationOutcome.QUIET_HOURS_SUPPRESSED) }
    }

    @Test
    fun `sleep completion does not create feedback when recommendation was suppressed by quiet hours`() = runTest {
        val persist = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
        coEvery { persist(any(), any(), any()) } returns 99L
        val createFeedback = mockk<CreateSleepRecommendationFeedbackUseCase>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(7200)
        val completedRecord = mockk<com.babytracker.domain.model.SleepRecord> {
            every { id } returns 5L
            every { isInProgress } returns false
            every { startTime } returns bestEstimate
        }
        val sleepFlow = MutableStateFlow<com.babytracker.domain.model.SleepRecord?>(completedRecord)
        val anchor = mockk<com.babytracker.domain.model.SleepRecord>().also { every { it.id } returns 1L }
        val customSleepRepo = mockk<SleepRepository>(relaxed = true).also {
            coEvery { it.getLatestRecord() } returns anchor
            every { it.observeLatestRecord() } returns sleepFlow
        }

        val coordinator = buildCoordinator(
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
            enabledFlow = MutableStateFlow(true),
            leadFlow = MutableStateFlow(60),
            quietStartFlow = MutableStateFlow(0),
            quietEndFlow = MutableStateFlow(1439),
            sleepRepository = customSleepRepo,
            recommendation = SleepRecommendationUseCases(
                persist = persist,
                updateLifecycle = mockk(relaxed = true),
                createFeedback = createFeedback,
            ),
        )
        coordinator.start()
        advanceTimeBy(300)

        coVerify(exactly = 0) { createFeedback(any(), RecommendationOutcome.ACTED_IN_WINDOW, any(), any(), any()) }
        coVerify(exactly = 0) { createFeedback(any(), RecommendationOutcome.ACTED_OUTSIDE, any(), any(), any()) }
    }

    @Test
    fun `sleep completion does not create ACTED feedback after alarm cancelled by past-trigger re-reconcile`() = runTest {
        // Regression: scheduled window state must be cleared when alarm is cancelled due to past-trigger,
        // otherwise sleep-completion collector creates false ACTED feedback.
        val persist = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
        coEvery { persist(any(), any(), any()) } returns 10L
        val createFeedback = mockk<CreateSleepRecommendationFeedbackUseCase>(relaxed = true)
        val bestEstimate = Instant.now().plusSeconds(3600)
        val completedRecord = mockk<com.babytracker.domain.model.SleepRecord> {
            every { id } returns 7L
            every { isInProgress } returns false
            every { startTime } returns bestEstimate
        }
        val sleepFlow = MutableStateFlow<com.babytracker.domain.model.SleepRecord?>(null)
        val anchor = mockk<com.babytracker.domain.model.SleepRecord>().also { every { it.id } returns 1L }
        val customSleepRepo = mockk<SleepRepository>(relaxed = true).also {
            coEvery { it.getLatestRecord() } returns anchor
            every { it.observeLatestRecord() } returns sleepFlow
        }
        // lead = 15 → triggerAt = now + 45min (future, alarm scheduled on first reconcile)
        val leadFlow = MutableStateFlow(15)

        val coordinator = buildCoordinator(
            stateFlow = MutableStateFlow(windowState(bestEstimate)),
            enabledFlow = MutableStateFlow(true),
            leadFlow = leadFlow,
            sleepRepository = customSleepRepo,
            recommendation = SleepRecommendationUseCases(
                persist = persist,
                updateLifecycle = mockk(relaxed = true),
                createFeedback = createFeedback,
            ),
        )
        coordinator.start()
        advanceTimeBy(300) // first reconcile: alarm scheduled, window state set

        // Increase lead so triggerAt = bestEstimate - 90min < now → past-trigger, alarm cancelled
        leadFlow.value = 90
        advanceTimeBy(300) // second reconcile: past-trigger, window state must be cleared

        // Sleep completion arrives after alarm was cancelled
        sleepFlow.value = completedRecord
        advanceTimeBy(300)

        coVerify(exactly = 0) { createFeedback(any(), RecommendationOutcome.ACTED_IN_WINDOW, any(), any(), any()) }
        coVerify(exactly = 0) { createFeedback(any(), RecommendationOutcome.ACTED_OUTSIDE, any(), any(), any()) }
    }

    private fun TestScope.buildCoordinator(
        scheduler: PredictiveSleepScheduler = mockk(relaxed = true),
        stateFlow: MutableStateFlow<SleepPredictionState>,
        enabledFlow: MutableStateFlow<Boolean>,
        leadFlow: MutableStateFlow<Int>,
        quietStartFlow: MutableStateFlow<Int> = MutableStateFlow(0),
        quietEndFlow: MutableStateFlow<Int> = MutableStateFlow(0),
        sleepRepository: SleepRepository = defaultSleepRepository(),
        recommendation: SleepRecommendationUseCases = defaultRecommendationUseCases(),
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
        return PredictiveSleepNotificationCoordinator(
            predictSleepWindow = useCase,
            settingsRepository = settings,
            scheduler = scheduler,
            sleepRepository = sleepRepository,
            recommendation = recommendation,
            applicationScope = backgroundScope,
        )
    }

    private fun defaultRecommendationUseCases(): SleepRecommendationUseCases {
        val persist = mockk<PersistSleepRecommendationUseCase>(relaxed = true)
        val update = mockk<UpdateRecommendationLifecycleUseCase>(relaxed = true)
        val feedback = mockk<CreateSleepRecommendationFeedbackUseCase>(relaxed = true)
        return SleepRecommendationUseCases(persist, update, feedback)
    }

    private fun defaultSleepRepository(): SleepRepository {
        val mock = mockk<SleepRepository>(relaxed = true)
        val anchor = mockk<com.babytracker.domain.model.SleepRecord>(relaxed = true)
        coEvery { mock.getLatestRecord() } returns anchor
        every { mock.observeLatestRecord() } returns MutableStateFlow<com.babytracker.domain.model.SleepRecord?>(null)
        return mock
    }
}
