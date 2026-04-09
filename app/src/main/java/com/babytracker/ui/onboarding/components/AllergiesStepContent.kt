package com.babytracker.ui.onboarding.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.AllergyType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergiesStepContent(
    babyName: String,
    selectedAllergies: Set<AllergyType>,
    customNote: String,
    isSaving: Boolean,
    onAllergyToggled: (AllergyType) -> Unit,
    onCustomNoteChanged: (String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val stripHeight = 88.dp
        val cardHeight = maxHeight - stripHeight + 16.dp

        // Blue hero strip — secondary color distinguishes this step from Baby Info
        OnboardingHeroStrip(
            title = "ALLERGIES",
            gradientColors = listOf(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.surface,
            ),
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 32.dp),
            ) {
                LinearProgressIndicator(
                    progress = { 1.0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Does $babyName have any known allergies?",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        AllergyType.entries.forEach { allergy ->
                            val selected = allergy in selectedAllergies
                            FilterChip(
                                selected = selected,
                                onClick = { onAllergyToggled(allergy) },
                                label = { Text(allergy.label) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = AllergyType.OTHER in selectedAllergies,
                        enter = slideInVertically(),
                        exit = slideOutVertically(),
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customNote,
                                onValueChange = onCustomNoteChanged,
                                label = { Text("Describe the allergy") },
                                singleLine = false,
                                maxLines = 3,
                                supportingText = { Text("${customNote.length}/100") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (selectedAllergies.isEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "You can always update this later in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onFinish,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Get Started")
                    }
                }
            }
        }
    }
}
