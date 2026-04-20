# SPEC — Task 3: Add Missing Semantic Color Tokens

**Part of:** [2026-04-20-design-system-alignment-design.md](./2026-04-20-design-system-alignment-design.md)
**Status:** Ready for implementation
**Blocks:** none
**Blocked by:** none (Task 1 and 2 are recommended but not strictly required)

---

## Problem

The app's `LightColorScheme` / `DarkColorScheme` in `Theme.kt` leaves several Material 3 slots unset:

- `surfaceVariant` (used for inactive side-breakdown tiles during breastfeeding)
- `outline` (used for dividers and outlined-card borders)
- `outlineVariant` (used for subtle separators)
- `error`, `errorContainer`, `onErrorContainer` (error states)

For these, the app silently picks up Material 3 defaults. The design system's `colors_and_type.css` and `Screens.jsx` specify explicit canonical values. We'll define those tokens explicitly.

## Scope

- Add 6 new light-scheme token values and their dark-scheme counterparts to `Color.kt`.
- Pass them into `LightColorScheme` / `DarkColorScheme` in `Theme.kt`.
- Manual visual spot-check in emulator.

## Files to modify

- `app/src/main/java/com/babytracker/ui/theme/Color.kt`
- `app/src/main/java/com/babytracker/ui/theme/Theme.kt`

## Exact changes

### `Color.kt` — append to the light-scheme semantic tokens block

```kotlin
// Surface variant — used for inactive containers (e.g., non-current side tile)
val SurfaceVariantLight = Color(0xFFF0EDE0)

// Outline — dividers and outlined-card borders (follows Screens.jsx canonical value)
val OutlineLight = Color(0xFFCAC4D0)
val OutlineVariantLight = Color(0xFFCAC4D0)

// Error
val ErrorLight = Color(0xFFB00020)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)
```

### `Color.kt` — append to the dark-scheme semantic tokens block

```kotlin
val SurfaceVariantDark = Color(0xFF2B2930)
val OutlineDark = Color(0xFF938F99)
val OutlineVariantDark = Color(0xFF49454F)
val ErrorDark = Color(0xFFFFB4AB)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)
```

### `Theme.kt` — extend `LightColorScheme`

Add these entries to the `lightColorScheme(...)` call (after the existing `onSurfaceVariant = OnSurfaceVariantGrey` line):

```kotlin
surfaceVariant = SurfaceVariantLight,
outline = OutlineLight,
outlineVariant = OutlineVariantLight,
error = ErrorLight,
errorContainer = ErrorContainerLight,
onErrorContainer = OnErrorContainerLight,
```

### `Theme.kt` — extend `DarkColorScheme`

```kotlin
surfaceVariant = SurfaceVariantDark,
outline = OutlineDark,
outlineVariant = OutlineVariantDark,
error = ErrorDark,
errorContainer = ErrorContainerDark,
onErrorContainer = OnErrorContainerDark,
```

## Note on `outline`

The design system's CSS declares `--color-outline: #79747E` (Material 3 default) but `Screens.jsx` consistently uses `#CAC4D0`. We follow `Screens.jsx` because it's the canonical component-layer reference. If dividers/outlined-cards look too light after this change, revert by mapping `OutlineLight = Color(0xFF79747E)` — but do not leave the token unset.

## Acceptance criteria

- [ ] Six new light-scheme tokens and six dark-scheme tokens declared in `Color.kt`.
- [ ] `LightColorScheme` and `DarkColorScheme` in `Theme.kt` reference them via six new assignments each.
- [ ] `./gradlew build` passes.
- [ ] Manual spot-check: run app in emulator; visit Breastfeeding screen during an active session — the inactive side tile (which uses `surfaceVariant`) should render with a subtle warm-grey tint rather than Material 3's default cool-grey. Confirm the visual is acceptable.
- [ ] No crashes.

## Rollback

`git revert <sha>`. Tokens are additive; Material 3 defaults resume the moment the entries are removed from the color schemes.

## Commit message

```
feat(ui): add canonical semantic color tokens (surfaceVariant, outline, error)

Formalises the surfaceVariant, outline, outlineVariant, error,
errorContainer, and onErrorContainer tokens in both light and dark
color schemes, per design-System-handoff. Replaces Material 3 defaults
with the canonical brand values.
```
