package com.babytracker.ui.pumping

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PumpingHistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val baseNow = Instant.ofEpochSecond(1_700_000_000L)

    private fun session(id: Long, volumeMl: Int? = 120, breast: PumpingBreast = PumpingBreast.LEFT) =
        PumpingSession(
            id = id,
            startTime = baseNow.minusSeconds(id * 3600),
            endTime = baseNow.minusSeconds(id * 3600 - 600),
            breast = breast,
            volumeMl = volumeMl,
        )

    @Test
    fun emptyStateShowsNoPumpingSessionsText() {
        composeRule.setContent {
            BabyTrackerTheme {
                PumpingHistoryContent(
                    state = PumpingHistoryUiState(),
                    onEditClicked = {},
                )
            }
        }

        composeRule.onNodeWithText("No pumping sessions yet").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsSubtitle() {
        composeRule.setContent {
            BabyTrackerTheme {
                PumpingHistoryContent(
                    state = PumpingHistoryUiState(),
                    onEditClicked = {},
                )
            }
        }

        composeRule.onNodeWithText("Sessions you track will appear here").assertIsDisplayed()
    }

    @Test
    fun sessionListRendersOneCardPerSession() {
        val sessions = listOf(session(1), session(2, volumeMl = 200))
        composeRule.setContent {
            BabyTrackerTheme {
                PumpingHistoryContent(
                    state = PumpingHistoryUiState(sessions = sessions),
                    onEditClicked = {},
                )
            }
        }

        composeRule.onNodeWithText("Left · 120 ml").assertIsDisplayed()
        composeRule.onNodeWithText("Left · 200 ml").assertIsDisplayed()
    }

    @Test
    fun sessionCardShowsDashWhenVolumeIsNull() {
        val sessions = listOf(session(1, volumeMl = null))
        composeRule.setContent {
            BabyTrackerTheme {
                PumpingHistoryContent(
                    state = PumpingHistoryUiState(sessions = sessions),
                    onEditClicked = {},
                )
            }
        }

        composeRule.onNodeWithText("Left · —").assertIsDisplayed()
    }

    @Test
    fun tappingCardInvokesOnEditClicked() {
        var clicked: PumpingSession? = null
        val sessions = listOf(session(1))
        composeRule.setContent {
            BabyTrackerTheme {
                PumpingHistoryContent(
                    state = PumpingHistoryUiState(sessions = sessions),
                    onEditClicked = { clicked = it },
                )
            }
        }

        composeRule.onNodeWithText("Left · 120 ml").performClick()

        composeRule.runOnIdle {
            assertNotNull(clicked)
            assertEquals(1L, clicked!!.id)
        }
    }

    @Test
    fun sessionCardShowsBothBreastLabel() {
        val sessions = listOf(session(1, breast = PumpingBreast.BOTH))
        composeRule.setContent {
            BabyTrackerTheme {
                PumpingHistoryContent(
                    state = PumpingHistoryUiState(sessions = sessions),
                    onEditClicked = {},
                )
            }
        }

        composeRule.onNodeWithText("Both · 120 ml").assertIsDisplayed()
    }
}
