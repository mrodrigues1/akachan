package com.babytracker.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.ui.onboarding.components.AllergiesStepContent
import com.babytracker.ui.onboarding.components.BabyInfoStepContent
import com.babytracker.ui.onboarding.components.WelcomeStepContent
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OnboardingComponentsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun welcomeStepShowsAkachanSetupExperience() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                WelcomeStepContent(onGetStarted = {})
            }
        }

        composeRule.onNodeWithText("Welcome to Akachan").assertIsDisplayed()
        composeRule.onNodeWithText("A calm place to track feeds, sleep, and allergy notes during the first year.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Set up baby profile").assertIsDisplayed()
        composeRule.onNodeWithText("Welcome to Baby Tracker").assertDoesNotExist()
    }

    @Test
    fun babyInfoStepShowsProfileHeaderProgressAndAgeWarning() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                BabyInfoStepContent(
                    name = "Luna",
                    selectedDate = LocalDate.now().minusMonths(14),
                    showAgeWarning = true,
                    isNextEnabled = true,
                    onNameChanged = {},
                    onDateSelected = {},
                    onBack = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNodeWithText("STEP 2 OF 3").assertIsDisplayed()
        composeRule.onNodeWithText("Baby profile").assertIsDisplayed()
        composeRule.onNodeWithText("Start with the basics").assertIsDisplayed()
        composeRule.onNodeWithText("Akachan is designed for babies 0-12 months.").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        composeRule.onNodeWithText("BABY INFO").assertDoesNotExist()
    }

    @Test
    fun allergiesStepShowsFinalHeaderSummaryAndFinishAction() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                AllergiesStepContent(
                    babyName = "Luna",
                    selectedAllergies = emptySet(),
                    customNote = "",
                    isSaving = false,
                    onAllergyToggled = {},
                    onCustomNoteChanged = {},
                    onBack = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithText("STEP 3 OF 3").assertIsDisplayed()
        composeRule.onNodeWithText("Allergies").assertIsDisplayed()
        composeRule.onNodeWithText("Any known allergies?").assertIsDisplayed()
        composeRule.onNodeWithText("No known allergies selected.").assertIsDisplayed()
        composeRule.onNodeWithText("Finish setup").assertIsDisplayed()
        composeRule.onNodeWithText("ALLERGIES").assertDoesNotExist()
    }

    @Test
    fun allergiesStepEmbeddedModeHidesOnboardingChrome() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                AllergiesStepContent(
                    babyName = "Luna",
                    selectedAllergies = setOf(AllergyType.OTHER),
                    customNote = "Pears",
                    isSaving = false,
                    onAllergyToggled = {},
                    onCustomNoteChanged = {},
                    onBack = {},
                    onFinish = {},
                    showHeader = false,
                    showActions = false,
                )
            }
        }

        composeRule.onNodeWithText("Any known allergies?").assertIsDisplayed()
        composeRule.onNodeWithText("Describe the allergy").assertIsDisplayed()
        composeRule.onNodeWithText("STEP 3 OF 3").assertDoesNotExist()
        composeRule.onNodeWithText("Finish setup").assertDoesNotExist()
    }
}
