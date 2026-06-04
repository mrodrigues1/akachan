package com.babytracker.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.displayName
import com.babytracker.ui.theme.BabyTrackerTheme
import com.babytracker.domain.model.ThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SideSelectorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sideSelector_rendersBothSideOptions() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                SideSelector(selectedSide = null, onSideSelected = {})
            }
        }

        composeRule.onNodeWithText(BreastSide.LEFT.displayName()).assertIsDisplayed()
        composeRule.onNodeWithText(BreastSide.RIGHT.displayName()).assertIsDisplayed()
    }

    @Test
    fun sideSelector_exposesRadioButtonRoleForAccessibility() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                SideSelector(selectedSide = null, onSideSelected = {})
            }
        }

        val radioButtons = composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        )
        radioButtons.assertCountEquals(2)
    }

    @Test
    fun sideSelector_selectedSideMarkedAsSelected() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                SideSelector(selectedSide = BreastSide.LEFT, onSideSelected = {})
            }
        }

        composeRule.onNode(
            hasContentDescription("${BreastSide.LEFT.displayName()}, selected"),
        ).assertIsDisplayed()

        composeRule.onNode(
            hasContentDescription(BreastSide.RIGHT.displayName()),
        ).assertIsDisplayed()
    }

    @Test
    fun sideSelector_unselectedStateHasNeitherSideMarkedSelected() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                SideSelector(selectedSide = null, onSideSelected = {})
            }
        }

        composeRule.onAllNodes(
            hasContentDescription(", selected", substring = true),
        ).assertCountEquals(0)
    }

    @Test
    fun sideSelector_clickingLeftSideFiresCallback() {
        var selected: BreastSide? = null

        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                SideSelector(selectedSide = null, onSideSelected = { selected = it })
            }
        }

        composeRule.onNodeWithText(BreastSide.LEFT.displayName()).performClick()

        composeRule.runOnIdle {
            assertEquals(BreastSide.LEFT, selected)
        }
    }

    @Test
    fun sideSelector_clickingRightSideFiresCallback() {
        var selected: BreastSide? = null

        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                SideSelector(selectedSide = null, onSideSelected = { selected = it })
            }
        }

        composeRule.onNodeWithText(BreastSide.RIGHT.displayName()).performClick()

        composeRule.runOnIdle {
            assertEquals(BreastSide.RIGHT, selected)
        }
    }

    @Test
    fun sideSelector_selectionSwitchesBetweenSides() {
        composeRule.setContent {
            var selectedSide by remember { mutableStateOf<BreastSide?>(BreastSide.LEFT) }
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                SideSelector(
                    selectedSide = selectedSide,
                    onSideSelected = { selectedSide = it },
                )
            }
        }

        composeRule.onNode(hasContentDescription("${BreastSide.LEFT.displayName()}, selected"))
            .assertIsDisplayed()

        composeRule.onNodeWithText(BreastSide.RIGHT.displayName()).performClick()

        composeRule.onNode(hasContentDescription("${BreastSide.RIGHT.displayName()}, selected"))
            .assertIsDisplayed()
        composeRule.onNode(hasContentDescription("${BreastSide.LEFT.displayName()}, selected"))
            .assertDoesNotExist()
    }

    @Test
    fun sideSelector_eachSideMeetsTouchHeightRequirement() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                SideSelector(selectedSide = null, onSideSelected = {})
            }
        }

        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        ).apply {
            get(0).assertHeightIsAtLeast(88.dp)
            get(1).assertHeightIsAtLeast(88.dp)
        }
    }

    @Test
    fun sideSelector_leftSideExposesCorrectContentDescription() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                SideSelector(selectedSide = BreastSide.RIGHT, onSideSelected = {})
            }
        }

        composeRule.onNode(
            hasText(BreastSide.LEFT.displayName()).and(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
            ),
        ).assertIsDisplayed()

        composeRule.onNode(
            hasContentDescription("${BreastSide.RIGHT.displayName()}, selected"),
        ).assertIsDisplayed()
    }
}
