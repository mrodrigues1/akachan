package com.babytracker.ui.onboarding.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.FeatureDomain
import com.babytracker.ui.features.FeaturePicker
import com.babytracker.ui.onboarding.OnboardingStep

@Composable
fun TrackersStepContent(
    enabledFeatures: Set<AppFeature>,
    isNextEnabled: Boolean,
    onFeatureToggled: (AppFeature, Boolean) -> Unit,
    onDomainToggled: (FeatureDomain, Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        modifier = modifier,
        currentStep = OnboardingStep.TRACKERS,
        onBack = onBack,
        primaryLabel = stringResource(R.string.onboarding_continue),
        onPrimary = onNext,
        primaryEnabled = isNextEnabled,
        primaryTestTag = "onboarding_trackers_primary_action",
    ) {
        Text(
            text = stringResource(R.string.onboarding_trackers_question),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_trackers_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(22.dp))
        FeaturePicker(
            enabledFeatures = enabledFeatures,
            onFeatureToggled = onFeatureToggled,
            onDomainToggled = onDomainToggled,
        )
    }
}
