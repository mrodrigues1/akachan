# Glance Dependencies + Widget ColorProviders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** [AKA-48](https://linear.app/akachan/issue/AKA-48/add-glance-workmanager-deps-and-widget-colorproviders)
**Spec:** `docs/superpowers/specs/2026-05-24-home-screen-widget-design.md`
**Suggested branch:** `feat/glance-deps-and-widget-colors`
**Depends on:** none
**Blocks:** Plan 4 (skeleton), Plan 5 (content composables)

**Goal:** Add Jetpack Glance + WorkManager dependencies to the build and ship reusable Glance `ColorProviders` that mirror the Baby palette for both light and dark schemes.

**Architecture:** Library additions go through `gradle/libs.versions.toml` and `app/build.gradle.kts` per project convention. A new `widget/theme/WidgetColors.kt` file owns Glance-specific theme objects — `MaterialTheme.colorScheme` is not available inside a Glance composable, so the widget needs its own `ColorProviders` (and a small `androidx.glance.color.ColorProviders` factory). The widget reads from the same raw palette `val`s already in `ui/theme/Color.kt` (Pink* / Blue* / Surface* / Outline* / OnSurface*) to avoid duplicating hex values.

**Tech Stack:** AndroidX Glance AppWidget, Glance Material3, AndroidX WorkManager. Versions pinned to the latest AndroidX-compatible releases at implementation time (`glance` 1.1.x, `work` 2.10.x as of 2026-05).

---

## Files

- Create: `app/src/main/java/com/babytracker/widget/theme/WidgetColors.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:84-168`
- Modify: `CLAUDE.md:7-20` (tech-stack table — add Glance + WorkManager rows)
- Modify: `AGENTS.md:11-22` (mirror — must match `CLAUDE.md` identically)

---

### Task 1: Pin Glance + WorkManager versions

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add version entries**

In the `[versions]` block, add:

```toml
glance = "1.1.1"
work = "2.10.0"
```

- [ ] **Step 2: Add library entries**

In the `[libraries]` block (after the `# DataStore` group is fine):

```toml
# Glance (widgets)
glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }

# WorkManager
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
```

- [ ] **Step 3: Commit nothing yet** — continue to Task 2.

---

### Task 2: Wire dependencies into `:app`

**Files:**
- Modify: `app/build.gradle.kts:84-168`

- [ ] **Step 1: Add to `dependencies { ... }`**

After the `// DataStore` group and before `// Coroutines`, add:

```kotlin
    // Glance (widgets)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // WorkManager
    implementation(libs.work.runtime.ktx)
```

- [ ] **Step 2: Verify Gradle sync succeeds**

Run: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath -q | grep -E "glance|work-runtime" | head -20`
Expected: lines containing `androidx.glance:glance-appwidget:1.1.1`, `androidx.glance:glance-material3:1.1.1`, and `androidx.work:work-runtime-ktx:2.10.0`.

If the resolved version is newer than the spec value (transitive bump), update `libs.versions.toml` to match and re-run.

---

### Task 3: Create `WidgetColors.kt`

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/theme/WidgetColors.kt`

The Glance widget cannot consume `MaterialTheme.colorScheme`, so we define a dedicated `androidx.glance.material3.ColorProviders` (light + dark) and a single `GlanceTheme` wrapper. Reuse raw palette values from `com.babytracker.ui.theme` — never re-declare hex.

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.widget.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders as material3ColorProviders
import com.babytracker.ui.theme.ErrorContainerDark
import com.babytracker.ui.theme.ErrorContainerLight
import com.babytracker.ui.theme.ErrorDark
import com.babytracker.ui.theme.ErrorLight
import com.babytracker.ui.theme.OnErrorContainerDark
import com.babytracker.ui.theme.OnErrorContainerLight
import com.babytracker.ui.theme.OnPrimaryContainerDarkPink
import com.babytracker.ui.theme.OnPrimaryWhite
import com.babytracker.ui.theme.OnSecondaryContainerDarkBlue
import com.babytracker.ui.theme.OnSecondaryWhite
import com.babytracker.ui.theme.OnSurfaceDark
import com.babytracker.ui.theme.OnSurfaceDarkTheme
import com.babytracker.ui.theme.OnSurfaceVariantGrey
import com.babytracker.ui.theme.OnSurfaceVariantGreyDark
import com.babytracker.ui.theme.OnTertiaryContainerDarkGreen
import com.babytracker.ui.theme.OnTertiaryWhite
import com.babytracker.ui.theme.OutlineDark
import com.babytracker.ui.theme.OutlineLight
import com.babytracker.ui.theme.OutlineVariantDark
import com.babytracker.ui.theme.OutlineVariantLight
import com.babytracker.ui.theme.PrimaryContainerPink
import com.babytracker.ui.theme.PrimaryContainerPinkDark
import com.babytracker.ui.theme.PrimaryPink
import com.babytracker.ui.theme.PrimaryPinkDark
import com.babytracker.ui.theme.SecondaryBlue
import com.babytracker.ui.theme.SecondaryBlueDark
import com.babytracker.ui.theme.SecondaryContainerBlue
import com.babytracker.ui.theme.SecondaryContainerBlueDark
import com.babytracker.ui.theme.SurfaceDark
import com.babytracker.ui.theme.SurfaceVariantDark
import com.babytracker.ui.theme.SurfaceVariantLight
import com.babytracker.ui.theme.SurfaceYellow
import com.babytracker.ui.theme.TertiaryContainerGreen
import com.babytracker.ui.theme.TertiaryContainerGreenDark
import com.babytracker.ui.theme.TertiaryGreen
import com.babytracker.ui.theme.TertiaryGreenDark

