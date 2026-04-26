package com.babytracker.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

enum class OnboardingStep { WELCOME, BABY_INFO, ALLERGIES }

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val babyName: String = "",
    val birthDate: LocalDate = LocalDate.now(),
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    val isNextEnabled: Boolean
        get() = when (_uiState.value.currentStep) {
            OnboardingStep.WELCOME -> true
            OnboardingStep.BABY_INFO -> _uiState.value.babyName.isNotBlank()
            OnboardingStep.ALLERGIES -> true
        }

    fun onNameChanged(name: String) {
        if (name.length <= 50) {
            _uiState.update { it.copy(babyName = name) }
        }
    }

    fun onBirthDateSelected(date: LocalDate) {
        val monthsAgo = Period.between(date, LocalDate.now()).toTotalMonths()
        _uiState.update {
            it.copy(birthDate = date, showAgeWarning = monthsAgo > 12)
        }
    }

    fun onAllergyToggled(allergy: AllergyType) {
        _uiState.update { state ->
            val updated = state.selectedAllergies.toMutableSet()
            if (allergy in updated) updated.remove(allergy) else updated.add(allergy)
            state.copy(selectedAllergies = updated)
        }
    }

    fun onCustomAllergyNoteChanged(note: String) {
        if (note.length <= 100) {
            _uiState.update { it.copy(customAllergyNote = note) }
        }
    }

    fun onNextStep() {
        _uiState.update { state ->
            when (state.currentStep) {
                OnboardingStep.WELCOME -> state.copy(currentStep = OnboardingStep.BABY_INFO)
                OnboardingStep.BABY_INFO -> state.copy(currentStep = OnboardingStep.ALLERGIES)
                OnboardingStep.ALLERGIES -> state
            }
        }
    }

    fun onPreviousStep() {
        _uiState.update { state ->
            when (state.currentStep) {
                OnboardingStep.WELCOME -> state
                OnboardingStep.BABY_INFO -> state.copy(currentStep = OnboardingStep.WELCOME)
                OnboardingStep.ALLERGIES -> state.copy(currentStep = OnboardingStep.BABY_INFO)
            }
        }
    }

    fun onFinish() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, savingError = false) }
            val state = _uiState.value
            val baby = Baby(
                name = state.babyName.trim(),
                birthDate = state.birthDate,
                allergies = state.selectedAllergies.toList(),
                customAllergyNote = state.customAllergyNote
                    .takeIf { AllergyType.OTHER in state.selectedAllergies && it.isNotBlank() },
            )
            try {
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
