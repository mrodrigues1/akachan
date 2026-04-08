package com.babytracker.ui.onboarding

import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
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
    fun `initial_state_isNameStep`() {
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.NAME, state.currentStep)
        assertEquals("", state.babyName)
    }

    @Test
    fun `onNameChanged_updatesName`() {
        viewModel.onNameChanged("Luna")
        assertEquals("Luna", viewModel.uiState.value.babyName)
    }

    @Test
    fun `onNameChanged_exceeds50Chars_ignored`() {
        viewModel.onNameChanged("A".repeat(51))
        assertEquals("", viewModel.uiState.value.babyName)
    }

    @Test
    fun `onNextStep_fromName_movesToBirthDate`() {
        viewModel.onNameChanged("Luna")
        viewModel.onNextStep()
        assertEquals(OnboardingStep.BIRTH_DATE, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep_fromBirthDate_movesToName`() {
        viewModel.onNameChanged("Luna")
        viewModel.onNextStep()
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.NAME, viewModel.uiState.value.currentStep)
        assertEquals("Luna", viewModel.uiState.value.babyName)
    }

    @Test
    fun `onBirthDateSelected_over12Months_showsWarning`() {
        val fourteenMonthsAgo = LocalDate.now().minusMonths(14)
        viewModel.onBirthDateSelected(fourteenMonthsAgo)
        assertTrue(viewModel.uiState.value.showAgeWarning)
    }

    @Test
    fun `onBirthDateSelected_under12Months_noWarning`() {
        val threeMonthsAgo = LocalDate.now().minusMonths(3)
        viewModel.onBirthDateSelected(threeMonthsAgo)
        assertFalse(viewModel.uiState.value.showAgeWarning)
    }

    @Test
    fun `onAllergyToggled_addsAndRemoves`() {
        viewModel.onAllergyToggled(AllergyType.CMPA)
        assertTrue(AllergyType.CMPA in viewModel.uiState.value.selectedAllergies)

        viewModel.onAllergyToggled(AllergyType.CMPA)
        assertFalse(AllergyType.CMPA in viewModel.uiState.value.selectedAllergies)
    }

    @Test
    fun `onFinish_savesProfile_callsOnComplete`() = runTest {
        val babySlot = slot<Baby>()
        coJustRun { saveBabyProfile(capture(babySlot)) }

        viewModel.onNameChanged("Luna")
        var completeCalled = false
        viewModel.onFinish { completeCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { saveBabyProfile(any()) }
        assertEquals("Luna", babySlot.captured.name)
        assertTrue(completeCalled)
    }

    @Test
    fun `isNextEnabled_blankName_false`() {
        viewModel.onNameChanged("  ")
        assertFalse(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled_validName_true`() {
        viewModel.onNameChanged("Luna")
        assertTrue(viewModel.isNextEnabled)
    }
}
