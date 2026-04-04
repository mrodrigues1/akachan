package com.babytracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class OnboardingUiState(
    val babyName: String = "",
    val birthDateMillis: Long? = null,
    val selectedAllergies: Set<AllergyType> = emptySet(),
    val isSaving: Boolean = false,
    val isComplete: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saveBabyProfile: SaveBabyProfileUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isOnboardingComplete: Flow<Boolean> = settingsRepository.isOnboardingComplete()

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(babyName = name)
    }

    fun onBirthDateSelected(millis: Long) {
        _uiState.value = _uiState.value.copy(birthDateMillis = millis)
    }

    fun onAllergyToggled(allergy: AllergyType) {
        val current = _uiState.value.selectedAllergies
        val updated = if (allergy in current) current - allergy else current + allergy
        _uiState.value = _uiState.value.copy(selectedAllergies = updated)
    }

    fun onSaveProfile() {
        val state = _uiState.value
        if (state.babyName.isBlank() || state.birthDateMillis == null) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            saveBabyProfile(
                Baby(
                    name = state.babyName,
                    birthDate = Instant.ofEpochMilli(state.birthDateMillis),
                    allergies = state.selectedAllergies.toList()
                )
            )
            settingsRepository.setOnboardingComplete(true)
            _uiState.value = _uiState.value.copy(isSaving = false, isComplete = true)
        }
    }
}
