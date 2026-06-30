package com.babytracker.ui.onboarding.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.BabySex
import com.babytracker.ui.component.labelRes
import com.babytracker.ui.onboarding.OnboardingStep

@Composable
fun SexStepContent(
    babyName: String,
    selectedSex: BabySex,
    onSexSelected: (BabySex) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val babyLabel = babyName.trim().ifEmpty { stringResource(R.string.onboarding_baby_fallback) }

    OnboardingScaffold(
        modifier = modifier,
        currentStep = OnboardingStep.SEX,
        onBack = onBack,
        primaryLabel = stringResource(R.string.onboarding_continue),
        onPrimary = onNext,
        primaryTestTag = "onboarding_sex_primary_action",
    ) {
        Text(
            text = stringResource(R.string.onboarding_sex_question, babyLabel),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_sex_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SexChoiceCard(
                label = stringResource(BabySex.MALE.labelRes()),
                selected = selectedSex == BabySex.MALE,
                onClick = { onSexSelected(BabySex.MALE) },
                modifier = Modifier.weight(1f),
            )
            SexChoiceCard(
                label = stringResource(BabySex.FEMALE.labelRes()),
                selected = selectedSex == BabySex.FEMALE,
                onClick = { onSexSelected(BabySex.FEMALE) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SexChoiceCard(
            label = stringResource(BabySex.UNSPECIFIED.labelRes()),
            selected = selectedSex == BabySex.UNSPECIFIED,
            onClick = { onSexSelected(BabySex.UNSPECIFIED) },
            modifier = Modifier.fillMaxWidth(),
            minHeight = 64.dp,
        )
    }
}

@Composable
private fun SexChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 96.dp,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val selectedState = stringResource(R.string.state_selected)
    val notSelectedState = stringResource(R.string.state_not_selected)

    Surface(
        modifier = modifier
            .height(minHeight)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                stateDescription = if (selected) selectedState else notSelectedState
            },
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
