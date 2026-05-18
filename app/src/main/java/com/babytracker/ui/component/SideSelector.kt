package com.babytracker.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.displayName

@Composable
fun SideSelector(
    selectedSide: BreastSide?,
    onSideSelected: (BreastSide) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BreastSide.entries.forEach { side ->
            val isSelected = selectedSide == side
            val label = side.displayName()
            val arrowIcon = if (side == BreastSide.LEFT) Icons.Filled.KeyboardArrowLeft else Icons.Filled.KeyboardArrowRight

            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                animationSpec = tween(durationMillis = 220),
                label = "container_${side.name}",
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                animationSpec = tween(durationMillis = 220),
                label = "border_${side.name}",
            )
            val elevation by animateDpAsState(
                targetValue = if (isSelected) 6.dp else 0.dp,
                animationSpec = tween(durationMillis = 220),
                label = "elevation_${side.name}",
            )
            val iconTint by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                },
                animationSpec = tween(durationMillis = 220),
                label = "icon_tint_${side.name}",
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                animationSpec = tween(durationMillis = 220),
                label = "text_color_${side.name}",
            )

            Card(
                onClick = { onSideSelected(side) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 88.dp)
                    .semantics {
                        role = Role.RadioButton
                        selected = isSelected
                        contentDescription = "$label${if (isSelected) ", selected" else ""}"
                    },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                border = BorderStroke(width = 2.dp, color = borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = arrowIcon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = iconTint,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                    )
                }
            }
        }
    }
}
