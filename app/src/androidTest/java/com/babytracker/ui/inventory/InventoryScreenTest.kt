package com.babytracker.ui.inventory

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.ExpirationStatus
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.model.MilkBagWithExpiration
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class InventoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val baseNow = Instant.ofEpochSecond(1_700_000_000L)

    private fun bag(id: Long, volumeMl: Int = 120) = MilkBag(
        id = id,
        collectionDate = baseNow.minusSeconds(id * 3600),
        volumeMl = volumeMl,
        createdAt = baseNow.minusSeconds(id * 3600),
    )

    private fun bagWithExpiration(id: Long, volumeMl: Int = 120) = MilkBagWithExpiration(
        bag = bag(id, volumeMl),
        status = ExpirationStatus.NONE,
    )

    @Test
    fun summaryCardShowsTotalsFromState() {
        val summary = InventorySummary(totalMl = 240, bagCount = 2, oldestBagDate = baseNow.minusSeconds(3600))
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryContent(
                    state = InventoryUiState(
                        summary = summary,
                        bags = listOf(bagWithExpiration(1), bagWithExpiration(2)),
                    ),
                    onEdit = {},
                    onMarkUsed = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("240 mL").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
    }

    @Test
    fun emptyStateCopyShownWhenBagsIsEmpty() {
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryContent(
                    state = InventoryUiState(),
                    onEdit = {},
                    onMarkUsed = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("No bags in your stash yet").assertIsDisplayed()
    }

    @Test
    fun emptyStateDoesNotShowSummaryCard() {
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryContent(
                    state = InventoryUiState(),
                    onEdit = {},
                    onMarkUsed = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("TOTAL").assertDoesNotExist()
    }

    @Test
    fun tapMarkUsedButtonInvokesOnMarkUsedForCorrectBag() {
        var markedBag: MilkBag? = null
        val bags = listOf(bagWithExpiration(1), bagWithExpiration(2))
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryContent(
                    state = InventoryUiState(bags = bags),
                    onEdit = {},
                    onMarkUsed = { markedBag = it },
                    onDelete = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Mark used").onFirst().performClick()

        composeRule.runOnIdle {
            assertNotNull(markedBag)
            assertEquals(1L, markedBag!!.id)
        }
    }

    @Test
    fun tappingOverflowAndDeleteInvokesOnDelete() {
        var deletedBag: MilkBag? = null
        val bags = listOf(bagWithExpiration(1))
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryContent(
                    state = InventoryUiState(bags = bags),
                    onEdit = {},
                    onMarkUsed = {},
                    onDelete = { deletedBag = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle {
            assertNotNull(deletedBag)
            assertEquals(1L, deletedBag!!.id)
        }
    }

    @Test
    fun tappingOverflowAndEditInvokesOnEdit() {
        var editedBag: MilkBag? = null
        val bags = listOf(bagWithExpiration(1))
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryContent(
                    state = InventoryUiState(bags = bags),
                    onEdit = { editedBag = it },
                    onMarkUsed = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Edit").performClick()

        composeRule.runOnIdle {
            assertNotNull(editedBag)
            assertEquals(1L, editedBag!!.id)
        }
    }

    @Test
    fun bagRowShowsVolumeAndIsDisplayed() {
        val bags = listOf(bagWithExpiration(1, volumeMl = 180))
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryContent(
                    state = InventoryUiState(bags = bags),
                    onEdit = {},
                    onMarkUsed = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("180 mL").assertIsDisplayed()
    }
}
