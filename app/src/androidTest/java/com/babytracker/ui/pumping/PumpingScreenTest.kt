package com.babytracker.ui.pumping

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PumpingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idleStateShowsStartButton() {
        composeRule.setContent {
            BabyTrackerTheme {
                IdleTimerContent(
                    selectedBreast = PumpingBreast.BOTH,
                    onBreastSelected = {},
                    onStart = {},
                )
            }
        }

        composeRule.onNodeWithText("Start Pumping").assertIsDisplayed()
    }

    @Test
    fun startButtonIsEnabled() {
        composeRule.setContent {
            BabyTrackerTheme {
                IdleTimerContent(
                    selectedBreast = PumpingBreast.LEFT,
                    onBreastSelected = {},
                    onStart = {},
                )
            }
        }

        composeRule.onNodeWithText("Start Pumping").assertIsEnabled()
    }

    @Test
    fun tapStartInvokesOnStart() {
        var started = false

        composeRule.setContent {
            BabyTrackerTheme {
                IdleTimerContent(
                    selectedBreast = PumpingBreast.BOTH,
                    onBreastSelected = {},
                    onStart = { started = true },
                )
            }
        }

        composeRule.onNodeWithText("Start Pumping").performClick()

        composeRule.runOnIdle { assertTrue(started) }
    }

    @Test
    fun activeSessionShowsPauseButton() {
        val session = PumpingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            breast = PumpingBreast.BOTH,
        )

        composeRule.setContent {
            BabyTrackerTheme {
                ActiveTimerContent(
                    session = session,
                    onPause = {},
                    onResume = {},
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithText("Pause Session").assertIsDisplayed()
    }

    @Test
    fun pausedSessionShowsResumeButton() {
        val session = PumpingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            breast = PumpingBreast.BOTH,
            pausedAt = Instant.now().minusSeconds(60),
        )

        composeRule.setContent {
            BabyTrackerTheme {
                ActiveTimerContent(
                    session = session,
                    onPause = {},
                    onResume = {},
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithText("Resume Session").assertIsDisplayed()
    }

    @Test
    fun tapStopInvokesOnStop() {
        var stopped = false
        val session = PumpingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            breast = PumpingBreast.BOTH,
        )

        composeRule.setContent {
            BabyTrackerTheme {
                ActiveTimerContent(
                    session = session,
                    onPause = {},
                    onResume = {},
                    onStop = { stopped = true },
                )
            }
        }

        composeRule.onNodeWithText("Stop Session").performClick()

        composeRule.runOnIdle { assertTrue(stopped) }
    }

    @Test
    fun manualModeShowsSaveButton() {
        val manualState = ManualEntryState(
            startTime = Instant.now().minusSeconds(900),
            endTime = Instant.now(),
        )
        val state = PumpingUiState(mode = PumpingMode.MANUAL, manual = manualState)

        composeRule.setContent {
            BabyTrackerTheme {
                ManualModeContent(
                    state = state,
                    onFieldChange = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("Save Session").assertIsDisplayed()
    }

    @Test
    fun manualModeSaveIsDisabledWhileSaving() {
        val manualState = ManualEntryState(
            startTime = Instant.now().minusSeconds(900),
            endTime = Instant.now(),
            isSaving = true,
        )
        val state = PumpingUiState(mode = PumpingMode.MANUAL, manual = manualState)

        composeRule.setContent {
            BabyTrackerTheme {
                ManualModeContent(
                    state = state,
                    onFieldChange = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("Save Session").assertIsNotEnabled()
    }

    @Test
    fun manualModeShowsValidationError() {
        val manualState = ManualEntryState(
            startTime = Instant.now().minusSeconds(900),
            endTime = Instant.now(),
            validationError = "Enter volume in mL",
        )
        val state = PumpingUiState(mode = PumpingMode.MANUAL, manual = manualState)

        composeRule.setContent {
            BabyTrackerTheme {
                ManualModeContent(
                    state = state,
                    onFieldChange = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("Enter volume in mL").assertIsDisplayed()
    }

    @Test
    fun breastSelectorShowsAllThreeOptions() {
        composeRule.setContent {
            BabyTrackerTheme {
                IdleTimerContent(
                    selectedBreast = PumpingBreast.BOTH,
                    onBreastSelected = {},
                    onStart = {},
                )
            }
        }

        composeRule.onNodeWithText("Left").assertIsDisplayed()
        composeRule.onNodeWithText("Right").assertIsDisplayed()
        composeRule.onNodeWithText("Both").assertIsDisplayed()
    }

    @Test
    fun breastSelectorInvokesCallbackOnTap() {
        var selected: PumpingBreast? = null

        composeRule.setContent {
            BabyTrackerTheme {
                IdleTimerContent(
                    selectedBreast = PumpingBreast.BOTH,
                    onBreastSelected = { selected = it },
                    onStart = {},
                )
            }
        }

        composeRule.onNodeWithText("Left").performClick()

        composeRule.runOnIdle {
            assertTrue(selected == PumpingBreast.LEFT)
        }
    }
}
