package com.babytracker.ui.partner

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.babytracker.sharing.domain.model.SleepPredictionSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class PartnerSleepPredictionCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-05-16T11:00:00Z")
    private val generatedAt = now.minusSeconds(8 * 60).toEpochMilli()

    private fun setCard(
        prediction: SleepPredictionSnapshot?,
        activeSleepType: String? = null,
        hasActiveSleep: Boolean = false,
        hasActiveFeeding: Boolean = false,
    ) {
        composeRule.setContent {
            MaterialTheme {
                PartnerSleepPredictionCard(
                    prediction = prediction,
                    now = now,
                    activeSleepType = activeSleepType,
                    hasActiveSleep = hasActiveSleep,
                    hasActiveFeeding = hasActiveFeeding,
                )
            }
        }
    }

    @Test
    fun `window state renders header reasons and feed prompt`() {
        setCard(
            SleepPredictionSnapshot(
                stateLabel = "WINDOW",
                windowStart = now.plusSeconds(20 * 60).toEpochMilli(),
                windowEnd = now.plusSeconds(50 * 60).toEpochMilli(),
                bestEstimate = now.plusSeconds(35 * 60).toEpochMilli(),
                confidence = "MEDIUM",
                reasons = listOf("Awake 2h 05m", "Based on recent wake patterns"),
                feedPrompt = "A breastfeed may be due near this window.",
                generatedAt = generatedAt,
            ),
        )

        composeRule.onNodeWithText("NEXT SLEEP WINDOW", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Awake 2h 05m", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "A breastfeed may be due near this window.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Estimated", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `window high confidence is announced`() {
        setCard(
            SleepPredictionSnapshot(
                stateLabel = "WINDOW",
                windowStart = now.plusSeconds(20 * 60).toEpochMilli(),
                windowEnd = now.plusSeconds(50 * 60).toEpochMilli(),
                bestEstimate = now.plusSeconds(35 * 60).toEpochMilli(),
                confidence = "HIGH",
                generatedAt = generatedAt,
            ),
        )

        composeRule.onNodeWithContentDescription("high confidence", substring = true).assertIsDisplayed()
    }

    @Test
    fun `stale window renders as overdue`() {
        setCard(
            SleepPredictionSnapshot(
                stateLabel = "WINDOW",
                windowStart = now.minusSeconds(120 * 60).toEpochMilli(),
                windowEnd = now.minusSeconds(60 * 60).toEpochMilli(),
                bestEstimate = now.minusSeconds(90 * 60).toEpochMilli(),
                confidence = "HIGH",
                generatedAt = now.minusSeconds(3 * 60 * 60).toEpochMilli(),
            ),
        )

        composeRule.onNodeWithText("SLEEP WINDOW PASSED", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Watch for sleepy cues", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `currently sleeping without type renders type neutral copy`() {
        setCard(
            SleepPredictionSnapshot(stateLabel = "CURRENTLY_SLEEPING", generatedAt = generatedAt),
            hasActiveSleep = true,
        )

        composeRule.onNodeWithText("SLEEPING", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Sleep in progress", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `currently sleeping renders nap copy for active nap`() {
        setCard(
            SleepPredictionSnapshot(stateLabel = "CURRENTLY_SLEEPING", generatedAt = generatedAt),
            activeSleepType = "NAP",
            hasActiveSleep = true,
        )

        composeRule.onNodeWithText("Nap in progress", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `currently sleeping renders night sleep copy for active night sleep`() {
        setCard(
            SleepPredictionSnapshot(stateLabel = "CURRENTLY_SLEEPING", generatedAt = generatedAt),
            activeSleepType = "NIGHT_SLEEP",
            hasActiveSleep = true,
        )

        composeRule.onNodeWithText("Night Sleep in progress", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `stale currently sleeping is hidden when no active sleep`() {
        setCard(
            SleepPredictionSnapshot(stateLabel = "CURRENTLY_SLEEPING", generatedAt = generatedAt),
            hasActiveSleep = false,
        )

        composeRule.onNodeWithText("SLEEPING", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Nap in progress", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Estimated", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `after active feed renders copy while feed is active`() {
        setCard(
            SleepPredictionSnapshot(stateLabel = "AFTER_ACTIVE_FEED", generatedAt = generatedAt),
            hasActiveFeeding = true,
        )

        composeRule.onNodeWithText("FEEDING NOW", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Sleep window appears after this feed ends",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `stale after active feed is hidden when no active feeding`() {
        setCard(
            SleepPredictionSnapshot(stateLabel = "AFTER_ACTIVE_FEED", generatedAt = generatedAt),
            hasActiveFeeding = false,
        )

        composeRule.onNodeWithText("FEEDING NOW", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Estimated", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `need more data renders copy`() {
        setCard(SleepPredictionSnapshot(stateLabel = "NEED_MORE_DATA", generatedAt = generatedAt))

        composeRule.onNodeWithText(
            "Not enough logged data yet for a prediction",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `cue led renders copy`() {
        setCard(SleepPredictionSnapshot(stateLabel = "CUE_LED", generatedAt = generatedAt))

        composeRule.onNodeWithText(
            "Too early for predictions. Watch for cues",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `null prediction renders nothing`() {
        setCard(null)

        composeRule.onNodeWithText("Estimated", substring = true, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("SLEEP PREDICTION", useUnmergedTree = true).assertDoesNotExist()
    }
}
