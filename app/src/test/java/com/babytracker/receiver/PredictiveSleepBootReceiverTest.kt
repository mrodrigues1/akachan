package com.babytracker.receiver

import android.content.Context
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.manager.PredictiveSleepScheduler
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
    private lateinit var context: Context
    private lateinit var receiver: PredictiveSleepBootReceiver

    @BeforeEach
    fun setup() {
        settings = mockk()
        useCase = mockk()
        scheduler = mockk(relaxed = true)
        context = mockk(relaxed = true)
        receiver = PredictiveSleepBootReceiver().apply {
            settingsRepository = settings
            predictSleepWindow = useCase
            this.scheduler = this@PredictiveSleepBootReceiverTest.scheduler
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

        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate) }
    }

    @Test
    fun `does not reschedule when feature is disabled`() = runTest {
        every { settings.getPredictiveSleepEnabled() } returns flowOf(false)

        receiver.handle(context)

        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any()) }
    }

    @Test
    fun `cancels when state is not a window`() = runTest {
        every { settings.getPredictiveSleepEnabled() } returns flowOf(true)
        every { useCase() } returns flowOf(SleepPredictionState.Unavailable("no data"))

        receiver.handle(context)

        verify(exactly = 1) { scheduler.cancelPredictiveReminder() }
        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any()) }
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
        verify(exactly = 0) { scheduler.schedulePredictiveReminderAt(any(), any()) }
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

        verify(exactly = 1) { scheduler.schedulePredictiveReminderAt(any(), bestEstimate) }
        verify(exactly = 0) { scheduler.cancelPredictiveReminder() }
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
