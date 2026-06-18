# Task 6 — Extract: Feeding + Bottle + Pumping

> Part of the [i18n implementation plan](../2026-06-17-i18n-overview.md). Depends on Task 1. Follow the **extraction recipe** in the overview. See Global Constraints there.

**Goal:** Replace every hardcoded user-facing string across the bottle-feed, unified feeding-history, and pumping surfaces with string resources.

## Files

- Modify: `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedSheet.kt`
- Modify: `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedInput.kt`
- Modify: `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedViewModel.kt` (only if it emits copy)
- Modify: `app/src/main/java/com/babytracker/ui/feeding/UnifiedFeedingHistoryScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/feeding/FeedingHistoryViewModel.kt` (only if it emits copy)
- Modify: `app/src/main/java/com/babytracker/ui/pumping/PumpingScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/pumping/PumpingHistoryScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/pumping/EditPumpingSessionSheet.kt`
- Modify: `app/src/main/java/com/babytracker/ui/pumping/AddBagPromptSheet.kt`
- Modify: `app/src/main/java/com/babytracker/ui/pumping/PumpingViewModel.kt` / `PumpingHistoryViewModel.kt` (only if they emit copy)
- Modify: `app/src/main/res/values/strings.xml`

> Many `bottle_feed_*` and `feeding_*` keys already exist (lines 154–181 of
> `strings.xml`). Reuse them; only add keys for uncovered literals (notably the
> Pumping surfaces, which have none yet).

## Implementation

### Step 1: Enumerate residual literals

```bash
rg -n 'Text\(\s*"|contentDescription = "|label = \{ Text\("|placeholder' app/src/main/java/com/babytracker/ui/bottlefeed app/src/main/java/com/babytracker/ui/feeding app/src/main/java/com/babytracker/ui/pumping
rg -n '"[^"]*\$|"[^"]+" \+' app/src/main/java/com/babytracker/ui/bottlefeed app/src/main/java/com/babytracker/ui/feeding app/src/main/java/com/babytracker/ui/pumping
```

Cross-check each hit against existing `bottle_feed_*` / `feeding_*` keys.

- [ ] **Step 1 done when** you have the list of *uncovered* literals.

### Step 2: Add base keys

Add a `<!-- Pumping -->` section (and any residual feeding keys). Use
`pumping_<element>` naming. Pumping session counts, durations, and mL volumes use
`<plurals>` / format args (mirror `feeding_history_day_totals` which already uses
`%1$s &#183; %2$d feeds`).

```xml
    <!-- Pumping -->
    <string name="pumping_title">Pumping</string>
    <string name="pumping_start">Start pumping</string>
    <string name="pumping_history_title">Pumping history</string>
    <!-- add the rest from Step 1 -->
```

- [ ] **Step 2 done when** every uncovered literal has a key.

### Step 3: Replace literals

```kotlin
// before
Text("Start pumping")
// after
Text(stringResource(R.string.pumping_start))
```

Imports as needed:

```kotlin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.babytracker.R
```

- [ ] **Step 3 done when** no composable in scope holds a user-facing literal.

### Step 4: Shrink the lint baseline

Remove the `HardcodedText` entries for the touched files from
`app/lint-baseline.xml`.

- [ ] **Step 4 done when** the baseline no longer lists these files.

## Verify

```
./gradlew ktlintFormat
./gradlew assembleDebug lintDebug
./gradlew test --tests "com.babytracker.ui.bottlefeed.*" --tests "com.babytracker.ui.feeding.*" --tests "com.babytracker.ui.pumping.*"
```

Expected: green; no new `HardcodedText`.

## Commit

```
feat(i18n): extract bottle-feed, feeding, and pumping strings

Move bottle-feed, unified feeding-history, and pumping user-facing literals into
strings.xml. Shrinks the lint baseline.
```
