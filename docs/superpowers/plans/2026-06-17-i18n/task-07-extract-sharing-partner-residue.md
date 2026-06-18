# Task 7 — Extract: Sharing/Partner + widget/tile residue

> Part of the [i18n implementation plan](../2026-06-17-i18n-overview.md). Depends on Task 1. Follow the **extraction recipe** in the overview. See Global Constraints there.

**Goal:** Replace remaining hardcoded user-facing strings in the Sharing/Partner surfaces and sweep up any residual hardcoded literals in widgets, tiles, and notification code so the codebase reaches zero `HardcodedText`.

**Why last among extraction issues:** it is the catch-all that drives the lint
count to zero, so it runs after the feature areas are clean.

## Files

- Modify: `app/src/main/java/com/babytracker/ui/sharing/ConnectPartnerScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/sharing/ManageSharingScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/sharing/ConnectPartnerViewModel.kt` / `ManageSharingViewModel.kt` (only if they emit copy)
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerFeedHistoryScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerSleepPredictionCard.kt`
- Modify: `app/src/main/java/com/babytracker/ui/partner/*ViewModel.kt` (only if they emit copy)
- Modify: any residual files flagged by the final sweep (Step 1) — e.g. widget
  composables under `widget/`, tile services under `tile/`, notification managers
  under `notification/` that still hold literals.
- Modify: `app/src/main/res/values/strings.xml`

> Many `partner_*`, `widget_*`, `tile_*`, and `notif_*` keys already exist.
> Reuse them; only add keys for uncovered literals.

## Implementation

### Step 1: Sweep the *whole* app for residual literals

```bash
./gradlew lintDebug
rg -n 'Text\(\s*"|contentDescription = "' app/src/main/java/com/babytracker/ui/sharing app/src/main/java/com/babytracker/ui/partner
# Catch-all: anything still flagged anywhere
rg -n 'HardcodedText' app/lint-baseline.xml
```

The remaining baseline entries are this task's worklist.

- [ ] **Step 1 done when** you have the complete list of every still-hardcoded literal in the app.

### Step 2: Add base keys

Add `<!-- Sharing / Partner -->` and any residual sections. Reuse existing
`partner_*` keys (`partner_log_bottle`, `partner_feeding_history`,
`partner_feeding_history_error_title`). Partner stash counts already have a
plural (`notif_title_partner_stash`); mirror that style for any new counts.

- [ ] **Step 2 done when** every literal from Step 1 has a key.

### Step 3: Replace literals

```kotlin
// before
Text("Connect partner")
// after
Text(stringResource(R.string.sharing_connect_partner))
```

Imports as needed:

```kotlin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.babytracker.R
```

For non-Compose surfaces (notification managers, receivers) use
`context.getString(R.string.key)` / `resources.getQuantityString(...)`.

- [ ] **Step 3 done when** no file in the app holds a user-facing literal.

### Step 4: Delete-to-zero check on the baseline

After replacing, regenerate the baseline:

```bash
rm app/lint-baseline.xml
./gradlew lintDebug
```

Expected: the regenerated `lint-baseline.xml` has **zero** `HardcodedText`
entries. If it has any, they are remaining literals — extract them. (Keep the
empty/near-empty baseline for now; Task 9 removes the file entirely when lint is
promoted to fatal.)

- [ ] **Step 4 done when** regenerated baseline has zero `HardcodedText`.

## Verify

```
./gradlew ktlintFormat
./gradlew assembleDebug lintDebug
./gradlew test --tests "com.babytracker.ui.sharing.*" --tests "com.babytracker.ui.partner.*"
```

Expected: green; lint reports no `HardcodedText` anywhere.

## Commit

```
feat(i18n): extract sharing/partner strings and clear residual literals

Move Sharing/Partner user-facing literals into strings.xml and sweep up any
remaining hardcoded text in widgets, tiles, and notification code. HardcodedText
count reaches zero.
```
