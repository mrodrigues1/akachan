# SPEC — Task 1: Restructure Raw Palette into Scales

**Part of:** [2026-04-20-design-system-alignment-design.md](./2026-04-20-design-system-alignment-design.md)
**Status:** Ready for implementation
**Blocks:** Task 2
**Blocked by:** none

---

## Problem

The current `Color.kt` defines raw color constants as a flat list of named tones (`BabyBlue`, `BabyPink`, `SoftGreen`, `SoftYellow`). The design system's `colors_and_type.css` organises the raw palette into a structured lightness scale per brand color:

```
pink-900 (#880E4F)  pink-700 (#C2185B)  pink-200 (#F8BBD0)  pink-100 (#F4C2C2)
blue-900 (#0D47A1)  blue-700 (#1976D2)  blue-200 (#B3E5FC)  blue-100 (#89CFF0)
green-900(#1B5E20)  green-700(#388E3C)  green-200(#C8E6C9)  green-100(#90EE90)
```

The scale expresses the relationship between tones (e.g., 700 = primary action, 200 = container, 900 = deepest on-container text). Having this in code — not just in CSS — enables future palette tweaks to propagate consistently.

## Scope

Introduce the 12 scale constants in `Color.kt` alongside the existing semantic tokens. No rewiring of semantic tokens in this task — that happens in Task 2.

## Files to modify

- `app/src/main/java/com/babytracker/ui/theme/Color.kt`

## Exact changes

### `Color.kt`

Replace the current "Raw palette constants" block (lines 5–9):

```kotlin
// Raw palette constants (kept as reference)
val BabyBlue = Color(0xFF89CFF0)
val BabyPink = Color(0xFFF4C2C2)
val SoftGreen = Color(0xFF90EE90)
val SoftYellow = Color(0xFFFFF9C4)
```

with the structured scale:

```kotlin
// ─── Raw palette — structured scale ─────────────────────────────
// Mirrors design-System-handoff/akachan-design-system/project/colors_and_type.css.
// 700 = primary action, 200 = container, 900 = on-container text, 100 = softest tone.

// Feeding / Primary
val Pink900 = Color(0xFF880E4F)
val Pink700 = Color(0xFFC2185B)
val Pink200 = Color(0xFFF8BBD0)
val Pink100 = Color(0xFFF4C2C2)

// Sleep / Secondary
val Blue900 = Color(0xFF0D47A1)
val Blue700 = Color(0xFF1976D2)
val Blue200 = Color(0xFFB3E5FC)
val Blue100 = Color(0xFF89CFF0)

// Success / Tertiary
val Green900 = Color(0xFF1B5E20)
val Green700 = Color(0xFF388E3C)
val Green200 = Color(0xFFC8E6C9)
val Green100 = Color(0xFF90EE90)

// Soft yellow — retained for surface (no scale equivalent)
val SoftYellow = Color(0xFFFFF9C4)
```

Semantic tokens (lines 11–39) are left untouched in this task.

## Usage check & deletion policy

Before removing `BabyBlue`, `BabyPink`, `SoftGreen`, grep the codebase:

```bash
# run from repo root
rg -n "BabyBlue|BabyPink|SoftGreen" app/src
```

- If zero references outside `Color.kt` itself → delete them.
- If referenced elsewhere → keep them as aliases:
  ```kotlin
  // Backward-compat aliases — prefer the scale tokens above in new code.
  val BabyBlue = Blue100
  val BabyPink = Pink100
  val SoftGreen = Green100
  ```

`SoftYellow` is retained unconditionally — it has no scale equivalent and may be referenced by future surface tinting work.

## Acceptance criteria

- [ ] 12 new scale constants (`Pink100/200/700/900`, `Blue100/200/700/900`, `Green100/200/700/900`) declared in `Color.kt`.
- [ ] Old constants (`BabyBlue`, `BabyPink`, `SoftGreen`) deleted OR re-aliased based on usage grep.
- [ ] `SoftYellow` retained.
- [ ] Semantic tokens block untouched.
- [ ] `./gradlew build` passes.
- [ ] No visible change in emulator — nothing yet references the new scale.

## Rollback

`git revert <sha>`. No downstream dependencies until Task 2 runs.

## Commit message

```
refactor(ui): introduce structured palette scale (pink/blue/green 100–900)

Mirrors the design-System-handoff colors_and_type.css scale. Pure additive
refactor — semantic tokens still point at literal hex values and will be
rewired in a follow-up.
```
