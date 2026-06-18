package com.babytracker.ui.onboarding

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.AllergyType
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

enum class OnboardingStep { WELCOME, FEATURES, BABY_INFO, ALLERGIES }

const val MAX_BABY_NAME_LENGTH = 50
const val MAX_CUSTOM_ALLERGY_NOTE_LENGTH = 100
const val MAX_CUSTOM_ALLERGY_NOTE_LINES = 3

private val LINE_BREAK_REGEX = Regex("[\\r\\n]+")

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val enabledFeatures: Set<AppFeature> = AppFeature.ALL,
    val babyName: String = "",
    val babyNameError: String? = null,
    val birthDate: LocalDate = LocalDate.now(),
    val birthDateError: String? = null,
    val sex: BabySex = BabySex.UNSPECIFIED,
    val selectedAllergies: Set<AllergyType> = emptySet(),
    val customAllergyNote: String = "",
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
        get() = when (_uiState.value.currentStep) {
            OnboardingStep.WELCOME -> true
            OnboardingStep.FEATURES -> _uiState.value.enabledFeatures.isNotEmpty()
            OnboardingStep.BABY_INFO -> _uiState.value.birthDateError == null
            OnboardingStep.ALLERGIES -> true
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
            it.copy(
                birthDate = date,
                birthDateError = null,
                showAgeWarning = monthsAgo > 12,
            )
        }
    }

    fun onSexSelected(sex: BabySex) {
        _uiState.update { it.copy(sex = sex) }
    }

    fun onAllergyToggled(allergy: AllergyType) {
        _uiState.update { state ->
            val updated = state.selectedAllergies.toMutableSet()
            val removingOther = allergy == AllergyType.OTHER && allergy in updated
            if (allergy in updated) updated.remove(allergy) else updated.add(allergy)
            state.copy(
                selectedAllergies = updated,
                customAllergyNote = if (removingOther) "" else state.customAllergyNote,
            )
        }
    }

    fun onAllergiesCleared() {
        _uiState.update {
            it.copy(
                selectedAllergies = emptySet(),
                customAllergyNote = "",
            )
        }
    }

    fun onCustomAllergyNoteChanged(note: String) {
        val sanitizedNote = note
            .normalizeLineBreaks()
            .takeLines(MAX_CUSTOM_ALLERGY_NOTE_LINES)
            .takeCodePoints(MAX_CUSTOM_ALLERGY_NOTE_LENGTH)
        _uiState.update { it.copy(customAllergyNote = sanitizedNote) }
    }

    fun onNextStep() {
        _uiState.update { state ->
            when (state.currentStep) {
                OnboardingStep.WELCOME -> state.copy(currentStep = OnboardingStep.FEATURES)
                OnboardingStep.FEATURES -> state.copy(currentStep = OnboardingStep.BABY_INFO)
                OnboardingStep.BABY_INFO ->
                    state.nextFromBabyInfo(appContext.getString(R.string.error_name_required))
                OnboardingStep.ALLERGIES -> state
            }
        }
    }

    fun onPreviousStep() {
        _uiState.update { state ->
            when (state.currentStep) {
                OnboardingStep.WELCOME -> state
                OnboardingStep.FEATURES -> state.copy(currentStep = OnboardingStep.WELCOME)
                OnboardingStep.BABY_INFO -> state.copy(currentStep = OnboardingStep.FEATURES)
                OnboardingStep.ALLERGIES -> state.copy(currentStep = OnboardingStep.BABY_INFO)
            }
        }
    }

    fun onFinish() {
        if (!_uiState.value.isBabyInfoValid()) {
            val nameRequiredError = appContext.getString(R.string.error_name_required)
            _uiState.update {
                it.withBabyInfoValidationErrors(nameRequiredError)
                    .copy(currentStep = OnboardingStep.BABY_INFO)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, savingError = false) }
            val state = _uiState.value
            val baby = Baby(
                name = state.babyName.trim(),
                birthDate = state.birthDate,
                allergies = state.selectedAllergies.toList(),
                customAllergyNote = state.customAllergyNote
                    .takeIf { AllergyType.OTHER in state.selectedAllergies && it.isNotBlank() },
                sex = state.sex,
            )
            try {
                featureToggleRepository.setEnabledFeatures(state.enabledFeatures)
                saveBabyProfile(baby)
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

private fun OnboardingUiState.isBabyInfoValid(): Boolean =
    babyName.isNotBlank() && birthDateError == null

private fun OnboardingUiState.nextFromBabyInfo(nameRequiredError: String): OnboardingUiState =
    if (isBabyInfoValid()) {
        copy(
            currentStep = OnboardingStep.ALLERGIES,
            babyNameError = null,
            birthDateError = null,
        )
    } else {
        withBabyInfoValidationErrors(nameRequiredError)
    }

private fun OnboardingUiState.withBabyInfoValidationErrors(nameRequiredError: String): OnboardingUiState =
    copy(
        babyNameError = if (babyName.isBlank()) nameRequiredError else babyNameError,
    )

private fun String.takeCodePoints(maxCodePoints: Int): String {
    if (codePointCount(0, length) <= maxCodePoints) return this
    return substring(0, offsetByCodePoints(0, maxCodePoints))
}

private fun String.normalizeLineBreaks(): String =
    replace(LINE_BREAK_REGEX, "\n")

private fun String.takeLines(maxLines: Int): String =
    lineSequence().take(maxLines).joinToString("\n")
