package com.babytracker.receiver

import android.content.Context
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRecommendationRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.util.showPredictiveSleepReminder
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class PredictiveSleepReceiverTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sleepSettingsRepository: SleepSettingsRepository
    private lateinit var sleepRecommendationRepository: SleepRecommendationRepository
    private lateinit var featureToggleRepository: FeatureToggleRepository
    private lateinit var receiver: PredictiveSleepReceiver
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setup() {
        settingsRepository = mockk()
        sleepSettingsRepository = mockk()
        sleepRecommendationRepository = mockk(relaxed = true)
        featureToggleRepository = mockk()
        receiver = PredictiveSleepReceiver().apply {
            this.settingsRepository = this@PredictiveSleepReceiverTest.settingsRepository
            this.sleepSettingsRepository = this@PredictiveSleepReceiverTest.sleepSettingsRepository
            this.sleepRecommendationRepository = this@PredictiveSleepReceiverTest.sleepRecommendationRepository
            this.featureToggleRepository = this@PredictiveSleepReceiverTest.featureToggleRepository
        }
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(0)
        every { featureToggleRepository.getEnabledFeatures() } returns flowOf(AppFeature.ALL)
        mockkStatic("com.babytracker.util.PredictiveSleepNotificationHelperKt")
        every { showPredictiveSleepReminder(context = context, bestEstimateMs = any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `shows reminder and marks recommendation FIRED when enabled and not stale or quiet`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(60)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)

        receiver.handle(context, bestEstimate.toEpochMilli(), 42L)

        verify(exactly = 1) { showPredictiveSleepReminder(context = context, bestEstimateMs = bestEstimate.toEpochMilli()) }
        coVerify(exactly = 1) { sleepRecommendationRepository.updateLifecycle(42L, RecommendationLifecycle.FIRED) }
    }

    @Test
    fun `shows reminder but skips lifecycle update when recommendationId is not positive`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(60)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)

        receiver.handle(context, bestEstimate.toEpochMilli(), 0L)

        verify(exactly = 1) { showPredictiveSleepReminder(context = context, bestEstimateMs = bestEstimate.toEpochMilli()) }
        coVerify(exactly = 0) { sleepRecommendationRepository.updateLifecycle(any(), any()) }
    }

    @Test
    fun `does not show reminder when predictive setting is disabled at fire time`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(60)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(false)

        receiver.handle(context, bestEstimate.toEpochMilli(), 42L)

        verify(exactly = 0) { showPredictiveSleepReminder(context = any(), bestEstimateMs = any()) }
        coVerify(exactly = 0) { sleepRecommendationRepository.updateLifecycle(any(), any()) }
    }

    @Test
    fun `does not show reminder when SLEEP app feature is disabled even if predictive setting is enabled`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(60)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)
        every { featureToggleRepository.getEnabledFeatures() } returns flowOf(emptySet())

        receiver.handle(context, bestEstimate.toEpochMilli(), 42L)

        verify(exactly = 0) { showPredictiveSleepReminder(context = any(), bestEstimateMs = any()) }
        coVerify(exactly = 0) { sleepRecommendationRepository.updateLifecycle(any(), any()) }
    }

    @Test
    fun `does not show reminder when prediction is stale`() = runTest {
        val bestEstimate = Instant.now().minusSeconds(60 * 60)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)

        receiver.handle(context, bestEstimate.toEpochMilli(), 42L)

        verify(exactly = 0) { showPredictiveSleepReminder(context = any(), bestEstimateMs = any()) }
    }

    @Test
    fun `does not show reminder when fire time lands inside quiet hours`() = runTest {
        val bestEstimate = Instant.now().plusSeconds(60)
        every { sleepSettingsRepository.getPredictiveSleepEnabled() } returns flowOf(true)
        every { settingsRepository.getQuietHoursStartMinute() } returns flowOf(0)
        every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(24 * 60)

        receiver.handle(context, bestEstimate.toEpochMilli(), 42L)

        verify(exactly = 0) { showPredictiveSleepReminder(context = any(), bestEstimateMs = any()) }
    }
}
