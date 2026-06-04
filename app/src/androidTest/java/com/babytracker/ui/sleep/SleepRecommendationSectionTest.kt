package com.babytracker.ui.sleep

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.ScheduleEntry
import com.babytracker.domain.model.ScheduleMode
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepWindow
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

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

    private fun scheduleWithNapAt(napTime: LocalTime) = SleepSchedule(
        ageInWeeks = 20,
        mode = ScheduleMode.CLOCK_ALIGNED,
        wakeWindows = emptyList(),
        napTimes = listOf(ScheduleEntry(startTime = napTime, duration = Duration.ofMinutes(90), label = "Nap")),
        bedtime = LocalTime.of(19, 0),
        bedtimeWindow = LocalTime.of(18, 30)..LocalTime.of(19, 30),
        totalSleepRecommendation = Duration.ofHours(14)..Duration.ofHours(16),
        totalSleepLogged = null,
        regressionWarning = null,
        napTransitionSuggestion = null,
        lastFeedTime = null,
        isPersonalized = false,
    )

    @Test
    fun windowState_showsConfidenceBadge_medium() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(confidence = Confidence.MEDIUM), schedule = null)
            }
        }
        composeRule.onNodeWithText("MEDIUM").assertIsDisplayed()
    }

    @Test
    fun windowState_showsConfidenceBadge_low() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(confidence = Confidence.LOW), schedule = null)
            }
        }
        composeRule.onNodeWithText("LOW").assertIsDisplayed()
    }

    @Test
    fun windowState_showsReasons() {
        composeRule.setContent {
            BabyTrackerTheme { SleepRecommendationSection(state = windowState(), schedule = null) }
        }
        composeRule.onNodeWithText("awake 2h05").assertIsDisplayed()
        composeRule.onNodeWithText("based on recent wake patterns").assertIsDisplayed()
    }

    @Test
    fun windowState_safetyPromptAlwaysShown() {
        composeRule.setContent {
            BabyTrackerTheme { SleepRecommendationSection(state = windowState(), schedule = null) }
        }
        composeRule.onNodeWithText("Always place baby on their back to sleep.").assertIsDisplayed()
    }

    @Test
    fun windowState_feedPromptShown_whenPresent() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(
                    state = windowState(feedPrompt = "a breastfeed may be due near this window"),
                    schedule = null,
                )
            }
        }
        composeRule.onNodeWithText("a breastfeed may be due near this window").assertIsDisplayed()
    }

    @Test
    fun windowState_feedPromptNotShown_whenNull() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(feedPrompt = null), schedule = null)
            }
        }
        composeRule.onNodeWithText("a breastfeed may be due near this window").assertDoesNotExist()
    }

    @Test
    fun needMoreDataState_showsHint() {
        composeRule.setContent {
            BabyTrackerTheme { SleepRecommendationSection(state = needMoreDataState(), schedule = null) }
        }
        composeRule.onNodeWithText("Log a few more naps with both sleep and wake times.", substring = true).assertIsDisplayed()
    }

    @Test
    fun overdueState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.Overdue, schedule = null)
            }
        }
        composeRule.onNodeWithText("Watch for sleep cues", substring = true).assertIsDisplayed()
    }

    @Test
    fun cueLedState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.CueLed, schedule = null)
            }
        }
        composeRule.onNodeWithText("Watching baby's cues", substring = true).assertIsDisplayed()
    }

    @Test
    fun currentlySleepingState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.CurrentlySleeping, schedule = null)
            }
        }
        composeRule.onNodeWithText("Baby is currently sleeping", substring = true).assertIsDisplayed()
    }

    @Test
    fun afterActiveFeedState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.AfterActiveFeed, schedule = null)
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
                    schedule = null,
                )
            }
        }
        composeRule.onNodeWithText("SLEEP RECOMMENDATION").assertDoesNotExist()
    }

    @Test
    fun windowState_planVsPredictorRow_appearsWhenFutureNapExists() {
        val now = LocalTime.of(14, 0)
        val schedule = scheduleWithNapAt(LocalTime.of(15, 0))
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(), schedule = schedule, now = now)
            }
        }
        composeRule.onNodeWithText("DAY PLAN").assertIsDisplayed()
    }

    @Test
    fun windowState_planVsPredictorRow_hiddenWhenAllNapsPast() {
        val now = LocalTime.of(16, 0)
        val schedule = scheduleWithNapAt(LocalTime.of(15, 0))
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(), schedule = schedule, now = now)
            }
        }
        composeRule.onNodeWithText("DAY PLAN").assertDoesNotExist()
    }
}
