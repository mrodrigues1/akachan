package com.babytracker.ui.onboarding.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.AllergyType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergiesStepContent(
    babyName: String,
    selectedAllergies: Set<AllergyType>,
    customNote: String,
    onAllergyToggled: (AllergyType) -> Unit,
    onCustomNoteChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Does $babyName have any known allergies?",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
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
}
