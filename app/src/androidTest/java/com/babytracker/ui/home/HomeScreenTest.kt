package com.babytracker.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
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
    fun inventoryStripCard_showsNoBagsStored_whenSummaryIsEmpty() {
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryStripCard(summary = InventorySummary.Empty, onClick = {})
            }
        }
        composeRule.onNodeWithText("No bags stored").assertIsDisplayed()
    }

    @Test
    fun inventoryStripCard_showsBagCount_whenSummaryHasBags() {
        val summary = InventorySummary(totalMl = 240, bagCount = 3, oldestBagDate = null)
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryStripCard(summary = summary, onClick = {})
            }
        }
        composeRule.onNodeWithText("240 mL · 3 bags").assertIsDisplayed()
    }

    @Test
    fun lastNightSleepCard_showsNoDataYet_whenDurationIsNull() {
        composeRule.setContent {
            BabyTrackerTheme {
                LastNightSleepCard(duration = null, onClick = {})
            }
        }
        composeRule.onNodeWithText("No data yet").assertIsDisplayed()
    }

    @Test
    fun lastNightSleepCard_showsDuration_whenDurationIsSet() {
        composeRule.setContent {
            BabyTrackerTheme {
                LastNightSleepCard(duration = Duration.ofHours(6).plusMinutes(42), onClick = {})
            }
        }
        composeRule.onNodeWithText("6h 42m").assertIsDisplayed()
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
    fun inventoryStripCard_click_invokesCallback() {
        var tapped = false
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryStripCard(summary = InventorySummary.Empty, onClick = { tapped = true })
            }
        }
        composeRule.onNodeWithText("Inventory").performClick()
        assertTrue(tapped)
    }

    @Test
    fun secondRowCards_pumpingAndLastNight_areDisplayed() {
        composeRule.setContent {
            BabyTrackerTheme {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PumpingHomeCard(
                            active = null,
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        )
                        LastNightSleepCard(
                            duration = null,
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        )
                    }
                    InventoryStripCard(
                        summary = InventorySummary.Empty,
                        onClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("Pumping").assertIsDisplayed()
        composeRule.onNodeWithText("LAST NIGHT").assertIsDisplayed()
        composeRule.onNodeWithText("Inventory").assertIsDisplayed()
    }

    @Test
    fun cueRow_allChipsDisplayed() {
        composeRule.setContent {
            BabyTrackerTheme {
                CueQuickTapRow(onCueTapped = {})
            }
        }
        composeRule.onNodeWithText("😪 Sleepy").assertIsDisplayed()
        composeRule.onNodeWithText("😋 Hungry").assertIsDisplayed()
        composeRule.onNodeWithText("😣 Fussy").assertIsDisplayed()
        composeRule.onNodeWithText("🤒 Sick").assertIsDisplayed()
        composeRule.onNodeWithText("🦷 Teething").assertIsDisplayed()
        composeRule.onNodeWithText("✈️ Travel").assertIsDisplayed()
    }

    @Test
    fun cueRow_tapCallsCallback() {
        var tappedType: BabyEventType? = null
        composeRule.setContent {
            BabyTrackerTheme {
                CueQuickTapRow(onCueTapped = { tappedType = it })
            }
        }
        composeRule.onNodeWithText("😪 Sleepy").performClick()
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
        composeRule.onNodeWithText("😪 Sleepy").performClick()
        composeRule.mainClock.advanceTimeBy(100L)
        // Chip must be selected immediately after tap
        composeRule.onNodeWithText("😪 Sleepy").assertIsSelected()
        // Advance past the 1 200 ms window
        composeRule.mainClock.advanceTimeBy(1_300L)
        // Chip must return to unselected
        composeRule.onNodeWithText("😪 Sleepy").assertIsNotSelected()
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
        composeRule.onNodeWithText("😪 Sleepy").performClick()
        composeRule.mainClock.advanceTimeBy(800L)
        // Second tap at 800 ms — would expire at 2 000 ms if timer is reset
        composeRule.onNodeWithText("😪 Sleepy").performClick()
        // Advance to 1 900 ms total (past first tap's 1 200 ms, but only 1 100 ms past second tap)
        composeRule.mainClock.advanceTimeBy(1_100L)
        // First removal job should have been cancelled — chip still selected
        composeRule.onNodeWithText("😪 Sleepy").assertIsSelected()
        // Advance 300 ms more (2 200 ms total; 1 400 ms past second tap)
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.onNodeWithText("😪 Sleepy").assertIsNotSelected()
        composeRule.mainClock.autoAdvance = true
    }
}
