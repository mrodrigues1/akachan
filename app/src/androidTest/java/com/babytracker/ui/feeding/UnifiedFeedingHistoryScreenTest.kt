package com.babytracker.ui.feeding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.DailyFeedingTotals
import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.FeedingDayGroup
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.ui.breastfeeding.BreastfeedingDeleteConfirmationDialog
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class UnifiedFeedingHistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val bottle = BottleFeed(
        id = 9L,
        timestamp = Instant.parse("2026-06-01T12:00:00Z"),
        volumeMl = 120,
        type = FeedType.FORMULA,
        createdAt = Instant.EPOCH,
    )

    private val session = BreastfeedingSession(
        id = 7L,
        startTime = Instant.parse("2026-06-01T10:00:00Z"),
        endTime = Instant.parse("2026-06-01T10:15:00Z"),
        startingSide = BreastSide.LEFT,
    )

    @Test
    fun rendersDayHeaderAndBothFeedTypes() {
        composeRule.setContent {
            BabyTrackerTheme {
                UnifiedFeedingHistoryContent(
                    state = sampleState(),
                    onEditBottle = {},
                    onDeleteBottle = {},
                    onEditBreastfeeding = {},
                    onDeleteBreastfeeding = {},
                )
            }
        }

        composeRule.onNodeWithText("JUN 1 · 120 ML · 2 FEEDS").assertIsDisplayed()
        composeRule.onNodeWithText("Formula bottle").assertIsDisplayed()
        composeRule.onNodeWithText("Left side").assertIsDisplayed()
    }

    @Test
    fun tappingBottleRowInvokesEdit() {
        var edited: BottleFeed? = null
        composeRule.setContent {
            BabyTrackerTheme {
                UnifiedFeedingHistoryContent(
                    state = sampleState(),
                    onEditBottle = { edited = it },
                    onDeleteBottle = {},
                    onEditBreastfeeding = {},
                    onDeleteBreastfeeding = {},
                )
            }
        }

        composeRule.onNodeWithText("Formula bottle").performClick()

        composeRule.runOnIdle { assertEquals(bottle, edited) }
    }

    @Test
    fun tappingBreastfeedingRowInvokesEdit() {
        var edited: BreastfeedingSession? = null
        composeRule.setContent {
            BabyTrackerTheme {
                UnifiedFeedingHistoryContent(
                    state = sampleState(),
                    onEditBottle = {},
                    onDeleteBottle = {},
                    onEditBreastfeeding = { edited = it },
                    onDeleteBreastfeeding = {},
                )
            }
        }

        composeRule.onNodeWithText("Left side").performClick()

        composeRule.runOnIdle { assertEquals(session, edited) }
    }

    @Test
    fun bottleOverflowDeleteShowsConfirmationAndConfirms() {
        var deleted = false
        composeRule.setContent {
            var pendingDelete by remember { mutableStateOf<BottleFeed?>(null) }
            BabyTrackerTheme {
                UnifiedFeedingHistoryContent(
                    state = sampleState(),
                    onEditBottle = {},
                    onDeleteBottle = { pendingDelete = it },
                    onEditBreastfeeding = {},
                    onDeleteBreastfeeding = {},
                )
                pendingDelete?.let {
                    FeedingDeleteConfirmationDialog(
                        onConfirm = {
                            deleted = true
                            pendingDelete = null
                        },
                        onDismiss = { pendingDelete = null },
                    )
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("More options")[0].performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Delete feed?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle { assertTrue(deleted) }
    }

    @Test
    fun breastfeedingOverflowDeleteShowsConfirmationAndConfirms() {
        var deleted = false
        composeRule.setContent {
            var pendingDelete by remember { mutableStateOf<BreastfeedingSession?>(null) }
            BabyTrackerTheme {
                UnifiedFeedingHistoryContent(
                    state = sampleState(),
                    onEditBottle = {},
                    onDeleteBottle = {},
                    onEditBreastfeeding = {},
                    onDeleteBreastfeeding = { pendingDelete = it },
                )
                pendingDelete?.let {
                    BreastfeedingDeleteConfirmationDialog(
                        onConfirm = {
                            deleted = true
                            pendingDelete = null
                        },
                        onDismiss = { pendingDelete = null },
                    )
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("More options")[1].performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Delete this session?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle { assertTrue(deleted) }
    }

    @Test
    fun emptyStateShowsWhenNoDays() {
        composeRule.setContent {
            BabyTrackerTheme {
                UnifiedFeedingHistoryContent(
                    state = FeedingHistoryUiState(isLoading = false),
                    onEditBottle = {},
                    onDeleteBottle = {},
                    onEditBreastfeeding = {},
                    onDeleteBreastfeeding = {},
                )
            }
        }

        composeRule.onNodeWithText("No feeds logged yet").assertIsDisplayed()
    }

    private fun sampleState() = FeedingHistoryUiState(
        days = listOf(
            FeedingDayGroup(
                date = LocalDate.of(2026, 6, 1),
                totals = DailyFeedingTotals(
                    bottleVolumeMl = 120,
                    bottleCount = 1,
                    breastfeedingCount = 1,
                ),
                entries = listOf(
                    FeedEntry.Bottle(bottle),
                    FeedEntry.Breastfeeding(session),
                ),
            ),
        ),
        volumeUnit = VolumeUnit.ML,
        isLoading = false,
    )
}
