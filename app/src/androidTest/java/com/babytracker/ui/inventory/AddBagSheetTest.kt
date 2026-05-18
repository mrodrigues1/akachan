package com.babytracker.ui.inventory

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AddBagSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val baseNow = Instant.ofEpochSecond(1_700_000_000L)

    private fun defaultState(volumeMl: String = "") = AddBagSheetState(
        collectionDate = baseNow,
        volumeMl = volumeMl,
    )

    @Test
    fun validationErrorIsDisplayedWhenStateHasError() {
        composeRule.setContent {
            BabyTrackerTheme {
                AddBagSheet(
                    state = defaultState(volumeMl = "0").copy(
                        validationError = "Volume must be greater than 0",
                    ),
                    onFieldChange = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Volume must be greater than 0").assertIsDisplayed()
    }

    @Test
    fun tapSaveBagCallsOnConfirm() {
        var confirmed = false
        composeRule.setContent {
            BabyTrackerTheme {
                AddBagSheet(
                    state = defaultState(volumeMl = "120"),
                    onFieldChange = {},
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Save bag").performClick()

        composeRule.runOnIdle {
            assertTrue(confirmed)
        }
    }

    @Test
    fun tapCancelCallsOnDismissWithoutConfirm() {
        var dismissed = false
        var confirmed = false
        composeRule.setContent {
            BabyTrackerTheme {
                AddBagSheet(
                    state = defaultState(),
                    onFieldChange = {},
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
            assertFalse(confirmed)
        }
    }

    @Test
    fun saveBagIsDisabledWhileSaving() {
        composeRule.setContent {
            BabyTrackerTheme {
                AddBagSheet(
                    state = defaultState(volumeMl = "120").copy(isSaving = true),
                    onFieldChange = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Save bag").assertIsNotEnabled()
    }

    @Test
    fun sheetDisplaysAddMilkBagTitle() {
        composeRule.setContent {
            BabyTrackerTheme {
                AddBagSheet(
                    state = defaultState(),
                    onFieldChange = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Add milk bag").assertIsDisplayed()
    }
}
