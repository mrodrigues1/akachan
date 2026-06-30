package com.babytracker.ui.onboarding.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.ui.onboarding.ONBOARDING_PROGRESS_STEPS
import com.babytracker.ui.onboarding.OnboardingStep
import com.babytracker.ui.theme.LocalDarkTheme

/**
 * Shared chrome for every wizard step after the cover: a progress header (back arrow +
 * step dots), a scrollable content area, and a single bottom primary action. Screens
 * supply only their question content and the action label, keeping each step lean.
 */
@Composable
fun OnboardingScaffold(
    currentStep: OnboardingStep,
    onBack: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    isSaving: Boolean = false,
    primaryTestTag: String? = null,
    content: @Composable ColumnScope.(isCompactHeight: Boolean) -> Unit,
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
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                OnboardingProgressHeader(
                    currentStep = currentStep,
                    onBack = onBack,
                    isCompactHeight = isCompactHeight,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontalPadding),
                ) {
                    Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 16.dp))
                    content(isCompactHeight)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                val buttonModifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .padding(bottom = buttonBottomPadding)
                    .heightIn(min = 48.dp)
                    .then(if (primaryTestTag != null) Modifier.testTag(primaryTestTag) else Modifier)
                Button(
                    onClick = onPrimary,
                    enabled = primaryEnabled && !isSaving,
                    modifier = buttonModifier,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    if (isSaving) {
                        SavingButtonContent()
                    } else {
                        Text(primaryLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingButtonContent() {
    val savingSetupDescription = stringResource(R.string.onboarding_saving_setup_cd)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(20.dp)
                .semantics { contentDescription = savingSetupDescription },
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
        )
        Text(stringResource(R.string.onboarding_saving))
    }
}

@Composable
private fun OnboardingProgressHeader(
    currentStep: OnboardingStep,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isCompactHeight: Boolean = false,
) {
    val total = ONBOARDING_PROGRESS_STEPS.size
    val rawIndex = ONBOARDING_PROGRESS_STEPS.indexOf(currentStep)
    // SUMMARY (and any non-progress step) reads as the final step so every dot is filled.
    val activeIndex = if (rawIndex >= 0) rawIndex else total - 1
    val stepIndicator = stringResource(R.string.onboarding_step_indicator, activeIndex + 1, total)
    val border = if (LocalDarkTheme.current) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isCompactHeight) 16.dp else 20.dp,
                vertical = if (isCompactHeight) 8.dp else 16.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = border,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        }
        Row(
            modifier = Modifier.semantics {
                contentDescription = stepIndicator
                liveRegion = LiveRegionMode.Polite
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(total) { index ->
                ProgressDot(active = index <= activeIndex)
            }
        }
    }
}

@Composable
private fun ProgressDot(active: Boolean) {
    val color by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "onboarding_progress_dot",
    )
    Surface(
        modifier = Modifier
            .height(8.dp)
            .width(if (active) 24.dp else 8.dp)
            .clearAndSetSemantics {},
        shape = RoundedCornerShape(4.dp),
        color = color,
    ) {}
}
