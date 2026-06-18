# Task 4 — Extract: Onboarding + Growth + Milestones

> Part of the [i18n implementation plan](../2026-06-17-i18n-overview.md). Depends on Task 1. Follow the **extraction recipe** in the overview. See Global Constraints there.

**Goal:** Replace every hardcoded user-facing string across the Onboarding flow, Growth tracking, and Milestones surfaces with string resources.

## Files

- Modify: `app/src/main/java/com/babytracker/ui/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/onboarding/components/WelcomeStepContent.kt`
- Modify: `app/src/main/java/com/babytracker/ui/onboarding/components/BabyInfoStepContent.kt`
- Modify: `app/src/main/java/com/babytracker/ui/onboarding/components/AllergiesStepContent.kt`
- Modify: `app/src/main/java/com/babytracker/ui/onboarding/components/OnboardingHeroStrip.kt`
- Modify: `app/src/main/java/com/babytracker/ui/onboarding/OnboardingViewModel.kt` (only if it emits copy)
- Modify: `app/src/main/java/com/babytracker/ui/growth/GrowthScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/growth/AddMeasurementSheet.kt`
- Modify: `app/src/main/java/com/babytracker/ui/growth/GrowthViewModel.kt` (only if it emits copy)
- Modify: `app/src/main/java/com/babytracker/ui/milestone/MilestonesScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/milestone/MilestoneDetailScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/milestone/MilestoneEditorSheet.kt`
- Modify: `app/src/main/java/com/babytracker/ui/milestone/MilestonesViewModel.kt` / `MilestoneDetailViewModel.kt` (only if they emit copy)
- Modify: `app/src/main/res/values/strings.xml`

## Implementation

### Step 1: Enumerate literals

```bash
rg -n 'Text\(\s*"|contentDescription = "|label = \{ Text\("|placeholder' app/src/main/java/com/babytracker/ui/onboarding app/src/main/java/com/babytracker/ui/growth app/src/main/java/com/babytracker/ui/milestone
rg -n '"[^"]*\$|"[^"]+" \+' app/src/main/java/com/babytracker/ui/onboarding app/src/main/java/com/babytracker/ui/growth app/src/main/java/com/babytracker/ui/milestone
```

- [ ] **Step 1 done when** you have the full literal list.

### Step 2: Add base keys

Representative entries grounded in real literals
(`BabyInfoStepContent.kt`, `AllergiesStepContent.kt`, `WelcomeStepContent.kt`):

```xml
    <!-- Onboarding -->
    <string name="onboarding_setup_profile">Set up baby profile</string>
    <string name="onboarding_baby_name_label">Baby\'s name</string>
    <string name="onboarding_dob_label">Date of birth</string>
    <string name="onboarding_continue">Continue</string>
    <string name="onboarding_use_date">Use date</string>
    <string name="onboarding_other_allergy_label">Describe other allergy</string>
    <string name="onboarding_saving">Saving</string>
    <string name="onboarding_finish_setup">Finish setup</string>
```

Reuse `cancel` for the existing "Cancel" literal. Add Growth and Milestones
sections for their literals from Step 1.

- [ ] **Step 2 done when** every Step 1 literal has a key.

### Step 3: Replace literals

```kotlin
// before
label = { Text("Baby's name") },
Text("Continue")
// after
label = { Text(stringResource(R.string.onboarding_baby_name_label)) },
Text(stringResource(R.string.onboarding_continue))
```

Imports as needed:

```kotlin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.babytracker.R
```

Growth values ("12.3 kg", "X cm", percentiles) and milestone counts use format
args / plurals — never `+`.

- [ ] **Step 3 done when** no composable in scope holds a user-facing literal.

### Step 4: Shrink the lint baseline

Remove the `HardcodedText` entries for the touched files from
`app/lint-baseline.xml`.

- [ ] **Step 4 done when** the baseline no longer lists these files.

## Verify

```
./gradlew ktlintFormat
./gradlew assembleDebug lintDebug
./gradlew test --tests "com.babytracker.ui.onboarding.*" --tests "com.babytracker.ui.growth.*" --tests "com.babytracker.ui.milestone.*"
```

Expected: green; no new `HardcodedText`. Update literal-asserting tests to read
the resource via `context.getString(...)`.

## Commit

```
feat(i18n): extract onboarding, growth, and milestones strings

Move Onboarding, Growth, and Milestones user-facing literals into strings.xml.
Shrinks the lint baseline.
```
