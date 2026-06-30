package com.babytracker.ui.onboarding

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabySex
import com.babytracker.domain.model.FeatureDomain
import com.babytracker.domain.model.FeatureSelection
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

enum class OnboardingStep { WELCOME, NAME, BIRTHDAY, SEX, TRACKERS, SUMMARY }

const val MAX_BABY_NAME_LENGTH = 50

// Retained for AllergiesStepContent's character counter, which is now reused only by Settings.
const val MAX_CUSTOM_ALLERGY_NOTE_LENGTH = 100

/** Steps that show a progress dot — the cover and final screens sit outside the count. */
val ONBOARDING_PROGRESS_STEPS = listOf(
    OnboardingStep.NAME,
    OnboardingStep.BIRTHDAY,
    OnboardingStep.SEX,
    OnboardingStep.TRACKERS,
)

private val LINE_BREAK_REGEX = Regex("[\\r\\n]+")

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val enabledFeatures: Set<AppFeature> = AppFeature.ALL,
    val babyName: String = "",
    val babyNameError: String? = null,
    val birthDate: LocalDate = LocalDate.now(),
    val birthDateError: String? = null,
    val sex: BabySex = BabySex.UNSPECIFIED,
    val bornEarly: Boolean = false,
    val dueDate: LocalDate? = null,
    val dueDateError: String? = null,
    val showAgeWarning: Boolean = false,
    val isSaving: Boolean = false,
    val savingError: Boolean = false,
    val navigationComplete: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saveBabyProfile: SaveBabyProfileUseCase,
    private val featureToggleRepository: FeatureToggleRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    val isNextEnabled: Boolean
        get() = with(_uiState.value) {
            when (currentStep) {
                OnboardingStep.WELCOME -> true
                OnboardingStep.NAME -> babyName.isNotBlank()
                OnboardingStep.BIRTHDAY -> isBirthdayStepValid
                OnboardingStep.SEX -> true
                OnboardingStep.TRACKERS -> enabledFeatures.isNotEmpty()
                OnboardingStep.SUMMARY -> true
            }
        }

    fun onFeatureToggled(feature: AppFeature, enabled: Boolean) {
        _uiState.update {
            it.copy(enabledFeatures = FeatureSelection.setFeature(it.enabledFeatures, feature, enabled))
        }
    }

    fun onDomainToggled(domain: FeatureDomain, enabled: Boolean) {
        _uiState.update {
            it.copy(enabledFeatures = FeatureSelection.setDomain(it.enabledFeatures, domain, enabled))
        }
    }

    fun onNameChanged(name: String) {
        val sanitizedName = name
            .replace(LINE_BREAK_REGEX, " ")
            .takeCodePoints(MAX_BABY_NAME_LENGTH)
        _uiState.update {
            it.copy(
                babyName = sanitizedName,
                babyNameError = if (sanitizedName.isNotBlank()) null else it.babyNameError,
            )
        }
    }

    fun onBirthDateSelected(date: LocalDate) {
        val today = LocalDate.now()
        if (date.isAfter(today)) {
            _uiState.update { it.copy(birthDateError = appContext.getString(R.string.error_birth_date_future)) }
            return
        }

        val monthsAgo = Period.between(date, today).toTotalMonths()
        _uiState.update {
            // Re-validate any existing due date against the new birth date.
            val dueDateError = it.dueDate
                ?.takeIf { due -> due.isBefore(date) }
                ?.let { appContext.getString(R.string.error_due_date_before_birth) }
            it.copy(
                birthDate = date,
                birthDateError = null,
                showAgeWarning = monthsAgo > 12,
                dueDateError = dueDateError,
            )
        }
    }

    fun onSexSelected(sex: BabySex) {
        _uiState.update { it.copy(sex = sex) }
    }

    fun onBornEarlyToggled(bornEarly: Boolean) {
        _uiState.update {
            it.copy(
                bornEarly = bornEarly,
                dueDate = if (bornEarly) it.dueDate else null,
                dueDateError = if (bornEarly) it.dueDateError else null,
            )
        }
    }

    fun onDueDateSelected(date: LocalDate) {
        _uiState.update {
            // A preterm baby is born before the due date, so the due date must be on or after birth.
            if (date.isBefore(it.birthDate)) {
                it.copy(dueDate = date, dueDateError = appContext.getString(R.string.error_due_date_before_birth))
            } else {
                it.copy(dueDate = date, dueDateError = null)
            }
        }
    }

    fun onNextStep() {
        _uiState.update { state ->
            when (state.currentStep) {
                OnboardingStep.WELCOME -> state.copy(currentStep = OnboardingStep.NAME)
                OnboardingStep.NAME ->
                    state.nextFromName(appContext.getString(R.string.error_name_required))
                OnboardingStep.BIRTHDAY ->
                    if (state.isBirthdayStepValid) {
                        state.copy(currentStep = OnboardingStep.SEX)
                    } else {
                        state
                    }
                OnboardingStep.SEX -> state.copy(currentStep = OnboardingStep.TRACKERS)
                OnboardingStep.TRACKERS -> state.copy(currentStep = OnboardingStep.SUMMARY)
                OnboardingStep.SUMMARY -> state
            }
        }
    }

    fun onPreviousStep() {
        _uiState.update { state ->
            when (state.currentStep) {
                OnboardingStep.WELCOME -> state
                OnboardingStep.NAME -> state.copy(currentStep = OnboardingStep.WELCOME)
                OnboardingStep.BIRTHDAY -> state.copy(currentStep = OnboardingStep.NAME)
                OnboardingStep.SEX -> state.copy(currentStep = OnboardingStep.BIRTHDAY)
                OnboardingStep.TRACKERS -> state.copy(currentStep = OnboardingStep.SEX)
                OnboardingStep.SUMMARY -> state.copy(currentStep = OnboardingStep.TRACKERS)
            }
        }
    }

    fun onFinish() {
        if (_uiState.value.babyName.isBlank()) {
            _uiState.update {
                it.copy(
                    currentStep = OnboardingStep.NAME,
                    babyNameError = appContext.getString(R.string.error_name_required),
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, savingError = false) }
            val state = _uiState.value
            val baby = Baby(
                name = state.babyName.trim(),
                birthDate = state.birthDate,
                sex = state.sex,
            )
            val userDueDate = state.dueDate?.takeIf { state.bornEarly }
            try {
                featureToggleRepository.setEnabledFeatures(state.enabledFeatures)
                saveBabyProfile(baby, userDueDate)
                _uiState.update { it.copy(isSaving = false, navigationComplete = true) }
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid baby profile during onboarding", e)
                _uiState.update { it.copy(isSaving = false, savingError = true) }
            } catch (e: IOException) {
                Log.w(TAG, "Unable to save baby profile during onboarding", e)
                _uiState.update { it.copy(isSaving = false, savingError = true) }
            }
        }
    }

    private companion object {
        const val TAG = "OnboardingViewModel"
    }
}

/** The birthday step is complete once the birth date is valid and, if born early, a valid due date is set. */
private val OnboardingUiState.isBirthdayStepValid: Boolean
    get() {
        val dueDateOk = !bornEarly || (dueDate != null && dueDateError == null)
        return birthDateError == null && dueDateOk
    }

private fun OnboardingUiState.nextFromName(nameRequiredError: String): OnboardingUiState =
    if (babyName.isNotBlank()) {
        copy(currentStep = OnboardingStep.BIRTHDAY, babyNameError = null)
    } else {
        copy(babyNameError = nameRequiredError)
    }

private fun String.takeCodePoints(maxCodePoints: Int): String {
    if (codePointCount(0, length) <= maxCodePoints) return this
    return substring(0, offsetByCodePoints(0, maxCodePoints))
}
