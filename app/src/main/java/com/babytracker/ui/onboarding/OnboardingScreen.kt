package com.babytracker.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.ui.onboarding.components.AllergiesStepContent
import com.babytracker.ui.onboarding.components.BirthDateStepContent
import com.babytracker.ui.onboarding.components.NameStepContent
import com.babytracker.ui.onboarding.components.StepIndicator

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

    BackHandler(enabled = uiState.currentStep != OnboardingStep.NAME) {
        viewModel.onPreviousStep()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            StepIndicator(
                currentStep = uiState.currentStep.ordinal,
                totalSteps = OnboardingStep.entries.size,
            )
            Spacer(modifier = Modifier.height(32.dp))

            AnimatedContent(targetState = uiState.currentStep, label = "onboarding_step") { step ->
                when (step) {
                    OnboardingStep.NAME -> NameStepContent(
                        name = uiState.babyName,
                        onNameChanged = viewModel::onNameChanged,
                        onNextStep = viewModel::onNextStep,
                    )
                    OnboardingStep.BIRTH_DATE -> BirthDateStepContent(
                        babyName = uiState.babyName,
                        selectedDate = uiState.birthDate,
                        showAgeWarning = uiState.showAgeWarning,
                        onDateSelected = viewModel::onBirthDateSelected,
                    )
                    OnboardingStep.ALLERGIES -> AllergiesStepContent(
                        babyName = uiState.babyName,
                        selectedAllergies = uiState.selectedAllergies,
                        customNote = uiState.customAllergyNote,
                        onAllergyToggled = viewModel::onAllergyToggled,
                        onCustomNoteChanged = viewModel::onCustomAllergyNoteChanged,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (uiState.currentStep == OnboardingStep.ALLERGIES) {
                        viewModel.onFinish(onComplete = onOnboardingComplete)
                    } else {
                        viewModel.onNextStep()
                    }
                },
                enabled = viewModel.isNextEnabled && !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (uiState.currentStep == OnboardingStep.ALLERGIES) "Get Started" else "Next"
                    )
                }
            }
        }
    }
}
