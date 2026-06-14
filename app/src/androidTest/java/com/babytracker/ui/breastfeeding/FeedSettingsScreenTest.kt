package com.babytracker.ui.breastfeeding

import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.repository.FeedSettingsRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.CountRecentValidIntervalsUseCase
import com.babytracker.manager.NotificationPermissionChecker
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

private const val TEXT_NOTIFICATIONS_BLOCKED = "Notifications blocked"
private const val TEXT_QUIET_HOURS_DISABLED = "Quiet hours disabled"
private const val TEXT_NEED_MORE_FEEDS = "Need 3+ recent feeds to predict."

@RunWith(AndroidJUnit4::class)
class FeedSettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun predictiveSwitch() =
        composeRule.onNodeWithTag("predictive_switch").performScrollTo()

    private fun buildViewModel(
        feedSettings: FeedSettingsRepository,
        quietStart: MutableStateFlow<Int> = MutableStateFlow(0),
        quietEnd: MutableStateFlow<Int> = MutableStateFlow(480),
        validIntervalCount: Int = 0,
        permissionChecker: NotificationPermissionChecker = NotificationPermissionChecker { true },
    ): FeedSettingsViewModel {
        val settings = mockk<SettingsRepository>(relaxed = true)
        every { settings.getQuietHoursStartMinute() } returns quietStart
        every { settings.getQuietHoursEndMinute() } returns quietEnd
        val count = mockk<CountRecentValidIntervalsUseCase>()
        every { count() } returns flowOf(validIntervalCount)
        return FeedSettingsViewModel(
            feedSettingsRepository = feedSettings,
            settingsRepository = settings,
            countRecentValidIntervals = count,
            notificationPermissionChecker = permissionChecker,
        )
    }

    @Test
    fun maxPerBreastRowShowsValueAndOpensEditSheet() {
        val feed = FakeFeedSettingsRepository(maxPerBreastInitial = 12)
        val vm = buildViewModel(feed)
        composeRule.setContent { FeedSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Max per breast").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("12 min").assertIsDisplayed()

        composeRule.onNodeWithText("Max per breast").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit Max per breast").assertIsDisplayed()
    }

    @Test
    fun editingMaxTotalFeedPersistsValue() {
        val feed = FakeFeedSettingsRepository()
        val vm = buildViewModel(feed)
        composeRule.setContent { FeedSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Max total feed").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Minutes (0 = disabled)").performTextInput("40")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(40, feed.maxTotalFeed.value)
        }
    }

    @Test
    fun leadTimeRowIgnoredWhileToggleOffThenPersistsAfterToggleOn() {
        val feed = FakeFeedSettingsRepository(predictiveEnabledInitial = false)
        val vm = buildViewModel(feed, validIntervalCount = 3)
        composeRule.setContent { FeedSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        composeRule.waitForIdle()

        // Toggle OFF: segmented button is disabled, so clicking is a no-op.
        composeRule.onAllNodesWithText("30m")[0].performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(15, feed.predictiveLeadMinutes.value)

        predictiveSwitch().performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("30m")[0].performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(30, feed.predictiveLeadMinutes.value)
    }

    @Test
    fun quietHoursStartEqualsEndShowsDisabledHelper() {
        val feed = FakeFeedSettingsRepository(predictiveEnabledInitial = true)
        val vm = buildViewModel(
            feed,
            quietStart = MutableStateFlow(480),
            quietEnd = MutableStateFlow(480),
            validIntervalCount = 3,
        )
        composeRule.setContent { FeedSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(TEXT_QUIET_HOURS_DISABLED)[0].performScrollTo().assertIsDisplayed()
    }

    @Test
    fun permissionGrantedHidesWarning() {
        val feed = FakeFeedSettingsRepository(predictiveEnabledInitial = false)
        val vm = buildViewModel(feed, permissionChecker = NotificationPermissionChecker { true })
        composeRule.setContent { FeedSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        composeRule.waitForIdle()
        predictiveSwitch().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TEXT_NOTIFICATIONS_BLOCKED).assertDoesNotExist()
    }

    @Test
    fun permissionDeniedShowsWarningAndKeepsToggleOn() {
        val feed = FakeFeedSettingsRepository(predictiveEnabledInitial = false)
        val vm = buildViewModel(feed, permissionChecker = NotificationPermissionChecker { false })
        composeRule.setContent { FeedSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        composeRule.waitForIdle()

        predictiveSwitch().performClick()
        composeRule.waitForIdle()

        assertTrue(feed.predictiveEnabled.value)
        composeRule.onNodeWithText(TEXT_NOTIFICATIONS_BLOCKED).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun openSettingsButtonLaunchesAppNotificationSettingsIntent() {
        Intents.init()
        try {
            val feed = FakeFeedSettingsRepository(predictiveEnabledInitial = true)
            val vm = buildViewModel(feed, permissionChecker = NotificationPermissionChecker { false })
            composeRule.setContent { FeedSettingsScreen(onNavigateBack = {}, viewModel = vm) }
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
        val feed = FakeFeedSettingsRepository(predictiveEnabledInitial = true)
        val vm = buildViewModel(feed, permissionChecker = NotificationPermissionChecker { true })
        composeRule.setContent { FeedSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TEXT_NOTIFICATIONS_BLOCKED).assertDoesNotExist()

        composeRule.runOnUiThread { vm.refreshNotificationsPermission(false) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(TEXT_NOTIFICATIONS_BLOCKED).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyDataHintAbsentWhenValidIntervalCountAtLeastThree() {
        val feed = FakeFeedSettingsRepository(predictiveEnabledInitial = true)
        val vm = buildViewModel(feed, validIntervalCount = 5)
        composeRule.setContent { FeedSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TEXT_NEED_MORE_FEEDS).assertDoesNotExist()
    }

    private class FakeFeedSettingsRepository(
        maxPerBreastInitial: Int = 0,
        maxTotalFeedInitial: Int = 0,
        predictiveEnabledInitial: Boolean = false,
        predictiveLeadInitial: Int = 15,
    ) : FeedSettingsRepository {

        val maxPerBreast = MutableStateFlow(maxPerBreastInitial)
        val maxTotalFeed = MutableStateFlow(maxTotalFeedInitial)
        val predictiveEnabled = MutableStateFlow(predictiveEnabledInitial)
        val predictiveLeadMinutes = MutableStateFlow(predictiveLeadInitial)

        override fun getMaxPerBreastMinutes(): Flow<Int> = maxPerBreast

        override suspend fun setMaxPerBreastMinutes(minutes: Int) {
            maxPerBreast.value = minutes
        }

        override fun getMaxTotalFeedMinutes(): Flow<Int> = maxTotalFeed

        override suspend fun setMaxTotalFeedMinutes(minutes: Int) {
            maxTotalFeed.value = minutes
        }

        override fun getPredictiveEnabled(): Flow<Boolean> = predictiveEnabled

        override suspend fun setPredictiveEnabled(enabled: Boolean) {
            predictiveEnabled.value = enabled
        }

        override fun getPredictiveLeadMinutes(): Flow<Int> = predictiveLeadMinutes

        override suspend fun setPredictiveLeadMinutes(minutes: Int) {
            predictiveLeadMinutes.value = minutes
        }
    }
}
