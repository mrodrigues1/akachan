package com.babytracker.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.ui.onboarding.components.AllergiesStepContent
import com.babytracker.ui.onboarding.components.BabyInfoStepContent
import com.babytracker.ui.onboarding.components.WelcomeStepContent

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.savingError) {
        if (uiState.savingError) {
            snackbarHostState.showSnackbar("Could not save. Please try again.")
        }
    }

    BackHandler(enabled = uiState.currentStep != OnboardingStep.WELCOME) {
        viewModel.onPreviousStep()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        AnimatedContent(
            targetState = uiState.currentStep,
            label = "onboarding_step",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStepContent(
                    onGetStarted = viewModel::onNextStep,
                )
                OnboardingStep.BABY_INFO -> BabyInfoStepContent(
                    name = uiState.babyName,
                    selectedDate = uiState.birthDate,
                    showAgeWarning = uiState.showAgeWarning,
                    isNextEnabled = viewModel.isNextEnabled,
                    onNameChanged = viewModel::onNameChanged,
                    onDateSelected = viewModel::onBirthDateSelected,
                    onBack = viewModel::onPreviousStep,
                    onNext = viewModel::onNextStep,
                )
                OnboardingStep.ALLERGIES -> AllergiesStepContent(
                    babyName = uiState.babyName,
                    selectedAllergies = uiState.selectedAllergies,
                    customNote = uiState.customAllergyNote,
                    isSaving = uiState.isSaving,
                    onAllergyToggled = viewModel::onAllergyToggled,
                    onCustomNoteChanged = viewModel::onCustomAllergyNoteChanged,
                    onBack = viewModel::onPreviousStep,
                    onFinish = { viewModel.onFinish(onOnboardingComplete) },
                )
            }
        }
    }
}
