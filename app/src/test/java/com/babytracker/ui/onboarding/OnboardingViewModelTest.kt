package com.babytracker.ui.onboarding

import android.content.Context
import com.babytracker.R
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private lateinit var saveBabyProfile: SaveBabyProfileUseCase
    private lateinit var featureToggleRepository: FeatureToggleRepository
    private lateinit var appContext: Context
    private lateinit var viewModel: OnboardingViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        saveBabyProfile = mockk()
        featureToggleRepository = mockk(relaxed = true)
        appContext = mockk {
            every { getString(R.string.error_name_required) } returns "Enter a name to continue."
            every { getString(R.string.error_birth_date_future) } returns "Birth date cannot be in the future."
        }
        viewModel = OnboardingViewModel(saveBabyProfile, featureToggleRepository, appContext)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** WELCOME -> FEATURES -> BABY_INFO. Sets a valid name so BABY_INFO can later advance. */
    private fun advanceToBabyInfo(name: String = "Luna") {
        viewModel.onNextStep() // WELCOME -> FEATURES
        viewModel.onNextStep() // FEATURES -> BABY_INFO
        viewModel.onNameChanged(name)
    }

    @Test
    fun `initial state is welcome step`() {
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.WELCOME, state.currentStep)
        assertEquals("", state.babyName)
    }

    @Test
    fun `onNextStep from welcome moves to features`() {
        viewModel.onNextStep()
        assertEquals(OnboardingStep.FEATURES, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep from features moves to baby info`() {
        viewModel.onNextStep() // WELCOME -> FEATURES
        viewModel.onNextStep() // FEATURES -> BABY_INFO
        assertEquals(OnboardingStep.BABY_INFO, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep from baby info moves to allergies`() {
        advanceToBabyInfo()
        viewModel.onNextStep() // BABY_INFO -> ALLERGIES
        assertEquals(OnboardingStep.ALLERGIES, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep from allergies stays on allergies`() {
        advanceToBabyInfo()
        viewModel.onNextStep() // -> ALLERGIES
        viewModel.onNextStep() // stays
        assertEquals(OnboardingStep.ALLERGIES, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep from welcome stays on welcome`() {
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep from features moves to welcome`() {
        viewModel.onNextStep() // WELCOME -> FEATURES
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep from baby info moves to features`() {
        viewModel.onNextStep() // WELCOME -> FEATURES
        viewModel.onNextStep() // FEATURES -> BABY_INFO
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.FEATURES, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep from allergies moves to baby info`() {
        advanceToBabyInfo()
        viewModel.onNextStep() // -> ALLERGIES
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.BABY_INFO, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `features step starts with all features selected`() {
        assertEquals(AppFeature.ALL, viewModel.uiState.value.enabledFeatures)
    }

    @Test
    fun `onFeatureToggled removes a feature`() {
        viewModel.onFeatureToggled(AppFeature.DIAPERS, enabled = false)
        assertEquals(AppFeature.ALL - AppFeature.DIAPERS, viewModel.uiState.value.enabledFeatures)
    }

    @Test
    fun `disabling the last feature is rejected`() {
        AppFeature.entries.filter { it != AppFeature.SLEEP }
            .forEach { viewModel.onFeatureToggled(it, enabled = false) }
        assertEquals(setOf(AppFeature.SLEEP), viewModel.uiState.value.enabledFeatures)

        viewModel.onFeatureToggled(AppFeature.SLEEP, enabled = false)
        assertEquals(setOf(AppFeature.SLEEP), viewModel.uiState.value.enabledFeatures)
    }

    @Test
    fun `onDomainToggled enabling a domain restores its features`() {
        viewModel.onDomainToggled(com.babytracker.domain.model.FeatureDomain.GROWTH_DEVELOPMENT, enabled = false)
        assertEquals(
            AppFeature.ALL - AppFeature.GROWTH - AppFeature.MILESTONES,
            viewModel.uiState.value.enabledFeatures,
        )
        viewModel.onDomainToggled(com.babytracker.domain.model.FeatureDomain.GROWTH_DEVELOPMENT, enabled = true)
        assertEquals(AppFeature.ALL, viewModel.uiState.value.enabledFeatures)
    }

    @Test
    fun `onNameChanged updates name`() {
        viewModel.onNameChanged("Luna")
        assertEquals("Luna", viewModel.uiState.value.babyName)
    }

    @Test
    fun `onNameChanged limits typed names to 50 characters`() {
        viewModel.onNameChanged("A".repeat(51))
        assertEquals("A".repeat(50), viewModel.uiState.value.babyName)
    }

    @Test
    fun `onNameChanged limits names by code point so emoji are not split`() {
        val babyEmoji = "👶"
        viewModel.onNameChanged(babyEmoji.repeat(51))
        assertEquals(babyEmoji.repeat(50), viewModel.uiState.value.babyName)
    }

    @Test
    fun `onNameChanged replaces pasted line breaks with spaces`() {
        viewModel.onNameChanged("Luna\nRose")
        assertEquals("Luna Rose", viewModel.uiState.value.babyName)
    }

    @Test
    fun `onNextStep from baby info with blank name stays on baby info and shows error`() {
        viewModel.onNextStep() // WELCOME -> FEATURES
        viewModel.onNextStep() // FEATURES -> BABY_INFO
        viewModel.onNextStep() // blank name, stays on BABY_INFO

        assertEquals(OnboardingStep.BABY_INFO, viewModel.uiState.value.currentStep)
        assertEquals("Enter a name to continue.", viewModel.uiState.value.babyNameError)
    }

    @Test
    fun `onNameChanged clears baby name error once name is valid`() {
        viewModel.onNextStep() // WELCOME -> FEATURES
        viewModel.onNextStep() // FEATURES -> BABY_INFO
        viewModel.onNextStep() // blank name -> error
        assertEquals("Enter a name to continue.", viewModel.uiState.value.babyNameError)

        viewModel.onNameChanged("Luna")

        assertEquals(null, viewModel.uiState.value.babyNameError)
    }

    @Test
    fun `isNextEnabled on welcome is always true`() {
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on features is true when at least one feature is on`() {
        viewModel.onNextStep() // -> FEATURES
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on baby info with blank name is true`() {
        viewModel.onNextStep() // -> FEATURES
        viewModel.onNextStep() // -> BABY_INFO
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on baby info with whitespace only name is true`() {
        viewModel.onNextStep() // -> FEATURES
        viewModel.onNextStep() // -> BABY_INFO
        viewModel.onNameChanged("   ")
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on baby info with valid name is true`() {
        viewModel.onNextStep() // -> FEATURES
        viewModel.onNextStep() // -> BABY_INFO
        viewModel.onNameChanged("Luna")
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on allergies is always true`() {
        advanceToBabyInfo()
        viewModel.onNextStep() // -> ALLERGIES
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `onBirthDateSelected over 12 months shows warning`() {
        val fourteenMonthsAgo = LocalDate.now().minusMonths(14)
        viewModel.onBirthDateSelected(fourteenMonthsAgo)
        assertTrue(viewModel.uiState.value.showAgeWarning)
    }

    @Test
    fun `onBirthDateSelected under 12 months no warning`() {
        val threeMonthsAgo = LocalDate.now().minusMonths(3)
        viewModel.onBirthDateSelected(threeMonthsAgo)
        assertFalse(viewModel.uiState.value.showAgeWarning)
    }

    @Test
    fun `onBirthDateSelected future date keeps current date and shows error`() {
        val initialDate = viewModel.uiState.value.birthDate
        viewModel.onBirthDateSelected(LocalDate.now().plusDays(1))

        assertEquals(initialDate, viewModel.uiState.value.birthDate)
        assertEquals("Birth date cannot be in the future.", viewModel.uiState.value.birthDateError)
    }

    @Test
    fun `onAllergyToggled adds and removes`() {
        viewModel.onAllergyToggled(AllergyType.CMPA)
        assertTrue(AllergyType.CMPA in viewModel.uiState.value.selectedAllergies)
        viewModel.onAllergyToggled(AllergyType.CMPA)
        assertFalse(AllergyType.CMPA in viewModel.uiState.value.selectedAllergies)
    }

    @Test
    fun `onAllergyToggled clears custom note when other is removed`() {
        viewModel.onAllergyToggled(AllergyType.OTHER)
        viewModel.onCustomAllergyNoteChanged("Pears")

        viewModel.onAllergyToggled(AllergyType.OTHER)

        assertFalse(AllergyType.OTHER in viewModel.uiState.value.selectedAllergies)
        assertEquals("", viewModel.uiState.value.customAllergyNote)
    }

    @Test
    fun `onCustomAllergyNoteChanged limits note by code point`() {
        val pearEmoji = "🍐"
        viewModel.onCustomAllergyNoteChanged(pearEmoji.repeat(101))
        assertEquals(pearEmoji.repeat(100), viewModel.uiState.value.customAllergyNote)
    }

    @Test
    fun `onCustomAllergyNoteChanged limits pasted note to three lines`() {
        viewModel.onCustomAllergyNoteChanged("one\ntwo\nthree\nfour")
        assertEquals("one\ntwo\nthree", viewModel.uiState.value.customAllergyNote)
    }

    @Test
    fun `onAllergiesCleared clears selected allergies and custom note`() {
        viewModel.onAllergyToggled(AllergyType.CMPA)
        viewModel.onAllergyToggled(AllergyType.OTHER)
        viewModel.onCustomAllergyNoteChanged("Pears")

        viewModel.onAllergiesCleared()

        assertEquals(emptySet<AllergyType>(), viewModel.uiState.value.selectedAllergies)
        assertEquals("", viewModel.uiState.value.customAllergyNote)
    }

    @Test
    fun `onFinish saves profile and sets navigationComplete`() = runTest {
        val babySlot = slot<Baby>()
        coJustRun { saveBabyProfile(capture(babySlot)) }

        viewModel.onNameChanged("Luna")
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { saveBabyProfile(any()) }
        assertEquals("Luna", babySlot.captured.name)
        assertTrue(viewModel.uiState.value.navigationComplete)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `onFinish persists chosen features`() = runTest {
        coJustRun { saveBabyProfile(any()) }
        viewModel.onFeatureToggled(AppFeature.DIAPERS, enabled = false)
        viewModel.onNameChanged("Luna")

        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { featureToggleRepository.setEnabledFeatures(AppFeature.ALL - AppFeature.DIAPERS) }
    }

    @Test
    fun `onFinish with blank name returns to baby info and does not save`() = runTest {
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { saveBabyProfile(any()) }
        coVerify(exactly = 0) { featureToggleRepository.setEnabledFeatures(any()) }
        assertEquals(OnboardingStep.BABY_INFO, viewModel.uiState.value.currentStep)
        assertEquals("Enter a name to continue.", viewModel.uiState.value.babyNameError)
    }

    @Test
    fun `onFinish on failure sets savingError and clears isSaving`() = runTest {
        coEvery { saveBabyProfile(any()) } throws IOException("storage error")

        viewModel.onNameChanged("Luna")
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.savingError)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.navigationComplete)
    }

    @Test
    fun `onFinish resets savingError before retry so snackbar can fire again`() = runTest {
        coEvery { saveBabyProfile(any()) } throws IOException("storage error")
        viewModel.onNameChanged("Luna")
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.savingError)

        // Second attempt succeeds — savingError must be false at the end
        coJustRun { saveBabyProfile(any()) }
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.savingError)
        assertTrue(viewModel.uiState.value.navigationComplete)
    }
}
