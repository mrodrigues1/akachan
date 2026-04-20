# SPEC — Task 4: Add Missing Typography Roles

**Part of:** [2026-04-20-design-system-alignment-design.md](./2026-04-20-design-system-alignment-design.md)
**Status:** Ready for implementation
**Blocks:** none
**Blocked by:** none

---

## Problem

`AkachanTypography` in `Type.kt` defines only six roles: `displaySmall`, `headlineLarge`, `titleLarge`, `bodyLarge`, `labelMedium`, `labelSmall`. Screens that use `MaterialTheme.typography.titleMedium`, `titleSmall`, `bodyMedium`, or `bodySmall` silently inherit Material 3's defaults.

The design system's `colors_and_type.css` declares canonical values for all four missing roles. Declaring them explicitly:

1. Makes the theme self-documenting — a developer reading `Type.kt` sees the full scale.
2. Shields the app from Material 3 default drift in future library upgrades.
3. Catches any accidental mismatch between spec and runtime.

## Scope

Append four `TextStyle` entries to `AkachanTypography` matching the spec exactly. No screen changes — Compose will pick up the new values automatically.

## Files to modify

- `app/src/main/java/com/babytracker/ui/theme/Type.kt`

## Exact changes

Insert these entries in `AkachanTypography` after `titleLarge` and before `bodyLarge` (preserving Material 3 conventional ordering):

```kotlin
titleMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.sp
),
titleSmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
),
```

And after `bodyLarge`:

```kotlin
bodyMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
),
bodySmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp
),
```

Final role order in `AkachanTypography`:

```
displaySmall
headlineLarge
titleLarge
titleMedium   ← new
titleSmall    ← new
bodyLarge
bodyMedium    ← new
bodySmall     ← new
labelMedium
labelSmall
```

## Values — source mapping

All values sourced from `design-System-handoff/akachan-design-system/project/colors_and_type.css`:

| Role | Source line | Size | Weight | Line-height | Letter-spacing |
|---|---|---|---|---|---|
| `titleMedium` | 134, 183–189 | 18px | 600 | 26px | 0 |
| `titleSmall` | 135, 191–197 | 14px | 600 | 20px | 0 |
| `bodyMedium` | 137, 207–212 | 14px | 400 | 20px | 0 |
| `bodySmall` | 138, 214–219 | 12px | 400 | 16px | 0 |

## Acceptance criteria

- [ ] `AkachanTypography` contains all 10 roles in conventional Material 3 order.
- [ ] Weights and sizes match the source mapping table above exactly.
- [ ] `./gradlew build` passes.
- [ ] Manual spot-check: run app in emulator; any screen that previously used `titleMedium` / `titleSmall` / `bodyMedium` / `bodySmall` via Material 3 defaults should render at the same or very close visual weight. Flag any regression.

## Rollback

`git revert <sha>`. Material 3 defaults resume immediately.

## Commit message

```
feat(ui): add canonical typography roles (titleMedium/Small, bodyMedium/Small)

Formalises four typography roles in AkachanTypography that were previously
inherited as Material 3 defaults. Values mirror colors_and_type.css from
design-System-handoff.
```
