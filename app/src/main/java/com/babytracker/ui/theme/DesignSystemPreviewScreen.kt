package com.babytracker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignSystemPreviewScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Design System") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Palette Scale ──────────────────────────────────────────────────
            item { SectionHeader("Palette Scale") }
            item {
                SwatchRow("Pink", listOf(
                    "100" to Pink100, "200" to Pink200,
                    "700" to Pink700, "900" to Pink900,
                ))
            }
            item {
                SwatchRow("Blue", listOf(
                    "100" to Blue100, "200" to Blue200,
                    "700" to Blue700, "900" to Blue900,
                ))
            }
            item {
                SwatchRow("Green", listOf(
                    "100" to Green100, "200" to Green200,
                    "700" to Green700, "900" to Green900,
                ))
            }
            item {
                SwatchRow("Yellow", listOf(
                    "Soft" to SoftYellow, "Surface" to SurfaceYellow,
                ))
            }

            // ── Semantic Tokens — current theme ───────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader("Semantic Tokens (current theme)") }
            item {
                SwatchRow("Primary", listOf(
                    "primary" to MaterialTheme.colorScheme.primary,
                    "onPrimary" to MaterialTheme.colorScheme.onPrimary,
                    "container" to MaterialTheme.colorScheme.primaryContainer,
                    "onContainer" to MaterialTheme.colorScheme.onPrimaryContainer,
                ))
            }
            item {
                SwatchRow("Secondary", listOf(
                    "secondary" to MaterialTheme.colorScheme.secondary,
                    "onSecondary" to MaterialTheme.colorScheme.onSecondary,
                    "container" to MaterialTheme.colorScheme.secondaryContainer,
                    "onContainer" to MaterialTheme.colorScheme.onSecondaryContainer,
                ))
            }
            item {
                SwatchRow("Tertiary", listOf(
                    "tertiary" to MaterialTheme.colorScheme.tertiary,
                    "onTertiary" to MaterialTheme.colorScheme.onTertiary,
                    "container" to MaterialTheme.colorScheme.tertiaryContainer,
                    "onContainer" to MaterialTheme.colorScheme.onTertiaryContainer,
                ))
            }
            item {
                SwatchRow("Surface", listOf(
                    "surface" to MaterialTheme.colorScheme.surface,
                    "onSurface" to MaterialTheme.colorScheme.onSurface,
                    "variant" to MaterialTheme.colorScheme.surfaceVariant,
                    "onVariant" to MaterialTheme.colorScheme.onSurfaceVariant,
                ))
            }
            item {
                SwatchRow("Outline / Error", listOf(
                    "outline" to MaterialTheme.colorScheme.outline,
                    "outlineVar" to MaterialTheme.colorScheme.outlineVariant,
                    "error" to MaterialTheme.colorScheme.error,
                    "errContainer" to MaterialTheme.colorScheme.errorContainer,
                ))
            }

            // ── Typography ─────────────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader("Typography") }
            item { TypographySpecimen("displaySmall", MaterialTheme.typography.displaySmall) }
            item { TypographySpecimen("headlineLarge", MaterialTheme.typography.headlineLarge) }
            item { TypographySpecimen("headlineMedium", MaterialTheme.typography.headlineMedium) }
            item { TypographySpecimen("headlineSmall", MaterialTheme.typography.headlineSmall) }
            item { TypographySpecimen("titleLarge", MaterialTheme.typography.titleLarge) }
            item { TypographySpecimen("titleMedium", MaterialTheme.typography.titleMedium) }
            item { TypographySpecimen("titleSmall", MaterialTheme.typography.titleSmall) }
            item { TypographySpecimen("bodyLarge", MaterialTheme.typography.bodyLarge) }
            item { TypographySpecimen("bodyMedium", MaterialTheme.typography.bodyMedium) }
            item { TypographySpecimen("bodySmall", MaterialTheme.typography.bodySmall) }
            item { TypographySpecimen("labelLarge", MaterialTheme.typography.labelLarge) }
            item { TypographySpecimen("labelMedium", MaterialTheme.typography.labelMedium) }
            item { TypographySpecimen("labelSmall", MaterialTheme.typography.labelSmall) }

            // ── Shapes ─────────────────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader("Shapes") }
            item {
                ShapeRow(listOf(
                    "extraSmall\n4dp" to MaterialTheme.shapes.extraSmall,
                    "small\n8dp" to MaterialTheme.shapes.small,
                    "medium\n16dp" to MaterialTheme.shapes.medium,
                    "large\n24dp" to MaterialTheme.shapes.large,
                    "extraLarge\n50dp" to MaterialTheme.shapes.extraLarge,
                ))
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwatchRow(rowLabel: String, swatches: List<Pair<String, Color>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = rowLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(44.dp)
                .padding(top = 14.dp),
        )
        swatches.forEach { (label, color) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(color)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TypographySpecimen(name: String, style: TextStyle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = "Sample Abc 123",
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ShapeRow(shapes: List<Pair<String, Shape>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shapes.forEach { (label, shape) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
