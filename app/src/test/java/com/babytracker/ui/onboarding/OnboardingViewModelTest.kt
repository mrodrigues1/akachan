package com.babytracker.ui.onboarding

import android.content.Context
import com.babytracker.R
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabySex
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.LocalDate
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private lateinit var saveBabyProfile: SaveBabyProfileUseCase
    private lateinit var featureToggleRepository: FeatureToggleRepository
    private lateinit var appContext: Context
    private lateinit var viewModel: OnboardingViewModel
    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(testDispatcher)

    @BeforeEach
    fun setUp() {
        saveBabyProfile = mockk()
        featureToggleRepository = mockk(relaxed = true)
        appContext = mockk {
            every { getString(R.string.error_name_required) } returns "Enter a name to continue."
            every { getString(R.string.error_birth_date_future) } returns "Birth date cannot be in the future."
            every { getString(R.string.error_due_date_before_birth) } returns "Due date can't be before the birth date."
        }
        viewModel = OnboardingViewModel(saveBabyProfile, featureToggleRepository, appContext)
    }

    /** WELCOME -> NAME -> (named) so the wizard can advance past the name gate. */
    private fun advanceToBirthday(name: String = "Luna") {
        viewModel.onNextStep() // WELCOME -> NAME
        viewModel.onNameChanged(name)
        viewModel.onNextStep() // NAME -> BIRTHDAY
    }

    @Test
    fun `initial state is welcome step`() {
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.WELCOME, state.currentStep)
        assertEquals("", state.babyName)
    }

    @Test
    fun `onNextStep from welcome moves to name`() {
        viewModel.onNextStep()
        assertEquals(OnboardingStep.NAME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep from name with blank name stays and shows error`() {
        viewModel.onNextStep() // -> NAME
        viewModel.onNextStep() // blank name, stays
        assertEquals(OnboardingStep.NAME, viewModel.uiState.value.currentStep)
        assertEquals("Enter a name to continue.", viewModel.uiState.value.babyNameError)
    }

    @Test
    fun `onNextStep from name with valid name moves to birthday`() {
        viewModel.onNextStep() // -> NAME
        viewModel.onNameChanged("Luna")
        viewModel.onNextStep()
        assertEquals(OnboardingStep.BIRTHDAY, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep from birthday moves to sex`() {
        advanceToBirthday()
        viewModel.onNextStep()
        assertEquals(OnboardingStep.SEX, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep from sex moves to trackers`() {
        advanceToBirthday()
        viewModel.onNextStep() // -> SEX
        viewModel.onNextStep() // -> TRACKERS
        assertEquals(OnboardingStep.TRACKERS, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep from trackers moves to summary`() {
        advanceToBirthday()
        viewModel.onNextStep() // -> SEX
        viewModel.onNextStep() // -> TRACKERS
        viewModel.onNextStep() // -> SUMMARY
        assertEquals(OnboardingStep.SUMMARY, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep from welcome stays on welcome`() {
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep walks back through the wizard`() {
        advanceToBirthday()
        viewModel.onNextStep() // -> SEX
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.BIRTHDAY, viewModel.uiState.value.currentStep)
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.NAME, viewModel.uiState.value.currentStep)
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
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
    fun `onNameChanged updates name and limits length by code point`() {
        viewModel.onNameChanged("Luna")
        assertEquals("Luna", viewModel.uiState.value.babyName)

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
    fun `isNextEnabled on name requires a non-blank name`() {
        viewModel.onNextStep() // -> NAME
        assertFalse(viewModel.isNextEnabled)
        viewModel.onNameChanged("   ")
        assertFalse(viewModel.isNextEnabled)
        viewModel.onNameChanged("Luna")
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on sex is always true`() {
        advanceToBirthday()
        viewModel.onNextStep() // -> SEX
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `onBirthDateSelected over 12 months shows warning`() {
        viewModel.onBirthDateSelected(LocalDate.now().minusMonths(14))
        assertTrue(viewModel.uiState.value.showAgeWarning)
    }

    @Test
    fun `onBirthDateSelected under 12 months no warning`() {
        viewModel.onBirthDateSelected(LocalDate.now().minusMonths(3))
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
    fun `onBornEarlyToggled off clears due date`() {
        viewModel.onBornEarlyToggled(true)
        viewModel.onBirthDateSelected(LocalDate.now().minusMonths(2))
        viewModel.onDueDateSelected(LocalDate.now().minusMonths(1))
        assertTrue(viewModel.uiState.value.dueDate != null)

        viewModel.onBornEarlyToggled(false)
        assertNull(viewModel.uiState.value.dueDate)
        assertFalse(viewModel.uiState.value.bornEarly)
    }

    @Test
    fun `onDueDateSelected before birth date sets error`() {
        viewModel.onBirthDateSelected(LocalDate.now().minusMonths(2))
        viewModel.onDueDateSelected(LocalDate.now().minusMonths(3))
        assertEquals("Due date can't be before the birth date.", viewModel.uiState.value.dueDateError)
    }

    @Test
    fun `isNextEnabled on birthday is false while due date is invalid`() {
        advanceToBirthday()
        viewModel.onBirthDateSelected(LocalDate.now().minusMonths(2))
        viewModel.onBornEarlyToggled(true)
        viewModel.onDueDateSelected(LocalDate.now().minusMonths(3)) // before birth -> error
        assertFalse(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled on birthday is false when born early without a due date`() {
        advanceToBirthday()
        viewModel.onBirthDateSelected(LocalDate.now().minusMonths(2))
        viewModel.onBornEarlyToggled(true)
        assertFalse(viewModel.isNextEnabled)

        viewModel.onDueDateSelected(LocalDate.now().minusMonths(1))
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `onFinish saves profile with name and sex and sets navigationComplete`() = runTest {
        val babySlot = slot<Baby>()
        coJustRun { saveBabyProfile(capture(babySlot), any()) }

        viewModel.onNameChanged("Luna")
        viewModel.onSexSelected(BabySex.FEMALE)
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { saveBabyProfile(any(), any()) }
        assertEquals("Luna", babySlot.captured.name)
        assertEquals(BabySex.FEMALE, babySlot.captured.sex)
        assertTrue(viewModel.uiState.value.navigationComplete)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `onFinish passes the user due date when born early`() = runTest {
        val dueSlot = slot<LocalDate?>()
        coJustRun { saveBabyProfile(any(), captureNullable(dueSlot)) }
        val birth = LocalDate.now().minusMonths(2)
        val due = LocalDate.now().minusMonths(1)

        viewModel.onNameChanged("Luna")
        viewModel.onBirthDateSelected(birth)
        viewModel.onBornEarlyToggled(true)
        viewModel.onDueDateSelected(due)
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(due, dueSlot.captured)
    }

    @Test
    fun `onFinish does not pass a due date when not born early`() = runTest {
        val dueSlot = slot<LocalDate?>()
        coJustRun { saveBabyProfile(any(), captureNullable(dueSlot)) }

        viewModel.onNameChanged("Luna")
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(dueSlot.captured)
    }

    @Test
    fun `onFinish persists chosen features`() = runTest {
        coJustRun { saveBabyProfile(any(), any()) }
        viewModel.onFeatureToggled(AppFeature.DIAPERS, enabled = false)
        viewModel.onNameChanged("Luna")

        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { featureToggleRepository.setEnabledFeatures(AppFeature.ALL - AppFeature.DIAPERS) }
    }

    @Test
    fun `onFinish with blank name returns to name step and does not save`() = runTest {
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { saveBabyProfile(any(), any()) }
        coVerify(exactly = 0) { featureToggleRepository.setEnabledFeatures(any()) }
        assertEquals(OnboardingStep.NAME, viewModel.uiState.value.currentStep)
        assertEquals("Enter a name to continue.", viewModel.uiState.value.babyNameError)
    }

    @Test
    fun `onFinish on failure sets savingError and clears isSaving`() = runTest {
        coEvery { saveBabyProfile(any(), any()) } throws IOException("storage error")

        viewModel.onNameChanged("Luna")
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.savingError)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.navigationComplete)
    }

    @Test
    fun `onFinish resets savingError before retry so snackbar can fire again`() = runTest {
        coEvery { saveBabyProfile(any(), any()) } throws IOException("storage error")
        viewModel.onNameChanged("Luna")
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.savingError)

        coJustRun { saveBabyProfile(any(), any()) }
        viewModel.onFinish()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.savingError)
        assertTrue(viewModel.uiState.value.navigationComplete)
    }
}
