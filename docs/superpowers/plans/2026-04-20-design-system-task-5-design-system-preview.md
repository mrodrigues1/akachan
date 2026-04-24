# Design System Task 5: Design System Preview Screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `DesignSystemPreviewScreen` — a scrollable catalog that renders all palette scale swatches, all semantic color tokens (live from `MaterialTheme.colorScheme`), all 13 typography specimens, and all 5 shape sizes. The screen is wired into the nav graph and reachable from a "Developer" section in Settings (debug builds only, guarded by `BuildConfig.DEBUG`).

**Architecture:**
- New file: `app/src/main/java/com/babytracker/ui/theme/DesignSystemPreviewScreen.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt` — add `Routes.DESIGN_SYSTEM_PREVIEW` and the `composable(...)` entry
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt` — add `onNavigateToDesignSystem` param and a debug-only "Developer" section row

No ViewModel required — the screen is pure presentation with no state.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3

---

### Task 1: Create `DesignSystemPreviewScreen.kt`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/theme/DesignSystemPreviewScreen.kt`

- [ ] **Step 1: Create the file with full content**

```kotlin
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
```

---

### Task 2: Add `DESIGN_SYSTEM_PREVIEW` route to `AppNavGraph.kt`

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`

- [ ] **Step 1: Add the route constant**

In the `Routes` object, add after `SETTINGS`:

```kotlin
const val DESIGN_SYSTEM_PREVIEW = "design_system/preview"
```

- [ ] **Step 2: Add the composable entry**

Add a new import at the top of the file:

```kotlin
import com.babytracker.ui.theme.DesignSystemPreviewScreen
```

Add a new `composable(...)` entry inside `NavHost`, after the `SETTINGS` entry:

```kotlin
composable(Routes.DESIGN_SYSTEM_PREVIEW) {
    DesignSystemPreviewScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

- [ ] **Step 3: Thread the lambda through the SETTINGS composable**

Replace the existing `SETTINGS` composable entry:

```kotlin
composable(Routes.SETTINGS) {
    SettingsScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

with:

```kotlin
composable(Routes.SETTINGS) {
    SettingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToDesignSystem = { navController.navigate(Routes.DESIGN_SYSTEM_PREVIEW) },
    )
}
```

---

### Task 3: Add "Developer" section to `SettingsScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add `onNavigateToDesignSystem` parameter**

Add a new import at the top of the file:

```kotlin
import com.babytracker.BuildConfig
```

`BuildConfig` is already imported. Change the function signature from:

```kotlin
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
```

to:

```kotlin
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDesignSystem: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
```

- [ ] **Step 2: Add the "Developer" section**

Locate the version footer block (after the `HorizontalDivider()` that follows `SettingsSwitchRow`). Add the developer section between the last `HorizontalDivider()` and the `Spacer(modifier = Modifier.height(32.dp))` version footer spacer:

Replace:

```kotlin
            HorizontalDivider()

            // Version footer
            Spacer(modifier = Modifier.height(32.dp))
```

with:

```kotlin
            HorizontalDivider()

            if (BuildConfig.DEBUG) {
                Text(
                    text = "Developer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                SettingsRow(
                    label = "Design System",
                    value = "Colors, typography, shapes",
                    onClick = onNavigateToDesignSystem,
                )

                HorizontalDivider()
            }

            // Version footer
            Spacer(modifier = Modifier.height(32.dp))
```

---

### Task 4: Build verification

**Files:** none

- [ ] **Step 1: Run build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL` with no compilation errors.

If compilation fails:
- `Unresolved reference: Pink100` etc. → check that `DesignSystemPreviewScreen.kt` is in `package com.babytracker.ui.theme` (same package as `Color.kt`)
- `Unresolved reference: DesignSystemPreviewScreen` in `AppNavGraph.kt` → verify the import was added
- Signature mismatch on `SettingsScreen` → verify the new param has a default value `= {}`

---

### Task 5: Run unit tests

**Files:** none

- [ ] **Step 1: Run unit tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`. No test changes are needed — this is a pure additive UI screen with no business logic.

---

### Task 6: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add app/src/main/java/com/babytracker/ui/theme/DesignSystemPreviewScreen.kt
git add app/src/main/java/com/babytracker/navigation/AppNavGraph.kt
git add app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt
git commit -m "feat(ui): add DesignSystemPreviewScreen with palette, tokens, typography, shapes

Scrollable catalog screen showing all palette scale swatches (Pink/Blue/Green
100–900), all semantic color tokens live from MaterialTheme.colorScheme,
all 13 AkachanTypography specimens, and all 5 AkachanShapes sizes.
Reachable from Settings → Developer (debug builds only)."
```

---

## Acceptance Checklist

- [ ] `DesignSystemPreviewScreen.kt` created in `com.babytracker.ui.theme`
- [ ] Palette scale rows: Pink, Blue, Green (100/200/700/900), Yellow (Soft/Surface)
- [ ] Semantic token rows: Primary, Secondary, Tertiary, Surface, Outline/Error — all reading from `MaterialTheme.colorScheme`
- [ ] All 13 `AkachanTypography` slots displayed as specimens (name + "Sample Abc 123")
- [ ] All 5 `AkachanShapes` sizes displayed as clipped boxes
- [ ] `Routes.DESIGN_SYSTEM_PREVIEW = "design_system/preview"` added to `Routes`
- [ ] `composable(Routes.DESIGN_SYSTEM_PREVIEW)` wired in `AppNavGraph`
- [ ] `SettingsScreen` accepts `onNavigateToDesignSystem` with default `= {}`
- [ ] "Developer" section with "Design System" row visible only when `BuildConfig.DEBUG`
- [ ] Toggling dark theme in Settings → navigating to Design System shows dark-scheme token values
- [ ] `./gradlew build` passes
- [ ] `./gradlew test` passes