private val LightWidgetColorScheme = lightColorScheme(
    primary = PrimaryPink,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerPink,
    onPrimaryContainer = OnPrimaryContainerDarkPink,
    secondary = SecondaryBlue,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = SecondaryContainerBlue,
    onSecondaryContainer = OnSecondaryContainerDarkBlue,
    tertiary = TertiaryGreen,
    onTertiary = OnTertiaryWhite,
    tertiaryContainer = TertiaryContainerGreen,
    onTertiaryContainer = OnTertiaryContainerDarkGreen,
    surface = SurfaceYellow,
    background = SurfaceYellow,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantGrey,
    surfaceVariant = SurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

private val DarkWidgetColorScheme = darkColorScheme(
    primary = PrimaryPinkDark,
    onPrimary = OnPrimaryContainerDarkPink,
    primaryContainer = PrimaryContainerPinkDark,
    onPrimaryContainer = PrimaryContainerPink,
    secondary = SecondaryBlueDark,
    onSecondary = OnSecondaryContainerDarkBlue,
    secondaryContainer = SecondaryContainerBlueDark,
    onSecondaryContainer = SecondaryContainerBlue,
    tertiary = TertiaryGreenDark,
    onTertiary = OnTertiaryContainerDarkGreen,
    tertiaryContainer = TertiaryContainerGreenDark,
    onTertiaryContainer = TertiaryContainerGreen,
    surface = SurfaceDark,
    background = SurfaceDark,
    onSurface = OnSurfaceDarkTheme,
    onSurfaceVariant = OnSurfaceVariantGreyDark,
    surfaceVariant = SurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

// `androidx.glance.material3.ColorProviders` is a factory function returning
// `androidx.glance.color.ColorProviders`, so we alias the factory and keep the
// imported type for the val declaration.
val BabyWidgetColors: ColorProviders = material3ColorProviders(
    light = LightWidgetColorScheme,
    dark = DarkWidgetColorScheme,
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL. No new warnings about `BabyWidgetColors` or `androidx.glance.*` imports.

---

### Task 4: Document the new dependencies in CLAUDE.md + AGENTS.md

`AGENTS.md` is the mirror of `CLAUDE.md` per the sync note at the top of `AGENTS.md`. Both files must be updated identically — agentic worker tooling reads `AGENTS.md` as authoritative context.

**Files:**
- Modify: `CLAUDE.md:7-20`
- Modify: `AGENTS.md:11-22`

- [ ] **Step 1: Append two rows to the Tech Stack table in `CLAUDE.md`**

After the `Local DB | Room 2.8.4` row (or in topical order):

```
| Widgets | Glance 1.1.1 (appwidget + material3) |
| Background work | WorkManager 2.10.0 |
```

- [ ] **Step 2: Append the same two rows to `AGENTS.md`**

Same insertion point. Diff between the two files must remain only the sync-note line at the top.

- [ ] **Step 3: Verify mirror parity**

Run:
```bash
diff <(sed '1,/^---$/d' CLAUDE.md) <(sed '1,/^---$/d' AGENTS.md)
```
Expected: no output (identical after the front-matter / sync-note block).

- [ ] **Step 4: No test required** — docs-only change.

---

### Task 5: Run full build + format checks

- [ ] **Step 1: ktlint + detekt**

Run:
```
./gradlew ktlintFormat detekt
```
Expected: BUILD SUCCESSFUL with no detekt findings on the new file.

- [ ] **Step 2: Fast unit tests (sanity)**

Run:
```
./gradlew :app:testDebugUnitTest -PfastTests
```
Expected: BUILD SUCCESSFUL. No new failures (this plan adds no tests; it must not break existing ones).

---

### Task 6: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add gradle/libs.versions.toml \
        app/build.gradle.kts \
        app/src/main/java/com/babytracker/widget/theme/WidgetColors.kt \
        CLAUDE.md \
        AGENTS.md

git commit -m "chore(widget): add Glance + WorkManager deps and widget ColorProviders"
```

- [ ] **Step 2: Verify single-file diff cleanliness**

Run: `git show --stat HEAD`
Expected: 5 files changed; no unrelated changes.

---

## Acceptance Criteria

- `./gradlew :app:assembleDebug` succeeds.
- `./gradlew ktlintFormat detekt` is clean.
- `BabyWidgetColors` is importable from `com.babytracker.widget.theme`.
- `CLAUDE.md` **and** `AGENTS.md` tech-stack tables both list Glance + WorkManager and remain mirror-identical (see Task 4 Step 3).
- Commit on `feat/plans-home-screen-widget` adds exactly the five files above.
