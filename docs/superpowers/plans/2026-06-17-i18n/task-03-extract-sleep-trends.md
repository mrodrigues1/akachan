# Task 3 — Extract: Sleep + Trends

> Part of the [i18n implementation plan](../2026-06-17-i18n-overview.md). Depends on Task 1. Follow the **extraction recipe** in the overview. See Global Constraints there.

**Goal:** Replace every hardcoded user-facing string in the Sleep and Trends surfaces with string resources, converting count/duration copy to plurals/format args.

## Files

- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepHistoryScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepScheduleScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepSettingsScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepPredictionCard.kt`
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepRecommendationSection.kt`
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt` / `SleepSettingsViewModel.kt` (only if they emit copy — prefer resolving in composable)
- Modify: `app/src/main/java/com/babytracker/ui/trends/TrendsScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/trends/RhythmStrip.kt`
- Modify: `app/src/main/java/com/babytracker/ui/trends/TrendsViewModel.kt` (only if it emits copy)
- Modify: `app/src/main/res/values/strings.xml`

> `sleep_settings_title` already exists — reuse it. Do not duplicate.

## Implementation

### Step 1: Enumerate literals

```bash
rg -n 'Text\(\s*"|contentDescription = "|label = \{ Text\("|title = \{ Text\("' app/src/main/java/com/babytracker/ui/sleep app/src/main/java/com/babytracker/ui/trends
rg -n '"[^"]*\$|"[^"]+" \+' app/src/main/java/com/babytracker/ui/sleep app/src/main/java/com/babytracker/ui/trends
```

- [ ] **Step 1 done when** you have the full literal list.

### Step 2: Add base keys

Append a Sleep and a Trends section. Representative entries grounded in the real
literals (`SleepTrackingScreen.kt`): top-bar title "Sleep", actions
"Back"/"Sleep history"/"Sleep schedule"/"Sleep settings", "Log Past Sleep",
"Stop Session", "More options", delete dialog "Delete entry?"/"Delete entry".

```xml
    <!-- Sleep tracking -->
    <string name="sleep_title">Sleep</string>
    <string name="sleep_cd_history">Sleep history</string>
    <string name="sleep_cd_schedule">Sleep schedule</string>
    <string name="sleep_cd_settings">Sleep settings</string>
    <string name="sleep_log_past">Log Past Sleep</string>
    <string name="sleep_stop_session">Stop Session</string>
    <string name="sleep_delete_entry_title">Delete entry?</string>
    <string name="sleep_cd_delete_entry">Delete entry</string>
```

Reuse `back`, `delete`, `cancel`, `more_options` from the existing shared keys.

- [ ] **Step 2 done when** every Step 1 literal has a key.

### Step 3: Replace literals

Plain literal and `contentDescription`:

```kotlin
// before
title = { Text("Sleep") },
Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
Icon(Icons.Outlined.History, contentDescription = "Sleep history")
// after
title = { Text(stringResource(R.string.sleep_title)) },
Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.sleep_cd_history))
```

Imports as needed:

```kotlin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.babytracker.R
```

For any "X min" / "X naps" duration or count copy in `SleepPredictionCard` /
`SleepRecommendationSection`, use `pluralStringResource`/format args, not `+`.

- [ ] **Step 3 done when** no Sleep/Trends composable holds a user-facing literal.

### Step 4: Shrink the lint baseline

Remove the `HardcodedText` entries for the touched files from
`app/lint-baseline.xml` (or regenerate and confirm the count dropped).

- [ ] **Step 4 done when** the baseline no longer lists these files.

## Verify

```
./gradlew ktlintFormat
./gradlew assembleDebug lintDebug
./gradlew test --tests "com.babytracker.ui.sleep.*" --tests "com.babytracker.ui.trends.*"
```

Expected: green; no new `HardcodedText`. Update any test asserting on a literal
to read it via `context.getString(...)`.

## Commit

```
feat(i18n): extract sleep and trends strings to resources

Move Sleep and Trends user-facing literals into strings.xml, converting count
and duration copy to plurals/format args. Shrinks the lint baseline.
```
