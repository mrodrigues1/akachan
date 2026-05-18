package com.babytracker.ui.onboarding

import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
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
    private lateinit var viewModel: OnboardingViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        saveBabyProfile = mockk()
        viewModel = OnboardingViewModel(saveBabyProfile)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is welcome step`() {
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.WELCOME, state.currentStep)
        assertEquals("", state.babyName)
    }

    @Test
    fun `onNextStep from welcome moves to baby info`() {
        viewModel.onNextStep()
        assertEquals(OnboardingStep.BABY_INFO, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep from baby info moves to allergies`() {
        viewModel.onNextStep() // WELCOME -> BABY_INFO
        viewModel.onNameChanged("Luna")
        viewModel.onNextStep() // BABY_INFO -> ALLERGIES
        assertEquals(OnboardingStep.ALLERGIES, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep from allergies stays on allergies`() {
        viewModel.onNextStep()
        viewModel.onNameChanged("Luna")
        viewModel.onNextStep()
        viewModel.onNextStep()
        assertEquals(OnboardingStep.ALLERGIES, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep from welcome stays on welcome`() {
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep from baby info moves to welcome`() {
        viewModel.onNextStep()
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep from allergies moves to baby info`() {
        viewModel.onNextStep()
        viewModel.onNameChanged("Luna")
        viewModel.onNextStep()
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.BABY_INFO, viewModel.uiState.value.currentStep)
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
        val babyEmoji = "\uD83D\uDC76"
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
        viewModel.onNextStep()
        viewModel.onNextStep()

        assertEquals(OnboardingStep.BABY_INFO, viewModel.uiState.value.currentStep)
        assertEquals("Enter a name to continue.", viewModel.uiState.value.babyNameError)
    }

    @Test
    fun `onNameChanged clears baby name error once name is valid`() {
        viewModel.onNextStep()
        viewModel.onNextStep()
        assertEquals("Enter a name to continue.", viewModel.uiState.value.babyNameError)

        viewModel.onNameChanged("Luna")

        assertEquals(null, viewModel.uiState.value.babyNameError)
    }

    @Test
    fun `isNextEnabled on welcome is always true`() {
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on baby info with blank name is true`() {
        viewModel.onNextStep() // move to BABY_INFO
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on baby info with whitespace only name is true`() {
        viewModel.onNextStep() // move to BABY_INFO
        viewModel.onNameChanged("   ")
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on baby info with valid name is true`() {
        viewModel.onNextStep()
        viewModel.onNameChanged("Luna")
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on allergies is always true`() {
        viewModel.onNextStep()
        viewModel.onNameChanged("Luna")
        viewModel.onNextStep()
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
        val pearEmoji = "\uD83C\uDF50"
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
    fun `onFinish with blank name returns to baby info and does not save`() = runTest {
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { saveBabyProfile(any()) }
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
