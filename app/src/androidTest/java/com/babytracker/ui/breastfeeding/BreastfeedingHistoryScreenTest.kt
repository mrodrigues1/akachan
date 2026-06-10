package com.babytracker.ui.breastfeeding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class BreastfeedingHistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleSession = BreastfeedingSession(
        id = 7L,
        startTime = Instant.parse("2026-05-14T22:00:00Z"),
        endTime = Instant.parse("2026-05-14T22:30:00Z"),
        startingSide = BreastSide.LEFT,
    )

    @Test
    fun cardShowsSideLabelAndOverflowMenuButton() {
        composeRule.setContent {
            BabyTrackerTheme {
                FeedHistoryCard(session = sampleSession, onEdit = {}, onDelete = {})
            }
        }

        composeRule.onNodeWithText("Left side").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More options").assertIsDisplayed()
    }

    @Test
    fun tappingCardBodyInvokesOnEdit() {
        var edited = false
        composeRule.setContent {
            BabyTrackerTheme {
                FeedHistoryCard(session = sampleSession, onEdit = { edited = true }, onDelete = {})
            }
        }

        composeRule.onNodeWithText("Left side").performClick()

        composeRule.runOnIdle { assertTrue(edited) }
    }

    @Test
    fun overflowMenuShowsEditAndDelete() {
        composeRule.setContent {
            BabyTrackerTheme {
                FeedHistoryCard(session = sampleSession, onEdit = {}, onDelete = {})
            }
        }

        composeRule.onNodeWithContentDescription("More options").performClick()

        composeRule.onNodeWithText("Edit").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun overflowDeleteInvokesOnDelete() {
        var deleted = false
        composeRule.setContent {
            BabyTrackerTheme {
                FeedHistoryCard(session = sampleSession, onEdit = {}, onDelete = { deleted = true })
            }
        }

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle { assertTrue(deleted) }
    }

    @Test
    fun overflowEditInvokesOnEdit() {
        var edited = false
        composeRule.setContent {
            BabyTrackerTheme {
                FeedHistoryCard(session = sampleSession, onEdit = { edited = true }, onDelete = {})
            }
        }

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Edit").performClick()

        composeRule.runOnIdle { assertTrue(edited) }
    }

    @Test
    fun confirmationDialogConfirmInvokesOnConfirm() {
        var confirmed = false
        composeRule.setContent {
            BabyTrackerTheme {
                BreastfeedingDeleteConfirmationDialog(onConfirm = { confirmed = true }, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Delete this session?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle { assertTrue(confirmed) }
    }

    @Test
    fun confirmationDialogCancelInvokesOnDismiss() {
        var dismissed = false
        composeRule.setContent {
            BabyTrackerTheme {
                BreastfeedingDeleteConfirmationDialog(onConfirm = {}, onDismiss = { dismissed = true })
            }
        }

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.runOnIdle { assertTrue(dismissed) }
    }
}
