package com.babytracker.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.BreastSide
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeReorderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val noOpCallbacks = HomeTileCallbacks(
        onBreastfeeding = {},
        onSleep = {},
        onPumping = {},
        onInventory = {},
        onBottleFeed = {},
        onDiaper = {},
        onVaccine = {},
        onDoctorVisit = {},
        onFeedingHistory = {},
        onConnectPartner = {},
        onGrowth = {},
        onMilestones = {},
        onTrends = {},
    )

    @Test
    fun defaultOrder_rendersCoreTiles() {
        composeRule.setContent {
            BabyTrackerTheme {
                HomeContent(uiState = HomeUiState(), callbacks = noOpCallbacks, onReorder = {})
            }
        }
        composeRule.onNodeWithText("Breastfeeding").assertIsDisplayed()
        composeRule.onNodeWithText("Sleep").assertIsDisplayed()
        composeRule.onNodeWithText("Pumping").assertIsDisplayed()
        composeRule.onNodeWithText("Inventory").assertIsDisplayed()
        composeRule.onNodeWithText("Bottle feed").assertIsDisplayed()
        composeRule.onNodeWithText("Feeding history").assertIsDisplayed()
    }

    @Test
    fun tipTile_hidden_whenNoRecommendedSide() {
        composeRule.setContent {
            BabyTrackerTheme {
                HomeContent(
                    uiState = HomeUiState(nextRecommendedSide = null),
                    callbacks = noOpCallbacks,
                    onReorder = {},
                )
            }
        }
        composeRule.onNodeWithText("TIP").assertDoesNotExist()
    }

    @Test
    fun tipTile_visible_whenRecommendedSidePresent() {
        composeRule.setContent {
            BabyTrackerTheme {
                HomeContent(
                    uiState = HomeUiState(nextRecommendedSide = BreastSide.LEFT),
                    callbacks = noOpCallbacks,
                    onReorder = {},
                )
            }
        }
        // TIP sits near the bottom of the grid; scroll it into view before asserting so the test
        // is independent of screen size and tile count.
        composeRule.onNodeWithTag("home_tiles_grid").performScrollToNode(hasText("TIP"))
        composeRule.onNodeWithText("TIP").assertIsDisplayed()
    }

    @Test
    fun partnerTile_hidden_whenNotInNoneMode() {
        composeRule.setContent {
            BabyTrackerTheme {
                HomeContent(
                    uiState = HomeUiState(appMode = AppMode.PRIMARY),
                    callbacks = noOpCallbacks,
                    onReorder = {},
                )
            }
        }
        composeRule.onNodeWithText("Partner View").assertDoesNotExist()
    }
}
