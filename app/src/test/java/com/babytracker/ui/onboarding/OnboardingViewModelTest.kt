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
        viewModel.onNextStep() // BABY_INFO -> ALLERGIES
        assertEquals(OnboardingStep.ALLERGIES, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep from allergies stays on allergies`() {
        viewModel.onNextStep()
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
    fun `onNameChanged exceeds 50 chars is ignored`() {
        viewModel.onNameChanged("A".repeat(51))
        assertEquals("", viewModel.uiState.value.babyName)
    }

    @Test
    fun `isNextEnabled on welcome is always true`() {
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on baby info with blank name is false`() {
        viewModel.onNextStep() // move to BABY_INFO
        assertFalse(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on baby info with whitespace only name is false`() {
        viewModel.onNextStep() // move to BABY_INFO
        viewModel.onNameChanged("   ")
        assertFalse(viewModel.isNextEnabled)
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
    fun `onAllergyToggled adds and removes`() {
        viewModel.onAllergyToggled(AllergyType.CMPA)
        assertTrue(AllergyType.CMPA in viewModel.uiState.value.selectedAllergies)
        viewModel.onAllergyToggled(AllergyType.CMPA)
        assertFalse(AllergyType.CMPA in viewModel.uiState.value.selectedAllergies)
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
