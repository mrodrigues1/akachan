# Task 2 — Extract: Home + Breastfeeding + shared components

> Part of the [i18n implementation plan](../2026-06-17-i18n-overview.md). Depends on Task 1. Follow the **extraction recipe** in the overview. See Global Constraints there.

**Goal:** Replace every hardcoded user-facing string in the Home and Breastfeeding surfaces (and the shared components they use) with string resources, including the `PredictionCopy` ViewModel copy that is currently built by concatenation.

**Why grouped:** Home renders breastfeeding state and shares the timer/side/history components, so these files share vocabulary and should extract together to maximise key reuse.

## Files

- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeTileContent.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt` (only if it emits user-facing copy)
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingHistoryScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/EditBreastfeedingSessionSheet.kt`
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/FeedSettingsScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/FeedSettingsViewModel.kt` (only if it emits copy)
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/PredictionCopy.kt`
- Modify: `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt`
- Modify: `app/src/main/java/com/babytracker/ui/component/SideSelector.kt`
- Modify: `app/src/main/java/com/babytracker/ui/component/TimerDisplay.kt`
- Modify: `app/src/main/java/com/babytracker/ui/component/CueQuickTapRow.kt`
- Modify: `app/src/main/java/com/babytracker/ui/common/DateTimeFieldRow.kt`
- Modify: `app/src/main/res/values/strings.xml` (append new keys)

## Implementation

### Step 1: Enumerate literals in scope

Run, per file, to find every literal that needs a key:

```bash
rg -n 'Text\(\s*"|contentDescription = "|label = \{ Text\("|placeholder|title = \{ Text\("' app/src/main/java/com/babytracker/ui/home app/src/main/java/com/babytracker/ui/breastfeeding app/src/main/java/com/babytracker/ui/component app/src/main/java/com/babytracker/ui/common
```

Also find concatenations:

```bash
rg -n '"[^"]*\$|"[^"]+" \+' app/src/main/java/com/babytracker/ui/home app/src/main/java/com/babytracker/ui/breastfeeding
```

- [ ] **Step 1 done when** you have the literal list for these files.

### Step 2: Add base keys to `strings.xml`

Append under a new comment section. Representative entries (add the rest from your Step 1 list):

```xml
    <!-- Home -->
    <string name="home_reset_layout">Reset layout</string>

    <!-- Breastfeeding prediction copy -->
    <plurals name="breastfeeding_predict_hungry_ago">
        <item quantity="one">Likely hungry now &#183; ~%dm ago</item>
        <item quantity="other">Likely hungry now &#183; ~%dm ago</item>
    </plurals>
    <string name="breastfeeding_predict_hungry_around">Likely hungry around %1$s</string>
```

> Reuse existing keys where they fit (`cancel`, `delete`, `edit`, `back`,
> `more_options`, `loading`). Do not add duplicates.

- [ ] **Step 2 done when** every literal from Step 1 has a base key (new or reused).

### Step 3: Replace literals in composables

Pattern for a plain literal (`HomeScreen.kt:154`):

```kotlin
// before
text = { Text("Reset layout") },
// after
text = { Text(stringResource(R.string.home_reset_layout)) },
```

Pattern for `contentDescription`:

```kotlin
// before
Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
// after
Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
```

Add imports where missing:

```kotlin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.babytracker.R
```

- [ ] **Step 3 done when** no composable in scope holds a user-facing literal.

### Step 4: Refactor `PredictionCopy.kt` (ViewModel copy → resources)

`PredictionCopy.kt` currently builds strings by interpolation, e.g.
`primary = "Likely hungry now · ~${agoMinutes}m ago"` and
`primary = "Likely hungry around $timeLabel"`. Keep the ViewModel
locale-agnostic: expose resource IDs + args in the copy model instead of
resolved text, and resolve in the composable.

```kotlin
// PredictionCopy result model — carry IDs, not resolved strings
data class PredictionCopyRes(
    @StringRes val primaryRes: Int,
    val primaryArg: String? = null,    // e.g. formatted time label
    val primaryCount: Int? = null,     // e.g. agoMinutes for plurals
)
```

In the composable that renders it:

```kotlin
val text = when {
    copy.primaryCount != null ->
        pluralStringResource(R.plurals.breastfeeding_predict_hungry_ago, copy.primaryCount, copy.primaryCount)
    copy.primaryArg != null ->
        stringResource(copy.primaryRes, copy.primaryArg)
    else -> stringResource(copy.primaryRes)
}
```

Adjust the exact field set to match the real `PredictionCopy` branches found in
Step 1 (there are three: "now · ~Xm ago", "around <time>" in two contexts).

- [ ] **Step 4 done when** `PredictionCopy.kt` returns no hardcoded English; the composable resolves all copy.

### Step 5: Update the lint baseline (shrink)

```bash
./gradlew lintDebug -Dlint.baselines.continue=true
```

Then regenerate so removed warnings drop out: delete the `HardcodedText` entries
for the touched files from `app/lint-baseline.xml` (or regenerate the whole file
and confirm the count dropped). The baseline must only shrink.

- [ ] **Step 5 done when** `lint-baseline.xml` no longer lists the files in this task.

## Verify

```
./gradlew ktlintFormat
./gradlew assembleDebug lintDebug
./gradlew test --tests "com.babytracker.ui.breastfeeding.*" --tests "com.babytracker.ui.home.*"
```

Expected: build + lint green; no new `HardcodedText` in touched files; existing
Home/Breastfeeding tests still pass. If a test asserted on a literal string,
update it to assert the resource value via `context.getString(...)`.

## Commit

```
feat(i18n): extract home and breastfeeding strings to resources

Move all user-facing Home, Breastfeeding, and shared-component literals into
strings.xml, including PredictionCopy which now exposes string resource IDs in
state and resolves in the composable. Shrinks the lint baseline accordingly.
```
