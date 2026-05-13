package com.babytracker.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.ui.onboarding.components.AllergiesStepContent
import com.babytracker.ui.onboarding.components.BabyInfoStepContent
import com.babytracker.ui.onboarding.components.WelcomeStepContent
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertTrue
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
    fun welcomeStepSupportsNarrow2xFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                    WelcomeStepContent(
                        onGetStarted = {},
                        modifier = Modifier.requiredWidth(320.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Welcome to Akachan").assertIsDisplayed()
        composeRule.onNodeWithText("Optional partner view").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Set up baby profile").assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy),
        ).assertIsDisplayed()
    }

    @Test
    fun welcomePreviewIsHiddenFromTalkBack() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                WelcomeStepContent(onGetStarted = {})
            }
        }

        composeRule.onAllNodesWithText("Care").assertCountEquals(0)
        composeRule.onAllNodesWithText("Feeding").assertCountEquals(0)
        composeRule.onAllNodesWithText("Sleep").assertCountEquals(0)
    }

    @Test
    fun onboardingTextExposesHeadingsForTalkBackNavigation() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                AllergiesStepContent(
                    babyName = "Luna",
                    selectedAllergies = emptySet(),
                    customNote = "",
                    isSaving = false,
                    onAllergyToggled = {},
                    onAllergiesCleared = {},
                    onCustomNoteChanged = {},
                    onBack = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNode(
            hasText("Allergies")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("Any known allergies?")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("Known allergies")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertIsDisplayed()
    }

    @Test
    fun onboardingStepHeaderAnnouncesStepChangesPolitely() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                BabyInfoStepContent(
                    name = "Luna",
                    nameError = null,
                    selectedDate = LocalDate.now(),
                    birthDateError = null,
                    showAgeWarning = false,
                    isNextEnabled = true,
                    onNameChanged = {},
                    onDateSelected = {},
                    onBack = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNode(
            hasContentDescription("Step 2 of 3, Baby profile")
                .and(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)),
        ).assertIsDisplayed()
    }

    @Test
    fun babyInfoStepShowsProfileHeaderProgressAndAgeWarning() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                BabyInfoStepContent(
                    name = "Luna",
                    nameError = null,
                    selectedDate = LocalDate.now().minusMonths(14),
                    birthDateError = null,
                    showAgeWarning = true,
                    isNextEnabled = true,
                    onNameChanged = {},
                    onDateSelected = {},
                    onBack = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNodeWithText("Step 2 of 3").assertIsDisplayed()
        composeRule.onNodeWithText("Baby profile").assertIsDisplayed()
        composeRule.onNodeWithText("Start with the basics").assertIsDisplayed()
        composeRule.onNodeWithText("1 year, 2 months old").assertIsDisplayed()
        composeRule.onNodeWithText("Akachan is designed for babies 0-12 months.").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        composeRule.onNodeWithText("BABY INFO").assertDoesNotExist()
    }

    @Test
    fun babyInfoStepSupportsNarrow2xFontScaleWithValidation() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                    BabyInfoStepContent(
                        name = "Mia Sophia Isabelle Charlotte",
                        nameError = "Use 50 characters or fewer.",
                        selectedDate = LocalDate.now().minusMonths(14),
                        birthDateError = null,
                        showAgeWarning = true,
                        isNextEnabled = true,
                        onNameChanged = {},
                        onDateSelected = {},
                        onBack = {},
                        onNext = {},
                        modifier = Modifier.requiredWidth(320.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Baby profile").assertIsDisplayed()
        composeRule.onNodeWithText("Use 50 characters or fewer.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Akachan is designed for babies 0-12 months.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun dateOfBirthFieldExposesOneTalkBackButtonTarget() {
        val selectedDate = LocalDate.of(2025, 2, 3)

        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                BabyInfoStepContent(
                    name = "Luna",
                    nameError = null,
                    selectedDate = selectedDate,
                    birthDateError = null,
                    showAgeWarning = false,
                    isNextEnabled = true,
                    onNameChanged = {},
                    onDateSelected = {},
                    onBack = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNode(
            hasContentDescription("Date of birth, February 3, 2025")
                .and(hasClickAction()),
        ).assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription("Select date"), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun babyInfoStepShowsValidationErrors() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                BabyInfoStepContent(
                    name = "",
                    nameError = "Enter a name to continue.",
                    selectedDate = LocalDate.now(),
                    birthDateError = "Birth date cannot be in the future.",
                    showAgeWarning = false,
                    isNextEnabled = false,
                    onNameChanged = {},
                    onDateSelected = {},
                    onBack = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNodeWithText("Enter a name to continue.").assertIsDisplayed()
        composeRule.onNodeWithText("Birth date cannot be in the future.").assertIsDisplayed()
    }

    @Test
    fun noKnownAllergiesOptionExposesSelectedState() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                AllergiesStepContent(
                    babyName = "Luna",
                    selectedAllergies = emptySet(),
                    customNote = "",
                    isSaving = false,
                    onAllergyToggled = {},
                    onAllergiesCleared = {},
                    onCustomNoteChanged = {},
                    onBack = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNode(
            hasText("No known allergies")
                .and(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
                .and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected")),
        ).assertIsDisplayed()
    }

    @Test
    fun finishButtonExposesSavingStateForAccessibility() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                AllergiesStepContent(
                    babyName = "Luna",
                    selectedAllergies = emptySet(),
                    customNote = "",
                    isSaving = true,
                    onAllergyToggled = {},
                    onAllergiesCleared = {},
                    onCustomNoteChanged = {},
                    onBack = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNode(
            hasContentDescription("Saving setup")
                .and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Saving"))
                .and(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)),
        ).assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription("Saving setup")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
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
                    onAllergiesCleared = {},
                    onCustomNoteChanged = {},
                    onBack = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithText("Step 3 of 3").assertIsDisplayed()
        composeRule.onNodeWithText("Allergies").assertIsDisplayed()
        composeRule.onNodeWithText("Any known allergies?").assertIsDisplayed()
        composeRule.onNodeWithText("No known allergies").assertIsDisplayed()
        composeRule.onNodeWithText("Ready to save with no known allergies.").assertIsDisplayed()
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
                    onAllergiesCleared = {},
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
        composeRule.onNodeWithText("Step 3 of 3").assertDoesNotExist()
        composeRule.onNodeWithText("Finish setup").assertDoesNotExist()
    }

    @Test
    fun allergiesStepClearOptionCallsClearHandler() {
        var clearClicked = false
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                AllergiesStepContent(
                    babyName = "Luna",
                    selectedAllergies = setOf(AllergyType.CMPA),
                    customNote = "",
                    isSaving = false,
                    onAllergyToggled = {},
                    onAllergiesCleared = { clearClicked = true },
                    onCustomNoteChanged = {},
                    onBack = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithText("No known allergies").performClick()

        composeRule.runOnIdle {
            assertTrue(clearClicked)
        }
    }

    @Test
    fun allergiesStepNoKnownAllergiesClearsSelectionAndHidesOtherNote() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var selectedAllergies by remember {
                mutableStateOf(setOf(AllergyType.CMPA, AllergyType.OTHER))
            }
            var customNote by remember { mutableStateOf("Pears") }

            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                AllergiesStepContent(
                    babyName = "Luna",
                    selectedAllergies = selectedAllergies,
                    customNote = customNote,
                    isSaving = false,
                    onAllergyToggled = { allergy ->
                        selectedAllergies = if (allergy in selectedAllergies) {
                            selectedAllergies - allergy
                        } else {
                            selectedAllergies + allergy
                        }
                    },
                    onAllergiesCleared = {
                        selectedAllergies = emptySet()
                        customNote = ""
                    },
                    onCustomNoteChanged = { customNote = it },
                    onBack = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithText("Selected: Cow's Milk Protein, Other").assertIsDisplayed()
        composeRule.onNodeWithText("Describe the allergy").assertIsDisplayed()

        composeRule.onNodeWithText("No known allergies").performClick()
        composeRule.mainClock.advanceTimeBy(500)

        composeRule.onNodeWithText("Ready to save with no known allergies.").assertIsDisplayed()
        composeRule.onNodeWithText("Describe the allergy").assertDoesNotExist()
    }
}
