package com.babytracker.ui.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingHeroStrip(
    title: String,
    stepLabel: String,
    progress: Float,
    accentColor: Color,
    accentContainerColor: Color,
    accentContentColor: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stepDescription = formatStepDescription(stepLabel)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics {
                contentDescription = "$stepDescription, $title"
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = accentContainerColor,
                contentColor = accentContentColor,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stepLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
            StepBadge(
                progress = progress,
                accentContainerColor = accentContainerColor,
                accentContentColor = accentContentColor,
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .semantics {
                    contentDescription = "$stepDescription progress"
                },
            color = accentColor,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatStepDescription(stepLabel: String): String =
    stepLabel.lowercase().replaceFirstChar { first ->
        if (first.isLowerCase()) first.titlecase() else first.toString()
    }

@Composable
private fun StepBadge(
    progress: Float,
    accentContainerColor: Color,
    accentContentColor: Color,
    modifier: Modifier = Modifier,
) {
    val stepNumber = when {
        progress >= 1f -> "3"
        progress >= 0.5f -> "2"
        else -> "1"
    }

    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = accentContainerColor,
        contentColor = accentContentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
