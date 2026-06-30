package com.babytracker.ui.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.ui.onboarding.OnboardingStep

@Composable
fun SummaryStepContent(
    babyName: String,
    isSaving: Boolean,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val babyLabel = babyName.trim().ifEmpty { stringResource(R.string.onboarding_baby_fallback) }

    OnboardingScaffold(
        modifier = modifier,
        currentStep = OnboardingStep.SUMMARY,
        onBack = onBack,
        primaryLabel = stringResource(R.string.onboarding_enter_app),
        onPrimary = onFinish,
        isSaving = isSaving,
        primaryTestTag = "onboarding_summary_primary_action",
    ) { isCompactHeight ->
        SummaryHero(isCompactHeight = isCompactHeight)
        Spacer(modifier = Modifier.height(if (isCompactHeight) 18.dp else 28.dp))
        Text(
            text = stringResource(R.string.onboarding_summary_title, babyLabel),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_summary_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        PartnerInfoCard()
    }
}

@Composable
private fun SummaryHero(
    isCompactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val height = if (isCompactHeight) 96.dp else 160.dp
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.large)
            .background(gradient)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(if (isCompactHeight) 56.dp else 72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(if (isCompactHeight) 30.dp else 40.dp),
                )
            }
        }
    }
}

@Composable
private fun PartnerInfoCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.onboarding_partner_card_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_partner_card_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
