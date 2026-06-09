package com.babytracker.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import io.mockk.mockk
import com.babytracker.ui.onboarding.components.AllergiesStepContent
import com.babytracker.ui.onboarding.components.BabyInfoStepContent
import com.babytracker.ui.onboarding.components.WelcomeStepContent
import com.babytracker.ui.theme.BabyTrackerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
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

        composeRule.onNodeWithText("Welcome to Akachan").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Optional partner view").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Set up baby profile").assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy),
        ).assertIsDisplayed()
    }

    @Test
    fun welcomeStepSupportsLandscapeDark2xFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                BabyTrackerTheme(themeConfig = ThemeConfig.DARK) {
                    WelcomeStepContent(
                        onGetStarted = {},
                        modifier = Modifier.requiredSize(width = 640.dp, height = 360.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Welcome to Akachan").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Optional partner view").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Set up baby profile").assertIsDisplayed()
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
    fun welcomePrimaryActionKeepsMinimumTouchHeight() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                WelcomeStepContent(onGetStarted = {})
            }
        }

        composeRule.onNodeWithTag("onboarding_welcome_primary_action").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun babyInfoPrimaryActionKeepsMinimumTouchHeight() {
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

        composeRule.onNodeWithTag("onboarding_baby_info_primary_action").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun allergiesPrimaryActionKeepsMinimumTouchHeight() {
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

        composeRule.onNodeWithTag("onboarding_allergies_primary_action").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun onboardingScreenShowsSaveFailureSnackbar() {
        val viewModel = OnboardingViewModel(
            SaveBabyProfileUseCase(FailingBabyRepository(), mockk(relaxed = true)),
        )

        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                OnboardingScreen(
                    onOnboardingComplete = {},
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithText("Set up baby profile").performClick()
        composeRule.onNodeWithText("Baby's name").performTextInput("Luna")
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Finish setup").performClick()

        composeRule.onNodeWithText("Could not save. Please try again.").assertIsDisplayed()
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
            hasText("Allergy notes")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("Any allergies to note?")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("Add known allergies")
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
    fun babyInfoStepSupportsSmallHeight2xFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                    BabyInfoStepContent(
                        name = "Luna",
                        nameError = null,
                        selectedDate = LocalDate.now().minusMonths(2),
                        birthDateError = null,
                        showAgeWarning = false,
                        isNextEnabled = true,
                        onNameChanged = {},
                        onDateSelected = {},
                        onBack = {},
                        onNext = {},
                        modifier = Modifier.requiredSize(width = 320.dp, height = 420.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Baby profile").assertIsDisplayed()
        composeRule.onNodeWithText("Date of birth").performScrollTo().assertIsDisplayed()
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
            hasText("None known yet")
                .and(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
                .and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No known allergies selected")),
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
        composeRule.onNodeWithText("Allergy notes").assertIsDisplayed()
        composeRule.onNodeWithText("Any allergies to note?").assertIsDisplayed()
        composeRule.onNodeWithText("None known yet").assertIsDisplayed()
        composeRule.onNodeWithText("No allergies will be saved yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Finish setup").assertIsDisplayed()
        composeRule.onNodeWithText("ALLERGIES").assertDoesNotExist()
    }

    @Test
    fun allergiesStepSupportsLandscapeDark2xFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                BabyTrackerTheme(themeConfig = ThemeConfig.DARK) {
                    AllergiesStepContent(
                        babyName = "Luna",
                        selectedAllergies = setOf(AllergyType.CMPA, AllergyType.OTHER),
                        customNote = "Pears",
                        isSaving = false,
                        onAllergyToggled = {},
                        onAllergiesCleared = {},
                        onCustomNoteChanged = {},
                        onBack = {},
                        onFinish = {},
                        modifier = Modifier.requiredSize(width = 640.dp, height = 360.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Allergy notes").assertIsDisplayed()
        composeRule.onNodeWithText("Describe other allergy").performScrollTo().assertExists()
        composeRule.onNodeWithText("Finish setup").assertIsDisplayed()
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

        composeRule.onNodeWithText("Any allergies to note?").assertIsDisplayed()
        composeRule.onNodeWithText("Describe other allergy").assertIsDisplayed()
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

        composeRule.onNodeWithText("None known yet").performClick()

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

        composeRule.onNodeWithText("Will save: Cow's Milk Protein, Other").assertIsDisplayed()
        composeRule.onNodeWithText("Describe other allergy").assertIsDisplayed()

        composeRule.onNodeWithText("None known yet").performClick()
        composeRule.mainClock.advanceTimeBy(500)

        composeRule.onNodeWithText("No allergies will be saved yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Describe other allergy").assertDoesNotExist()
    }

    private class FailingBabyRepository : BabyRepository {
        override fun getBabyProfile(): Flow<Baby?> = flowOf(null)

        override suspend fun saveBabyProfile(baby: Baby) {
            throw IOException("storage error")
        }

        override fun isOnboardingComplete(): Flow<Boolean> = flowOf(false)
    }
}
