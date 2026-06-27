package com.babytracker.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Full-width primary save button used across bottom sheets.
 *
 * While [loading], the button is disabled and shows an inline spinner. When
 * [keepLabelWhileLoading] is true the spinner sits before the label (the label
 * stays visible); when false the spinner replaces the label entirely.
 */
@Composable
fun SheetSaveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    keepLabelWhileLoading: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            if (keepLabelWhileLoading) {
                Spacer(Modifier.width(8.dp))
            }
        }
        if (!loading || keepLabelWhileLoading) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
