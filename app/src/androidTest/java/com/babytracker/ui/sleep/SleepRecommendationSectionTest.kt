package com.babytracker.ui.sleep

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
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
        feedPrompt: String? = null,
        safetyPrompt: String = "Always place baby on their back to sleep.",
    ) = SleepPredictionState.Window(
        SleepWindow(
            windowStart = Instant.parse("2024-01-01T14:20:00Z"),
            windowEnd = Instant.parse("2024-01-01T14:50:00Z"),
            bestEstimate = Instant.parse("2024-01-01T14:35:00Z"),
            confidence = confidence,
            reasons = listOf("awake 2h05", "based on recent wake patterns"),
            feedPrompt = feedPrompt,
            safetyPrompt = safetyPrompt,
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
        composeRule.onNodeWithText("MEDIUM").assertIsDisplayed()
    }

    @Test
    fun windowState_showsConfidenceBadge_low() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(confidence = Confidence.LOW))
            }
        }
        composeRule.onNodeWithText("LOW").assertIsDisplayed()
    }

    @Test
    fun windowState_showsReasons() {
        composeRule.setContent {
            BabyTrackerTheme { SleepRecommendationSection(state = windowState()) }
        }
        composeRule.onNodeWithText("awake 2h05").assertIsDisplayed()
        composeRule.onNodeWithText("based on recent wake patterns").assertIsDisplayed()
    }

    @Test
    fun windowState_safetyPromptAlwaysShown() {
        composeRule.setContent {
            BabyTrackerTheme { SleepRecommendationSection(state = windowState()) }
        }
        composeRule.onNodeWithText("Always place baby on their back to sleep.").assertIsDisplayed()
    }

    @Test
    fun windowState_feedPromptShown_whenPresent() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(
                    state = windowState(feedPrompt = "a breastfeed may be due near this window"),
                )
            }
        }
        composeRule.onNodeWithText("a breastfeed may be due near this window").assertIsDisplayed()
    }

    @Test
    fun windowState_feedPromptNotShown_whenNull() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(feedPrompt = null))
            }
        }
        composeRule.onNodeWithText("a breastfeed may be due near this window").assertDoesNotExist()
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
        composeRule.onNodeWithText("Watch for sleep cues", substring = true).assertIsDisplayed()
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
        composeRule.onNodeWithText("Baby is currently sleeping", substring = true).assertIsDisplayed()
    }

    @Test
    fun afterActiveFeedState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.AfterActiveFeed)
            }
        }
        composeRule.onNodeWithText("A feed is in progress", substring = true).assertIsDisplayed()
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
        composeRule.onNodeWithText("SLEEP PREDICTION").assertDoesNotExist()
    }
}
