package com.babytracker.ui.onboarding.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.FeatureDomain
import com.babytracker.ui.features.FeaturePicker

@Composable
fun FeatureSelectionStepContent(
    enabledFeatures: Set<AppFeature>,
    onFeatureToggled: (AppFeature, Boolean) -> Unit,
    onDomainToggled: (FeatureDomain, Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactHeight = maxHeight < 560.dp || maxWidth > maxHeight
            val horizontalPadding = if (isCompactHeight) 20.dp else 24.dp
            val buttonBottomPadding = if (isCompactHeight) 16.dp else 24.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                OnboardingHeroStrip(
                    title = "What do you want to track?",
                    stepLabel = "Step 1 of 3",
                    progress = 0.33f,
                    accentColor = MaterialTheme.colorScheme.primary,
                    accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    accentContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onBack = onBack,
                    isCompactHeight = isCompactHeight,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontalPadding),
                ) {
                    Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 12.dp))
                    Text(
                        text = "Choose your trackers",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Everything is on to start. Turn off what you do not need; " +
                            "you can change this anytime in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(if (isCompactHeight) 16.dp else 22.dp))
                    FeaturePicker(
                        enabledFeatures = enabledFeatures,
                        onFeatureToggled = onFeatureToggled,
                        onDomainToggled = onDomainToggled,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        .padding(bottom = buttonBottomPadding)
                        .heightIn(min = 48.dp)
                        .testTag("onboarding_features_primary_action"),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Continue")
                }
            }
        }
    }
}
