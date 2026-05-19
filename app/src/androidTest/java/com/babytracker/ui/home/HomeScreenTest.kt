package com.babytracker.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

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
    fun inventoryCard_showsNoBagsStored_whenSummaryIsEmpty() {
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryHomeCard(summary = InventorySummary.Empty, onClick = {})
            }
        }
        composeRule.onNodeWithText("No bags stored").assertIsDisplayed()
    }

    @Test
    fun inventoryCard_showsBagCount_whenSummaryHasBags() {
        val summary = InventorySummary(totalMl = 240, bagCount = 3, oldestBagDate = null)
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryHomeCard(summary = summary, onClick = {})
            }
        }
        composeRule.onNodeWithText("240 mL · 3 bags").assertIsDisplayed()
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
    fun inventoryCard_click_invokesCallback() {
        var tapped = false
        composeRule.setContent {
            BabyTrackerTheme {
                InventoryHomeCard(summary = InventorySummary.Empty, onClick = { tapped = true })
            }
        }
        composeRule.onNodeWithText("Inventory").performClick()
        assertTrue(tapped)
    }

    @Test
    fun allFourSummaryCards_areDisplayed_inTwoRows() {
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
                        InventoryHomeCard(
                            summary = InventorySummary.Empty,
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("Pumping").assertIsDisplayed()
        composeRule.onNodeWithText("Inventory").assertIsDisplayed()
    }
}
