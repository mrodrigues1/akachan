package com.babytracker.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class HomeScreenPredictionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun futurePrediction(minutesUntil: Int, sampleSize: Int = 5) = FeedPrediction(
        predictedAt = Instant.now().plusSeconds(minutesUntil * 60L),
        averageIntervalMinutes = 180,
        sampleSize = sampleSize,
        isOverdue = false,
        minutesUntil = minutesUntil,
    )

    private fun overduePrediction(minutesOverdue: Int, sampleSize: Int = 5) = FeedPrediction(
        predictedAt = Instant.now().minusSeconds(minutesOverdue * 60L),
        averageIntervalMinutes = 180,
        sampleSize = sampleSize,
        isOverdue = true,
        minutesUntil = -minutesOverdue,
    )

    @Test
    fun subtitleRendersFutureLikelyHungryAround() {
        composeRule.setContent {
            BabyTrackerTheme {
                FeedingPredictionSubtitle(prediction = futurePrediction(minutesUntil = 45))
            }
        }
        composeRule.onNodeWithText("Likely hungry around", substring = true).assertIsDisplayed()
    }

    @Test
    fun subtitleRendersSecondaryLineWhenWithin30Minutes() {
        composeRule.setContent {
            BabyTrackerTheme {
                FeedingPredictionSubtitle(prediction = futurePrediction(minutesUntil = 12))
            }
        }
        composeRule.onNodeWithText("in ~12m").assertIsDisplayed()
    }

    @Test
    fun subtitleRendersOverdueHungryNow() {
        composeRule.setContent {
            BabyTrackerTheme {
                FeedingPredictionSubtitle(prediction = overduePrediction(minutesOverdue = 7))
            }
        }
        composeRule.onNodeWithText("hungry now", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("~7m ago", substring = true).assertIsDisplayed()
    }

    @Test
    fun lowConfidenceSuffixAppearsWhenSampleSizeThree() {
        composeRule.setContent {
            BabyTrackerTheme {
                FeedingPredictionSubtitle(prediction = futurePrediction(minutesUntil = 12, sampleSize = 3))
            }
        }
        composeRule.onNodeWithText("low confidence", substring = true).assertIsDisplayed()
    }

    @Test
    fun subtitleDoesNotOverflowAtSmallWidth() {
        composeRule.setContent {
            BabyTrackerTheme {
                FeedingPredictionSubtitle(
                    prediction = futurePrediction(minutesUntil = 12, sampleSize = 3),
                    modifier = Modifier.width(180.dp),
                )
            }
        }
        composeRule.onNodeWithText("Likely hungry around", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("low confidence", substring = true).assertIsDisplayed()
    }
}
