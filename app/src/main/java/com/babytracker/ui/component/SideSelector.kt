package com.babytracker.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.BreastSide

@Composable
fun SideSelector(
    selectedSide: BreastSide?,
    onSideSelected: (BreastSide) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        FilterChip(
            selected = selectedSide == BreastSide.LEFT,
            onClick = { onSideSelected(BreastSide.LEFT) },
            label = { Text("Left") }
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilterChip(
            selected = selectedSide == BreastSide.RIGHT,
            onClick = { onSideSelected(BreastSide.RIGHT) },
            label = { Text("Right") }
        )
    }
}
