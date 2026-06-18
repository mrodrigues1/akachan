# Task 5 — Extract: Settings + Inventory

> Part of the [i18n implementation plan](../2026-06-17-i18n-overview.md). Depends on Task 1. Follow the **extraction recipe** in the overview. See Global Constraints there.

**Goal:** Finish extracting the Settings surfaces (the only screen already partly localized) and the Inventory surfaces.

**Why grouped:** `SettingsScreen.kt` already uses `stringResource` for ~20 keys; this task closes the remaining gaps in Settings and brings Inventory (which has its own settings screen) up to the same standard.

## Files

- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt` (residual literals only)
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt` (only if it emits copy)
- Modify: `app/src/main/java/com/babytracker/ui/settings/DataSection.kt`
- Modify: `app/src/main/java/com/babytracker/ui/settings/DataExportViewModel.kt` (only if it emits copy)
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsReminderComponents.kt`
- Modify: `app/src/main/java/com/babytracker/ui/settings/WarningSurface.kt`
- Modify: `app/src/main/java/com/babytracker/ui/inventory/InventoryScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/inventory/InventorySettingsScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/inventory/AddBagSheet.kt`
- Modify: `app/src/main/java/com/babytracker/ui/inventory/InventoryViewModel.kt` / `InventorySettingsViewModel.kt` (only if they emit copy)
- Modify: `app/src/main/res/values/strings.xml`

> Many `settings_*` keys already exist (lines 69–118 of `strings.xml`). Reuse
> them; only add keys for literals not yet covered.

## Implementation

### Step 1: Enumerate residual literals

```bash
rg -n 'Text\(\s*"|contentDescription = "|label = \{ Text\("|placeholder' app/src/main/java/com/babytracker/ui/settings app/src/main/java/com/babytracker/ui/inventory
rg -n '"[^"]*\$|"[^"]+" \+' app/src/main/java/com/babytracker/ui/settings app/src/main/java/com/babytracker/ui/inventory
```

Cross-check each hit against existing `settings_*` keys before adding a new key.

- [ ] **Step 1 done when** you have the list of *uncovered* literals.

### Step 2: Add base keys

Add a `<!-- Settings (residual) -->` and `<!-- Inventory -->` section for the
uncovered literals, following `settings_<element>` / `inventory_<element>`
naming. Bag counts and mL totals use `<plurals>` / format args (mirror the
existing `notif_body_stash_expiration` plural and `bottle_feed_volume_label`).

- [ ] **Step 2 done when** every uncovered literal has a key.

### Step 3: Replace literals

```kotlin
// before
Text("Export data")
// after
Text(stringResource(R.string.settings_export_data))
```

Imports as needed:

```kotlin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.babytracker.R
```

`WarningSurface` content descriptions and any export progress/result copy in
`DataSection` / `DataExportViewModel` route through resources (resolve in the
composable; pass `@StringRes Int` in state if produced by the ViewModel).

- [ ] **Step 3 done when** no Settings/Inventory composable holds a user-facing literal.

### Step 4: Shrink the lint baseline

Remove the `HardcodedText` entries for the touched files from
`app/lint-baseline.xml`.

- [ ] **Step 4 done when** the baseline no longer lists these files.

## Verify

```
./gradlew ktlintFormat
./gradlew assembleDebug lintDebug
./gradlew test --tests "com.babytracker.ui.settings.*" --tests "com.babytracker.ui.inventory.*"
```

Expected: green; no new `HardcodedText`.

## Commit

```
feat(i18n): extract settings and inventory strings to resources

Close the remaining hardcoded literals in Settings and bring Inventory to full
resource coverage. Shrinks the lint baseline.
```
