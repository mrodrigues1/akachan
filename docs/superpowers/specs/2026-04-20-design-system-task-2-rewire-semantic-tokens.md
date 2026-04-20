# SPEC — Task 2: Rewire Semantic Color Tokens to Palette Scales

**Part of:** [2026-04-20-design-system-alignment-design.md](./2026-04-20-design-system-alignment-design.md)
**Status:** Ready for implementation
**Blocks:** none
**Blocked by:** Task 1

---

## Problem

After Task 1, the raw palette scales (`Pink700`, `Blue200`, etc.) exist but nothing uses them. The existing semantic tokens (`PrimaryPink`, `PrimaryContainerPink`, `OnPrimaryContainerDarkPink`, etc.) still declare literal hex values identical to what the scale tokens resolve to. This duplication means a future palette change would need to be applied in two places.

## Scope

Pure re-pointing. Each semantic token that has an exact scale equivalent gets its literal hex replaced by the scale constant. No changes to `Theme.kt`, no new tokens, no behaviour change.

## Files to modify

- `app/src/main/java/com/babytracker/ui/theme/Color.kt`

## Exact changes

### Before

```kotlin
// Light scheme semantic tokens
val PrimaryPink = Color(0xFFC2185B)
val OnPrimaryWhite = Color(0xFFFFFFFF)
val PrimaryContainerPink = Color(0xFFF8BBD0)
val OnPrimaryContainerDarkPink = Color(0xFF880E4F)

val SecondaryBlue = Color(0xFF1976D2)
val OnSecondaryWhite = Color(0xFFFFFFFF)
val SecondaryContainerBlue = Color(0xFFB3E5FC)
val OnSecondaryContainerDarkBlue = Color(0xFF0D47A1)

val TertiaryGreen = Color(0xFF388E3C)
val OnTertiaryWhite = Color(0xFFFFFFFF)
val TertiaryContainerGreen = Color(0xFFC8E6C9)
val OnTertiaryContainerDarkGreen = Color(0xFF1B5E20)

// ...

// Dark scheme semantic tokens
val PrimaryPinkDark = Color(0xFFF48FB1)
val PrimaryContainerPinkDark = Color(0xFF880E4F)
val SecondaryBlueDark = Color(0xFF90CAF9)
val SecondaryContainerBlueDark = Color(0xFF0D47A1)
val TertiaryGreenDark = Color(0xFFA5D6A7)
val TertiaryContainerGreenDark = Color(0xFF1B5E20)
```

### After

```kotlin
// ─── Light scheme semantic tokens ─────────────────────────────
val PrimaryPink = Pink700
val OnPrimaryWhite = Color(0xFFFFFFFF)
val PrimaryContainerPink = Pink200
val OnPrimaryContainerDarkPink = Pink900

val SecondaryBlue = Blue700
val OnSecondaryWhite = Color(0xFFFFFFFF)
val SecondaryContainerBlue = Blue200
val OnSecondaryContainerDarkBlue = Blue900

val TertiaryGreen = Green700
val OnTertiaryWhite = Color(0xFFFFFFFF)
val TertiaryContainerGreen = Green200
val OnTertiaryContainerDarkGreen = Green900

// ... (SurfaceYellow, OnSurfaceDark, OnSurfaceVariantGrey unchanged — no scale equivalent)

// ─── Dark scheme semantic tokens ──────────────────────────────
val PrimaryPinkDark = Color(0xFFF48FB1)      // no scale equivalent (lighter than -200)
val PrimaryContainerPinkDark = Pink900
val SecondaryBlueDark = Color(0xFF90CAF9)    // no scale equivalent
val SecondaryContainerBlueDark = Blue900
val TertiaryGreenDark = Color(0xFFA5D6A7)    // no scale equivalent
val TertiaryContainerGreenDark = Green900
```

### Rationale for partial rewiring

- The light-scheme "-700" and "-200" and "-900" tokens all have exact matches in the new scale → rewire.
- The dark-scheme lightest tones (`#F48FB1`, `#90CAF9`, `#A5D6A7`) are brighter than `Pink100`/`Blue100`/`Green100` and don't exist in the scale. Leave as literals. Adding them to the scale would be a design-system change, not an alignment.
- `OnPrimaryWhite` / `OnSecondaryWhite` / `OnTertiaryWhite` stay as literal `#FFFFFF` — pure white has no brand scale membership.
- `SurfaceYellow`, `OnSurfaceDark`, `OnSurfaceVariantGrey`, `SurfaceDark`, `OnSurfaceDarkTheme` unchanged.

## Acceptance criteria

- [ ] Every light-scheme semantic token with a scale equivalent now references the scale constant (e.g., `val PrimaryPink = Pink700`).
- [ ] Dark-scheme container tokens reference scale constants (`Pink900`, `Blue900`, `Green900`).
- [ ] No Material 3 color scheme values change — `./gradlew build` passes and the emulator renders identically.
- [ ] `Theme.kt` is NOT modified in this task.

## Rollback

`git revert <sha>`. No downstream consumers of the scale constants yet (Task 3 will be first).

## Commit message

```
refactor(ui): rewire semantic color tokens to palette scale constants

Light-scheme primary/secondary/tertiary tokens and dark-scheme containers
now reference Pink/Blue/Green scale constants from Task 1 instead of
duplicate hex literals. No behaviour change.
```
