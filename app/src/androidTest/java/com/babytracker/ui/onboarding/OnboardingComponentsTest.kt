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
import com.babytracker.domain.model.BabySex
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import io.mockk.mockk
import com.babytracker.ui.onboarding.components.AllergiesStepContent
import com.babytracker.ui.onboarding.components.BirthdayStepContent
import com.babytracker.ui.onboarding.components.NameStepContent
import com.babytracker.ui.onboarding.components.SexStepContent
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
    fun welcomeStepShowsAkachanCover() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                WelcomeStepContent(onGetStarted = {})
            }
        }

        composeRule.onNodeWithText("Welcome to Akachan").assertIsDisplayed()
        composeRule.onNodeWithText("A calm place to track feeds, sleep, and allergy notes during the first year.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Get started").assertIsDisplayed()
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
        composeRule.onNodeWithText("Get started").assertIsDisplayed()
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
        composeRule.onNodeWithText("Get started").assertIsDisplayed()
    }

    @Test
    fun welcomeHeroIsHiddenFromTalkBack() {
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
    fun nameStepPrimaryActionKeepsMinimumTouchHeight() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                NameStepContent(
                    name = "Luna",
                    nameError = null,
                    isNextEnabled = true,
                    onNameChanged = {},
                    onBack = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNodeWithTag("onboarding_name_primary_action").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun nameStepShowsQuestionAndValidationError() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                NameStepContent(
                    name = "",
                    nameError = "Enter a name to continue.",
                    isNextEnabled = false,
                    onNameChanged = {},
                    onBack = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNode(
            hasText("What should we call your baby?")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Enter a name to continue.").assertIsDisplayed()
    }

    @Test
    fun birthdayStepAnnouncesStepProgressPolitely() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                BirthdayStepContent(
                    babyName = "Luna",
                    selectedDate = LocalDate.now(),
                    birthDateError = null,
                    showAgeWarning = false,
                    bornEarly = false,
                    dueDate = null,
                    dueDateError = null,
                    isNextEnabled = true,
                    onDateSelected = {},
                    onBornEarlyToggled = {},
                    onDueDateSelected = {},
                    onBack = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNode(
            hasContentDescription("Step 2 of 4")
                .and(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)),
        ).assertIsDisplayed()
    }

    @Test
    fun birthdayStepShowsAgeWarningAndPersonalizedQuestion() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                BirthdayStepContent(
                    babyName = "Luna",
                    selectedDate = LocalDate.now().minusMonths(14),
                    birthDateError = null,
                    showAgeWarning = true,
                    bornEarly = false,
                    dueDate = null,
                    dueDateError = null,
                    isNextEnabled = true,
                    onDateSelected = {},
                    onBornEarlyToggled = {},
                    onDueDateSelected = {},
                    onBack = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNodeWithText("When was Luna born?").assertIsDisplayed()
        composeRule.onNodeWithText("1 year, 2 months old").assertIsDisplayed()
        composeRule.onNodeWithText("Akachan is designed for babies 0-12 months.").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun birthdayStepRevealsDueDateWhenBornEarly() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                BirthdayStepContent(
                    babyName = "Luna",
                    selectedDate = LocalDate.now().minusMonths(2),
                    birthDateError = null,
                    showAgeWarning = false,
                    bornEarly = true,
                    dueDate = null,
                    dueDateError = null,
                    isNextEnabled = true,
                    onDateSelected = {},
                    onBornEarlyToggled = {},
                    onDueDateSelected = {},
                    onBack = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNodeWithText("Born early?").assertIsDisplayed()
        composeRule.onNodeWithText("Due date").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dateOfBirthFieldExposesOneTalkBackButtonTarget() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                BirthdayStepContent(
                    babyName = "Luna",
                    selectedDate = LocalDate.of(2025, 2, 3),
                    birthDateError = null,
                    showAgeWarning = false,
                    bornEarly = false,
                    dueDate = null,
                    dueDateError = null,
                    isNextEnabled = true,
                    onDateSelected = {},
                    onBornEarlyToggled = {},
                    onDueDateSelected = {},
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
    fun sexStepShowsChoicesAndPersonalizedQuestion() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                SexStepContent(
                    babyName = "Luna",
                    selectedSex = BabySex.UNSPECIFIED,
                    onSexSelected = {},
                    onBack = {},
                    onNext = {},
                )
            }
        }

        composeRule.onNodeWithText("Is Luna a boy or a girl?").assertIsDisplayed()
        composeRule.onNodeWithText("Boy").assertIsDisplayed()
        composeRule.onNodeWithText("Girl").assertIsDisplayed()
        composeRule.onNodeWithText("Prefer not to say").assertIsDisplayed()
    }

    @Test
    fun onboardingScreenShowsSaveFailureSnackbar() {
        val viewModel = OnboardingViewModel(
            SaveBabyProfileUseCase(FailingBabyRepository(), mockk(relaxed = true)),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                OnboardingScreen(
                    onOnboardingComplete = {},
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithText("Get started").performClick() // WELCOME -> NAME
        composeRule.onNodeWithText("Baby's name").performTextInput("Luna")
        composeRule.onNodeWithText("Continue").performClick() // NAME -> BIRTHDAY
        composeRule.onNodeWithText("Continue").performClick() // BIRTHDAY -> SEX
        composeRule.onNodeWithText("Continue").performClick() // SEX -> TRACKERS
        composeRule.onNodeWithText("Continue").performClick() // TRACKERS -> SUMMARY
        composeRule.onNodeWithText("Enter app").performClick()

        composeRule.onNodeWithText("Could not save. Please try again.").assertIsDisplayed()
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
