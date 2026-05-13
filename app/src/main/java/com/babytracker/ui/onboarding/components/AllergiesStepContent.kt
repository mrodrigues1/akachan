package com.babytracker.ui.onboarding.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
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
    onAllergiesCleared: () -> Unit,
    onCustomNoteChanged: (String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    showActions: Boolean = true,
) {
    val rootModifier = if (showHeader || showActions) {
        modifier.fillMaxSize()
    } else {
        modifier.fillMaxWidth()
    }
    val babyLabel = babyName.trim().takeIf { it.isNotEmpty() } ?: "your baby"

    Surface(
        modifier = rootModifier,
        color = MaterialTheme.colorScheme.surface,
    ) {
        val columnSizeModifier = if (showHeader || showActions) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth()
        }
        val insetModifier = if (showHeader || showActions) {
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        } else {
            Modifier
        }

        Column(
            modifier = Modifier
                .then(columnSizeModifier)
                .then(insetModifier),
        ) {
            if (showHeader) {
                OnboardingHeroStrip(
                    title = "Allergies",
                    stepLabel = "Step 3 of 3",
                    progress = 1f,
                    accentColor = MaterialTheme.colorScheme.primary,
                    accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    accentContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onBack = onBack,
                )
            }
            val contentSizeModifier = if (showActions) {
                Modifier.weight(1f)
            } else {
                Modifier.fillMaxWidth()
            }

            Column(
                modifier = Modifier
                    .then(contentSizeModifier)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (showHeader || showActions) 24.dp else 0.dp),
            ) {
                Spacer(modifier = Modifier.height(if (showHeader) 12.dp else 0.dp))
                Text(
                    text = "Any known allergies?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose only what you already know about $babyLabel. You can leave this empty and update it later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(22.dp))
                NoKnownAllergiesOption(
                    selected = selectedAllergies.isEmpty(),
                    onClick = onAllergiesCleared,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Known allergies",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(10.dp))
                AllergyChipGrid(
                    selectedAllergies = selectedAllergies,
                    onAllergyToggled = onAllergyToggled,
                )
                AnimatedVisibility(
                    visible = AllergyType.OTHER in selectedAllergies,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customNote,
                            onValueChange = onCustomNoteChanged,
                            label = { Text("Describe the allergy") },
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            supportingText = { Text("${customNote.length}/100") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                AllergySelectionSummary(selectedAllergies = selectedAllergies)
            }
            if (showActions) {
                val finishButtonModifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .semantics {
                        if (isSaving) {
                            contentDescription = "Saving setup"
                            stateDescription = "Saving"
                            liveRegion = LiveRegionMode.Polite
                        }
                    }

                Button(
                    onClick = onFinish,
                    enabled = !isSaving,
                    modifier = finishButtonModifier,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    if (isSaving) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .semantics {
                                        contentDescription = "Saving setup"
                                    },
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Text("Saving")
                        }
                    } else {
                        Text("Finish setup")
                    }
                }
            }
        }
    }
}

@Composable
private fun NoKnownAllergiesOption(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionMark(selected = selected)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "No known allergies",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Use this if nothing has come up yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SelectionMark(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Surface(
        modifier = modifier.size(32.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllergyChipGrid(
    selectedAllergies: Set<AllergyType>,
    onAllergyToggled: (AllergyType) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AllergyType.entries.forEach { allergy ->
            val selected = allergy in selectedAllergies
            FilterChip(
                selected = selected,
                onClick = { onAllergyToggled(allergy) },
                label = { Text(allergy.label) },
                modifier = Modifier.semantics {
                    stateDescription = if (selected) "Selected" else "Not selected"
                },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                        )
                    }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun AllergySelectionSummary(
    selectedAllergies: Set<AllergyType>,
    modifier: Modifier = Modifier,
) {
    val summary = if (selectedAllergies.isEmpty()) {
        "Ready to save with no known allergies."
    } else {
        selectedAllergies.joinToString(prefix = "Selected: ") { it.label }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
