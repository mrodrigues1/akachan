package com.babytracker.ui.pumping

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AddBagPromptSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun defaultState(volumeMl: Int = 120) = BagPromptState(
        sessionId = 1L,
        collectionDate = Instant.now(),
        volumeMl = volumeMl,
    )

    @Test
    fun sheetDisplaysPrefilledVolume() {
        composeRule.setContent {
            BabyTrackerTheme {
                AddBagPromptSheet(
                    state = defaultState(volumeMl = 150),
                    onFieldChange = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("150").assertIsDisplayed()
        composeRule.onNodeWithText("Add to stash?").assertIsDisplayed()
    }

    @Test
    fun tapAddToStashWithValidVolumeCallsOnConfirm() {
        var confirmed = false

        composeRule.setContent {
            BabyTrackerTheme {
                AddBagPromptSheet(
                    state = defaultState(volumeMl = 100),
                    onFieldChange = {},
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Add to stash").performClick()

        composeRule.runOnIdle {
            assertTrue(confirmed)
        }
    }

    @Test
    fun tapSkipCallsOnDismissWithoutConfirm() {
        var dismissed = false
        var confirmed = false

        composeRule.setContent {
            BabyTrackerTheme {
                AddBagPromptSheet(
                    state = defaultState(),
                    onFieldChange = {},
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText("Skip").performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
            assertFalse(confirmed)
        }
    }

    @Test
    fun confirmWhileSavingIsDisabled() {
        composeRule.setContent {
            BabyTrackerTheme {
                AddBagPromptSheet(
                    state = defaultState().copy(isSaving = true),
                    onFieldChange = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Add to stash").assertIsNotEnabled()
    }

    @Test
    fun volumeErrorIsDisplayedWhenPresent() {
        composeRule.setContent {
            BabyTrackerTheme {
                AddBagPromptSheet(
                    state = defaultState(volumeMl = 0).copy(volumeError = "Volume must be greater than 0"),
                    onFieldChange = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Volume must be greater than 0").assertIsDisplayed()
    }
}
