# Onboarding Redesign Spec

**Date:** 2026-04-09  
**Status:** Approved  
**Scope:** `ui/onboarding/` — screens, components, ViewModel

---

## Overview

Redesign the onboarding flow to feel warmer and more consistent with the rest of the app. The new design introduces a dedicated welcome screen, merges the name and date-of-birth steps into a single "Baby Info" step, and adopts a Hero Banner + Floating Card layout throughout.

---

## Screen Flow

```
WELCOME  →  BABY_INFO  →  ALLERGIES
```

Three screens map directly to a new `OnboardingStep` enum with values `WELCOME`, `BABY_INFO`, `ALLERGIES`. The current `NAME` and `BIRTH_DATE` steps are removed.

---

## Layout System

All three screens share the same structural pattern:

- **Background:** the app's `surface` color (`#FFFDE7`) fills the full screen
- **Hero area:** occupies the top portion, drawn over the surface background
- **Floating white card:** `Shape(topStart=28.dp, topEnd=28.dp)`, elevation via `shadow`, overlaps the hero by `16.dp` using a `Box` layout

The card is not a bottom sheet — it is a `Column` inside a `Box` that stacks the hero and card as layers. The card grows to fill the remaining vertical space. Concretely: `OnboardingScreen` uses a `Box(Modifier.fillMaxSize())` where the hero (`fillMaxWidth()`) is drawn first and the card column is offset downward by `(heroHeight - 16.dp)` using `padding(top = heroHeight - 16.dp)`, so it visually overlaps the hero bottom edge.

---

## Screen 1 — Welcome

### Hero area
- Height: fills ~45% of screen (use `fillMaxHeight(0.45f)`)
- Background: gradient from `primaryContainer` (`#F8BBD0`) to `surface` (`#FFFDE7`), angle ~135°
- Content: single 🍼 emoji, `fontSize = 80.sp`, centered

### Floating card
- Background: `MaterialTheme.colorScheme.surface` (white in light theme)
- Top corner radius: `28.dp`
- Padding: `24.dp` horizontal, `24.dp` top, `32.dp` bottom
- Contents (top to bottom):
  1. Title: `"Welcome to Baby Tracker"` — `typography.headlineMedium` (ExtraBold)
  2. `Spacer(8.dp)`
  3. Subtitle: `"Your companion for the first months of parenthood."` — `typography.bodyMedium`, color `onSurfaceVariant`
  4. `Spacer` with `weight(1f)`
  5. CTA button: `"Get started"` — full-width, `shapes.extraLarge`, `primary` background

### Behaviour
- `BackHandler` is disabled on this screen
- Button always enabled (no validation needed)
- No progress bar

---

## Screen 2 — Baby Info

### Hero strip
- Height: `88.dp`
- Background: gradient from `primaryContainer` (`#F8BBD0`) to `surface` (`#FFFDE7`)
- Left: `IconButton` with back arrow (`Icons.AutoMirrored.Filled.ArrowBack`), navigates to `WELCOME`
- Center: `"BABY INFO"` — `typography.labelMedium` (Bold, uppercase per theme token), color `onPrimaryContainer`

### Floating card
- Overlaps hero by `16.dp`
- Padding: `20.dp` horizontal, `16.dp` top, `32.dp` bottom
- Contents (top to bottom):
  1. **Progress bar:** `LinearProgressIndicator`, `progress = 0.5f`, `trackColor = outlineVariant`, height `2.dp`, full width, `ClipShape(4.dp)`
  2. `Spacer(16.dp)`
  3. Heading: `"Tell us about your baby"` — `typography.headlineSmall`
  4. `Spacer(20.dp)`
  5. Baby name `OutlinedTextField` — same validation as current (`≤ 50 chars`, `KeyboardCapitalization.Words`, `ImeAction.Next`), auto-focused on entry
  6. `Spacer(12.dp)`
  7. Date of birth read-only `OutlinedTextField` — tap opens `DatePickerDialog` (same implementation as current `BirthDateStepContent`)
  8. `Spacer(4.dp)`
  9. Age display text — `typography.bodySmall`, `onSurfaceVariant`
  10. Age warning `AssistChip` (visible when baby > 12 months) — same as current
  11. `Spacer` with `weight(1f)`
  12. `"Next"` button — full-width, `shapes.extraLarge`, disabled when name is blank

