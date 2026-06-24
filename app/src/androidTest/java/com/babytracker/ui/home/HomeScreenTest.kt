package com.babytracker.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.model.TodayFeedingSummary
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import com.babytracker.ui.component.CueQuickTapRow

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pumpingCard_showsTapToLog_whenNoActiveSession() {
        composeRule.setContent {
            BabyTrackerTheme {
                PumpingHomeCard(active = null, onClick = {})
            }
        }
        composeRule.onNodeWithText("Tap to log").assertIsDisplayed()
    }

    @Test
    fun pumpingCard_showsLiveBadge_whenActiveSessionExists() {
        val session = PumpingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(120),
            breast = PumpingBreast.BOTH,
        )
        composeRule.setContent {
            BabyTrackerTheme {
                PumpingHomeCard(active = session, onClick = {})
            }
        }
        composeRule.onNodeWithText("Live").assertIsDisplayed()
    }

    @Test
    fun inventoryHomeCard_showsNoBagsStored_whenSummaryIsEmpty() {
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryHomeCard(summary = InventorySummary.Empty, volumeUnit = VolumeUnit.ML, onClick = {})
            }
        }
        composeRule.onNodeWithText("No bags stored").assertIsDisplayed()
    }

    @Test
    fun inventoryHomeCard_showsBagCount_whenSummaryHasBags() {
        val summary = InventorySummary(totalMl = 240, bagCount = 3, oldestBagDate = null)
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryHomeCard(summary = summary, volumeUnit = VolumeUnit.ML, onClick = {})
            }
        }
        composeRule.onNodeWithText("240 ml · 3 bags").assertIsDisplayed()
    }

    @Test
    fun pumpingCard_click_invokesCallback() {
        var tapped = false
        composeRule.setContent {
            BabyTrackerTheme {
                PumpingHomeCard(active = null, onClick = { tapped = true })
            }
        }
        composeRule.onNodeWithText("Pumping").performClick()
        assertTrue(tapped)
    }

    @Test
    fun inventoryHomeCard_click_invokesCallback() {
        var tapped = false
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryHomeCard(summary = InventorySummary.Empty, volumeUnit = VolumeUnit.ML, onClick = { tapped = true })
            }
        }
        composeRule.onNodeWithText("Inventory").performClick()
        assertTrue(tapped)
    }

    @Test
    fun secondRowCards_pumpingAndInventory_areDisplayed() {
        composeRule.setContent {
            BabyTrackerTheme {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PumpingHomeCard(
                        active = null,
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                    InventoryHomeCard(
                        summary = InventorySummary.Empty,
                        volumeUnit = VolumeUnit.ML,
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        composeRule.onNodeWithText("Pumping").assertIsDisplayed()
        composeRule.onNodeWithText("Inventory").assertIsDisplayed()
    }

    @Test
    fun feedingHistoryCard_showsEmptySummary_whenNoFeedsToday() {
        composeRule.setContent {
            BabyTrackerTheme {
                FeedingHistoryHomeCard(
                    summary = TodayFeedingSummary(),
                    volumeUnit = VolumeUnit.ML,
                    onClick = {},
                )
            }
        }
        composeRule.onNodeWithText("Feeding history").assertIsDisplayed()
        composeRule.onNodeWithText("No feeds today").assertIsDisplayed()
    }

    @Test
    fun feedingHistoryCard_showsVolumeAndCount_whenFeedsExist() {
        composeRule.setContent {
            BabyTrackerTheme {
                FeedingHistoryHomeCard(
                    summary = TodayFeedingSummary(bottleVolumeMl = 240, bottleCount = 3, breastfeedingCount = 2),
                    volumeUnit = VolumeUnit.ML,
                    onClick = {},
                )
            }
        }
        composeRule.onNodeWithText("240 ml · 5 feeds today").assertIsDisplayed()
    }

    @Test
    fun feedingHistoryCard_click_invokesCallback() {
        var tapped = false
        composeRule.setContent {
            BabyTrackerTheme {
                FeedingHistoryHomeCard(
                    summary = TodayFeedingSummary(),
                    volumeUnit = VolumeUnit.ML,
                    onClick = { tapped = true },
                )
            }
        }
        composeRule.onNodeWithText("Feeding history").performClick()
        assertTrue(tapped)
    }

    @Test
    fun cueRow_allChipsDisplayed() {
        composeRule.setContent {
            BabyTrackerTheme {
                CueQuickTapRow(onCueTapped = {})
            }
        }
        composeRule.onNodeWithText("Sleepy").assertIsDisplayed()
        composeRule.onNodeWithText("Hungry").assertIsDisplayed()
        composeRule.onNodeWithText("Fussy").assertIsDisplayed()
        composeRule.onNodeWithText("Sick").assertIsDisplayed()
        composeRule.onNodeWithText("Teething").assertIsDisplayed()
        composeRule.onNodeWithText("Travel").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cueRow_tapCallsCallback() {
        var tappedType: BabyEventType? = null
        composeRule.setContent {
            BabyTrackerTheme {
                CueQuickTapRow(onCueTapped = { tappedType = it })
            }
        }
        composeRule.onNodeWithText("Sleepy").performClick()
        assertTrue(tappedType == BabyEventType.SLEEPY_CUE)
    }

    @Test
    fun cueRow_chipIsSelectedThenReturnsToUnselectedAfterDelay() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BabyTrackerTheme {
                CueQuickTapRow(onCueTapped = {})
            }
        }
        composeRule.onNodeWithText("Sleepy").performClick()
        composeRule.mainClock.advanceTimeBy(100L)
        // Chip must be selected immediately after tap
        composeRule.onNodeWithText("Sleepy").assertIsSelected()
        // Advance past the 1 500 ms deselection delay
        composeRule.mainClock.advanceTimeBy(1_600L)
        // Chip must return to unselected
        composeRule.onNodeWithText("Sleepy").assertIsNotSelected()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun cueRow_repeatedTapExtendsSelectedWindow() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BabyTrackerTheme {
                CueQuickTapRow(onCueTapped = {})
            }
        }
        // First tap
        composeRule.onNodeWithText("Sleepy").performClick()
        composeRule.mainClock.advanceTimeBy(800L)
        // Second tap at 800 ms — resets the 1 500 ms deselection timer
        composeRule.onNodeWithText("Sleepy").performClick()
        // Advance to 1 900 ms total (past first tap's 1 500 ms, but only 1 100 ms past second tap)
        composeRule.mainClock.advanceTimeBy(1_100L)
        // First removal job should have been cancelled — chip still selected
        composeRule.onNodeWithText("Sleepy").assertIsSelected()
        // Advance 500 ms more (2 400 ms total; 1 600 ms past second tap — past the 1 500 ms window)
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithText("Sleepy").assertIsNotSelected()
        composeRule.mainClock.autoAdvance = true
    }
}
