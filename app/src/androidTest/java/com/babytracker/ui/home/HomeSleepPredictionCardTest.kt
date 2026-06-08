package com.babytracker.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.ui.sleep.SleepPredictionCard
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class HomeSleepPredictionCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun windowState(
        confidence: Confidence = Confidence.MEDIUM,
        feedPrompt: String? = null,
    ) = SleepPredictionState.Window(
        SleepWindow(
            windowStart = Instant.parse("2024-01-01T14:20:00Z"),
            windowEnd = Instant.parse("2024-01-01T14:50:00Z"),
            bestEstimate = Instant.parse("2024-01-01T14:35:00Z"),
            confidence = confidence,
            reasons = listOf("awake 2h05"),
            feedPrompt = feedPrompt,
            safetyPrompt = "Always place baby on their back to sleep.",
        )
    )

    private fun needMoreDataState() = SleepPredictionState.NeedMoreData(
        EvidenceProgress(
            completedIntervals = 3,
            requiredIntervals = 10,
            localDays = 1,
            requiredLocalDays = 3,
            hint = "Log a few more naps with both sleep and wake times.",
        )
    )

    @Test
    fun windowState_showsNextSleepText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = windowState()) }
        }
        composeRule.onNodeWithContentDescription("Next sleep", substring = true).assertIsDisplayed()
    }

    @Test
    fun windowState_lowConfidence_showsLowConfidenceIndicator() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = windowState(confidence = Confidence.LOW)) }
        }
        composeRule.onNodeWithContentDescription("Low confidence prediction").assertIsDisplayed()
    }

    @Test
    fun windowState_mediumConfidence_showsMediumConfidenceIndicator() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = windowState(confidence = Confidence.MEDIUM)) }
        }
        composeRule.onNodeWithContentDescription("Medium confidence prediction").assertIsDisplayed()
    }

    @Test
    fun windowState_safeSleepCollapsed_byDefault() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = windowState()) }
        }
        composeRule.onNodeWithText("Always place baby on their back to sleep.").assertDoesNotExist()
    }

    @Test
    fun windowState_safeSleepExpandsOnToggle() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = windowState()) }
        }
        composeRule.onNodeWithText("Safe sleep").performClick()
        composeRule.onNodeWithText("Always place baby on their back to sleep.").assertIsDisplayed()
    }

    @Test
    fun needMoreDataState_showsHint() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = needMoreDataState()) }
        }
        composeRule.onNodeWithText("Log a few more naps with both sleep and wake times.", substring = true).assertIsDisplayed()
    }

    @Test
    fun overdueState_showsCueText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = SleepPredictionState.Overdue) }
        }
        composeRule.onNodeWithText("Watch for cues", substring = true).assertIsDisplayed()
    }

    @Test
    fun cueLedState_showsCueLedText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = SleepPredictionState.CueLed) }
        }
        composeRule.onNodeWithText("Watching baby's cues", substring = true).assertIsDisplayed()
    }

    @Test
    fun currentlySleepingState_showsSleepingText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = SleepPredictionState.CurrentlySleeping) }
        }
        composeRule.onNodeWithText("Baby is sleeping").assertIsDisplayed()
    }

    @Test
    fun afterActiveFeedState_showsFeedingText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = SleepPredictionState.AfterActiveFeed) }
        }
        composeRule.onNodeWithText("Feeding now", substring = true).assertIsDisplayed()
    }

    @Test
    fun unavailableState_rendersNothing() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = SleepPredictionState.Unavailable("no data")) }
        }
        composeRule.onNodeWithText("SLEEP PREDICTION").assertDoesNotExist()
    }
}