### Behaviour
- `BackHandler` navigates to `WELCOME`
- `isNextEnabled` requires `babyName.isNotBlank()`
- The `DatePickerDialog` state (`showDatePicker`) is local to the composable — not in `UiState`

---

## Screen 3 — Allergies

### Hero strip
- Height: `88.dp`
- Background: gradient from `secondaryContainer` (`#B3E5FC`) to `surface` (`#FFFDE7`) — blue distinguishes this step from the pink baby info step
- Left: back arrow navigating to `BABY_INFO`
- Center: `"ALLERGIES"` — `typography.labelMedium`, color `onSecondaryContainer`

### Floating card
- Same shape and padding as Baby Info card
- Contents (top to bottom):
  1. **Progress bar:** `progress = 1.0f` (complete)
  2. `Spacer(16.dp)`
  3. Heading: `"Does [name] have any known allergies?"` — `typography.headlineSmall`
  4. `Spacer(16.dp)`
  5. Allergy chips in `FlowRow` — same `FilterChip` implementation as current, `secondaryContainer` selected color
  6. Animated custom note `OutlinedTextField` (visible when `AllergyType.OTHER` selected) — same `AnimatedVisibility` as current
  7. `"You can update this later in Settings."` hint text when no allergy selected
  8. `Spacer` with `weight(1f)`
  9. `"Get Started"` button — full-width, `shapes.extraLarge`; shows `CircularProgressIndicator` while saving

### Behaviour
- `BackHandler` navigates to `BABY_INFO`
- `isNextEnabled` always true (allergies are optional)
- Save and navigate on button tap — same `onFinish` logic as current

---

## ViewModel Changes

### `OnboardingStep` enum
```kotlin
enum class OnboardingStep { WELCOME, BABY_INFO, ALLERGIES }
```

### `OnboardingUiState`
Remove `showAgeWarning` — keep it, still needed for the date field on `BABY_INFO`.  
No other state changes needed.

### `isNextEnabled`
```kotlin
val isNextEnabled: Boolean
    get() = when (_uiState.value.currentStep) {
        OnboardingStep.WELCOME -> true
        OnboardingStep.BABY_INFO -> _uiState.value.babyName.isNotBlank()
        OnboardingStep.ALLERGIES -> true
    }
```

### `onNextStep` / `onPreviousStep`
Follow the linear `WELCOME → BABY_INFO → ALLERGIES` sequence.

---

## Component File Changes

| Action | File |
|--------|------|
| **Delete** | `components/StepIndicator.kt` |
| **Delete** | `components/NameStepContent.kt` |
| **Delete** | `components/BirthDateStepContent.kt` |
| **Create** | `components/WelcomeStepContent.kt` |
| **Create** | `components/OnboardingHeroStrip.kt` |
| **Create** | `components/BabyInfoStepContent.kt` (merges name + date) |
| **Modify** | `components/AllergiesStepContent.kt` (progress bar added, no other logic changes) |
| **Modify** | `OnboardingScreen.kt` (new `Box`-based layout, drives hero+card structure) |
| **Modify** | `OnboardingViewModel.kt` (new enum, updated `isNextEnabled` and step transitions) |

---

## Non-Goals

- No animation between hero strips (the strip updates with `AnimatedContent` the same way the card content does)
- No dark mode–specific hero gradients — the existing dark color scheme tokens are sufficient
- No changes to the save/persist logic in `SaveBabyProfileUseCase`
- No changes to navigation — onboarding still routes to home on completion
