package com.babytracker.ui.sleep

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepReason
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.SleepWindow
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SleepRecommendationSectionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun windowState(
        confidence: Confidence = Confidence.MEDIUM,
        feedDue: Boolean = false,
        reasons: List<SleepReason> = listOf(SleepReason.Disruption, SleepReason.CircadianSlot),
    ) = SleepPredictionState.Window(
        SleepWindow(
            windowStart = Instant.parse("2024-01-01T14:20:00Z"),
            windowEnd = Instant.parse("2024-01-01T14:50:00Z"),
            bestEstimate = Instant.parse("2024-01-01T14:35:00Z"),
            sleepType = SleepType.NAP,
            confidence = confidence,
            reasons = reasons,
            feedDue = feedDue,
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
    fun windowState_showsConfidenceBadge_medium() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(confidence = Confidence.MEDIUM))
            }
        }
        composeRule.onNodeWithText("Confidence: Medium").assertIsDisplayed()
    }

    @Test
    fun windowState_showsConfidenceBadge_low() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(confidence = Confidence.LOW))
            }
        }
        composeRule.onNodeWithText("Confidence: Low").assertIsDisplayed()
    }

    @Test
    fun windowState_showsReasons() {
        composeRule.setContent {
            BabyTrackerTheme { SleepRecommendationSection(state = windowState()) }
        }
        composeRule.onNodeWithText("reduced prediction confidence", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("circadian slot", substring = true).assertIsDisplayed()
    }

    @Test
    fun windowState_safetyPromptAlwaysShown() {
        composeRule.setContent {
            BabyTrackerTheme { SleepRecommendationSection(state = windowState()) }
        }
        composeRule.onNodeWithContentDescription("Safe sleep tip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Always follow your baby's sleep cues", substring = true).assertIsDisplayed()
    }

    @Test
    fun windowState_feedPromptShown_whenPresent() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(
                    state = windowState(feedDue = true),
                )
            }
        }
        composeRule.onNodeWithText("a breastfeed may be due near this window", substring = true).assertIsDisplayed()
    }

    @Test
    fun windowState_feedPromptNotShown_whenNull() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(feedDue = false))
            }
        }
        composeRule.onAllNodesWithText("a breastfeed may be due near this window", substring = true).assertCountEquals(0)
    }

    @Test
    fun needMoreDataState_showsHint() {
        composeRule.setContent {
            BabyTrackerTheme { SleepRecommendationSection(state = needMoreDataState()) }
        }
        composeRule.onNodeWithText("Log a few more naps with both sleep and wake times.", substring = true).assertIsDisplayed()
    }

    @Test
    fun overdueState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.Overdue)
            }
        }
        composeRule.onNodeWithText("Watch for cues", substring = true).assertIsDisplayed()
    }

    @Test
    fun cueLedState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.CueLed)
            }
        }
        composeRule.onNodeWithText("Watching baby's cues", substring = true).assertIsDisplayed()
    }

    @Test
    fun currentlySleepingState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.CurrentlySleeping)
            }
        }
        composeRule.onNodeWithText("Baby is sleeping", substring = true).assertIsDisplayed()
    }

    @Test
    fun afterActiveFeedState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.AfterActiveFeed)
            }
        }
        composeRule.onNodeWithText("Feeding now", substring = true).assertIsDisplayed()
    }

    @Test
    fun unavailableState_rendersNothing() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(
                    state = SleepPredictionState.Unavailable("no data"),
                )
            }
        }
        composeRule.onAllNodesWithText("SLEEP PREDICTION").assertCountEquals(0)
    }
}
