package com.babytracker.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.ui.onboarding.components.BirthdayStepContent
import com.babytracker.ui.onboarding.components.NameStepContent
import com.babytracker.ui.onboarding.components.SexStepContent
import com.babytracker.ui.onboarding.components.SummaryStepContent
import com.babytracker.ui.onboarding.components.TrackersStepContent
import com.babytracker.ui.onboarding.components.WelcomeStepContent

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savingErrorMessage = stringResource(R.string.error_onboarding_save)

    LaunchedEffect(uiState.savingError) {
        if (uiState.savingError) {
            snackbarHostState.showSnackbar(savingErrorMessage)
        }
    }
    LaunchedEffect(uiState.navigationComplete) {
        if (uiState.navigationComplete) onOnboardingComplete()
    }

    BackHandler(enabled = uiState.currentStep != OnboardingStep.WELCOME) {
        viewModel.onPreviousStep()
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
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
                OnboardingStep.NAME -> NameStepContent(
                    name = uiState.babyName,
                    nameError = uiState.babyNameError,
                    isNextEnabled = viewModel.isNextEnabled,
                    onNameChanged = viewModel::onNameChanged,
                    onBack = viewModel::onPreviousStep,
                    onNext = viewModel::onNextStep,
                )
                OnboardingStep.BIRTHDAY -> BirthdayStepContent(
                    babyName = uiState.babyName,
                    selectedDate = uiState.birthDate,
                    birthDateError = uiState.birthDateError,
                    showAgeWarning = uiState.showAgeWarning,
                    bornEarly = uiState.bornEarly,
                    dueDate = uiState.dueDate,
                    dueDateError = uiState.dueDateError,
                    isNextEnabled = viewModel.isNextEnabled,
                    onDateSelected = viewModel::onBirthDateSelected,
                    onBornEarlyToggled = viewModel::onBornEarlyToggled,
                    onDueDateSelected = viewModel::onDueDateSelected,
                    onBack = viewModel::onPreviousStep,
                    onNext = viewModel::onNextStep,
                )
                OnboardingStep.SEX -> SexStepContent(
                    babyName = uiState.babyName,
                    selectedSex = uiState.sex,
                    onSexSelected = viewModel::onSexSelected,
                    onBack = viewModel::onPreviousStep,
                    onNext = viewModel::onNextStep,
                )
                OnboardingStep.TRACKERS -> TrackersStepContent(
                    enabledFeatures = uiState.enabledFeatures,
                    isNextEnabled = viewModel.isNextEnabled,
                    onFeatureToggled = viewModel::onFeatureToggled,
                    onDomainToggled = viewModel::onDomainToggled,
                    onBack = viewModel::onPreviousStep,
                    onNext = viewModel::onNextStep,
                )
                OnboardingStep.SUMMARY -> SummaryStepContent(
                    babyName = uiState.babyName,
                    isSaving = uiState.isSaving,
                    onBack = viewModel::onPreviousStep,
                    onFinish = viewModel::onFinish,
                )
            }
        }
    }
}
