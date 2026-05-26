package com.babytracker.ui.settings

import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.domain.model.BackupData
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import com.babytracker.domain.usecase.breastfeeding.CountRecentValidIntervalsUseCase
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.NotificationPermissionChecker
import com.babytracker.sharing.domain.model.AppMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

private const val TEXT_PREDICTIVE_TOGGLE = "Predictive reminder"
private const val TEXT_FEEDING_REMINDERS = "FEEDING REMINDERS"
private const val TEXT_NOTIFICATIONS_BLOCKED = "Notifications blocked"
private const val TEXT_QUIET_HOURS_DISABLED = "Quiet hours disabled"
private const val TEXT_NEED_MORE_FEEDS = "Need 3+ recent feeds to predict."

@RunWith(AndroidJUnit4::class)
class SettingsScreenPredictionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun predictiveSwitch() =
        composeRule.onNodeWithTag("predictive_switch").performScrollTo()

    private fun buildViewModel(
        settingsRepository: SettingsRepository = PredictionFakeSettingsRepository(),
        countRecentValidIntervals: CountRecentValidIntervalsUseCase = mockk<CountRecentValidIntervalsUseCase>().also {
            every { it.invoke() } returns flowOf(0)
        },
        permissionChecker: NotificationPermissionChecker = NotificationPermissionChecker { true },
    ): SettingsViewModel {
        val babyRepository = PredictionFakeBabyRepository()
        return SettingsViewModel(
            getBabyProfile = GetBabyProfileUseCase(babyRepository),
            settingsRepository = settingsRepository,
            saveBabyProfile = SaveBabyProfileUseCase(babyRepository),
            countRecentValidIntervals = countRecentValidIntervals,
            notificationPermissionChecker = permissionChecker,
            napReminderScheduler = mockk(relaxed = true),
        )
    }

    @Test
    fun toggleRevealsConfigRows() {
        val repo = PredictionFakeSettingsRepository(
            appMode = AppMode.NONE,
            predictiveEnabledInitial = false,
        )
        val vm = buildViewModel(settingsRepository = repo)
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()

        // Toggle OFF: "Notify ahead by" row exists (just dimmed by alpha). Clicking "30m"
        // should be a no-op because enabled=false on the segmented button.
        composeRule.onNodeWithText("Notify ahead by").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("30m").performScrollTo().performClick()
        composeRule.waitForIdle()
        // No state change when disabled.
        assertEquals(
            "Expected lead minutes unchanged while toggle OFF",
            15,
            repo.predictiveLeadMinutes.value,
        )

        // Toggle ON.
        predictiveSwitch().performClick()
        composeRule.waitForIdle()

        // Now click "30m" - it should update.
        composeRule.onNodeWithText("30m").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            "Expected lead minutes to be 30 after toggle ON",
            30,
            repo.predictiveLeadMinutes.value,
        )
    }

    @Test
    fun quietHoursStartEqualsEndShowsDisabledHelper() {
        val repo = PredictionFakeSettingsRepository(
            appMode = AppMode.NONE,
            predictiveEnabledInitial = true,
            quietHoursStartInitial = 480,
            quietHoursEndInitial = 480,
        )
        val vm = buildViewModel(settingsRepository = repo)
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TEXT_QUIET_HOURS_DISABLED).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun segmentedButtonSelectionPersists() {
        val repo = PredictionFakeSettingsRepository(
            appMode = AppMode.NONE,
            predictiveEnabledInitial = true,
        )
        val vm = buildViewModel(settingsRepository = repo)
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("30m").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(
            "Expected predictiveLeadMinutes == 30",
            30,
            repo.predictiveLeadMinutes.value,
        )
    }

    @Test
    fun permissionGrantedHidesWarning() {
        val repo = PredictionFakeSettingsRepository(
            appMode = AppMode.NONE,
            predictiveEnabledInitial = false,
        )
        val vm = buildViewModel(
            settingsRepository = repo,
            permissionChecker = NotificationPermissionChecker { true },
        )
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        predictiveSwitch().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TEXT_NOTIFICATIONS_BLOCKED).assertDoesNotExist()
    }

    @Test
    fun permissionDeniedShowsWarningAndKeepsToggleOn() {
        val repo = PredictionFakeSettingsRepository(
            appMode = AppMode.NONE,
            predictiveEnabledInitial = false,
        )
        val vm = buildViewModel(
            settingsRepository = repo,
            permissionChecker = NotificationPermissionChecker { false },
        )
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()

        predictiveSwitch().performClick()
        composeRule.waitForIdle()

        // Toggle is still ON in repo state.
        assertTrue("Expected predictiveEnabled true after toggle", repo.predictiveEnabled.value)
        // Warning is visible.
        composeRule.onNodeWithText(TEXT_NOTIFICATIONS_BLOCKED).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun openSettingsButtonLaunchesAppNotificationSettingsIntent() {
        Intents.init()
        try {
            val repo = PredictionFakeSettingsRepository(
                appMode = AppMode.NONE,
                predictiveEnabledInitial = true,
            )
            val vm = buildViewModel(
                settingsRepository = repo,
                permissionChecker = NotificationPermissionChecker { false },
            )
            composeRule.setContent {
                SettingsScreen(onNavigateBack = {}, viewModel = vm)
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithText(TEXT_NOTIFICATIONS_BLOCKED).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Open settings").performScrollTo().performClick()
            composeRule.waitForIdle()

            Intents.intended(hasAction(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS))
        } finally {
            Intents.release()
        }
    }

    @Test
    fun permissionRevokedOnResumeShowsWarning() {
        val repo = PredictionFakeSettingsRepository(
            appMode = AppMode.NONE,
            predictiveEnabledInitial = true,
        )
        val vm = buildViewModel(
            settingsRepository = repo,
            permissionChecker = NotificationPermissionChecker { true },
        )
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        // Initially with permission granted + predictive enabled, no warning.
        composeRule.onNodeWithText(TEXT_NOTIFICATIONS_BLOCKED).assertDoesNotExist()

        // Simulate ON_RESUME re-check finding permission revoked.
        composeRule.runOnUiThread {
            vm.refreshNotificationsPermission(false)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(TEXT_NOTIFICATIONS_BLOCKED).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun toggleOffWhilePermissionDeniedHidesWarning() {
        val repo = PredictionFakeSettingsRepository(
            appMode = AppMode.NONE,
            predictiveEnabledInitial = false,
        )
        val vm = buildViewModel(
            settingsRepository = repo,
            permissionChecker = NotificationPermissionChecker { false },
        )
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()

        // Toggle ON: warning appears.
        predictiveSwitch().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TEXT_NOTIFICATIONS_BLOCKED).performScrollTo().assertIsDisplayed()

        // Toggle OFF: warning hidden.
        predictiveSwitch().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TEXT_NOTIFICATIONS_BLOCKED).assertDoesNotExist()
    }

    @Test
    fun partnerModeHidesFeedingRemindersSection() {
        val repo = PredictionFakeSettingsRepository(appMode = AppMode.PARTNER)
        val vm = buildViewModel(settingsRepository = repo)
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TEXT_FEEDING_REMINDERS).assertDoesNotExist()
        composeRule.onNodeWithText(TEXT_PREDICTIVE_TOGGLE).assertDoesNotExist()
    }

    @Test
    fun emptyDataHintAbsentWhenValidIntervalCountAtLeastThree() {
        val repo = PredictionFakeSettingsRepository(
            appMode = AppMode.NONE,
            predictiveEnabledInitial = true,
        )
        val countUseCase = mockk<CountRecentValidIntervalsUseCase>()
        every { countUseCase() } returns flowOf(5)
        val vm = buildViewModel(
            settingsRepository = repo,
            countRecentValidIntervals = countUseCase,
        )
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TEXT_NEED_MORE_FEEDS).assertDoesNotExist()
    }

    private class PredictionFakeBabyRepository : BabyRepository {
        override fun getBabyProfile(): Flow<Baby?> = flowOf(
            Baby(name = "Leo", birthDate = LocalDate.of(2025, 1, 1))
        )

        override suspend fun saveBabyProfile(baby: Baby) = Unit

        override fun isOnboardingComplete(): Flow<Boolean> = flowOf(true)
    }

    private class PredictionFakeSettingsRepository(
        private val appMode: AppMode = AppMode.NONE,
        predictiveEnabledInitial: Boolean = false,
        predictiveLeadInitial: Int = 15,
        quietHoursStartInitial: Int = 0,
        quietHoursEndInitial: Int = 480,
    ) : SettingsRepository {

        val predictiveEnabled = MutableStateFlow(predictiveEnabledInitial)
        val predictiveLeadMinutes = MutableStateFlow(predictiveLeadInitial)
        val quietHoursStart = MutableStateFlow(quietHoursStartInitial)
        val quietHoursEnd = MutableStateFlow(quietHoursEndInitial)

        override fun getThemeConfig(): Flow<ThemeConfig> = flowOf(ThemeConfig.SYSTEM)

        override suspend fun setThemeConfig(themeConfig: ThemeConfig) = Unit

        override fun isOnboardingComplete(): Flow<Boolean> = flowOf(true)

        override suspend fun setOnboardingComplete(complete: Boolean) = Unit

        override fun getMaxPerBreastMinutes(): Flow<Int> = flowOf(0)

        override suspend fun setMaxPerBreastMinutes(minutes: Int) = Unit

        override fun getMaxTotalFeedMinutes(): Flow<Int> = flowOf(0)

        override suspend fun setMaxTotalFeedMinutes(minutes: Int) = Unit

        override fun getWakeTime(): Flow<LocalTime?> = flowOf(null)

        override suspend fun setWakeTime(time: LocalTime) = Unit

        override fun getAutoUpdateEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setAutoUpdateEnabled(enabled: Boolean) = Unit

        override fun getRichNotificationsEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setRichNotificationsEnabled(enabled: Boolean) = Unit

        override fun getAppMode(): Flow<AppMode> = flowOf(appMode)

        override suspend fun setAppMode(mode: AppMode) = Unit

        override fun getShareCode(): Flow<String?> = flowOf(null)

        override suspend fun setShareCode(code: String) = Unit

        override suspend fun clearShareCode() = Unit

        override fun getPredictiveEnabled(): Flow<Boolean> = predictiveEnabled

        override suspend fun setPredictiveEnabled(enabled: Boolean) {
            predictiveEnabled.value = enabled
        }

        override fun getPredictiveLeadMinutes(): Flow<Int> = predictiveLeadMinutes

        override suspend fun setPredictiveLeadMinutes(minutes: Int) {
            predictiveLeadMinutes.value = minutes
        }

        override fun getQuietHoursStartMinute(): Flow<Int> = quietHoursStart

        override suspend fun setQuietHoursStartMinute(minuteOfDay: Int) {
            quietHoursStart.value = minuteOfDay
        }

        override fun getQuietHoursEndMinute(): Flow<Int> = quietHoursEnd

        override suspend fun setQuietHoursEndMinute(minuteOfDay: Int) {
            quietHoursEnd.value = minuteOfDay
        }

        override fun getNapReminderEnabled(): Flow<Boolean> = flowOf(false)

        override suspend fun setNapReminderEnabled(enabled: Boolean) = Unit

        override fun getNapReminderDelayMinutes(): Flow<Int> = flowOf(60)

        override suspend fun setNapReminderDelayMinutes(minutes: Int) = Unit

        override fun isImportInProgress(): Flow<Boolean> = flowOf(false)

        override suspend fun markImportInProgress(startedAt: Long) = Unit

        override suspend fun restoreFromBackup(data: BackupData) = Unit
    }
}
