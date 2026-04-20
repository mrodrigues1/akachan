# SPEC — Task 7: HistoryCard Badge Size Alignment

**Part of:** [2026-04-20-design-system-alignment-design.md](./2026-04-20-design-system-alignment-design.md)
**Status:** Ready for implementation
**Blocks:** none
**Blocked by:** none (independent of Tasks 1–6)

---

## Problem

The design system's Android UI kit (`design-System-handoff/akachan-design-system/project/ui_kits/android/Screens.jsx` line 135) specifies the `HistoryCard` emoji badge as:

```js
width: 44, height: 44, borderRadius: R.sm, fontSize: 20
```

The current implementation in `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt` (line 48) uses `Modifier.size(36.dp)` with `MaterialTheme.typography.bodyLarge` (16sp) for the emoji — a visible undersize versus the spec.

This is a **user-visible change**, unlike Tasks 1–6 which are pure canonicalisation. Called out deliberately: Option A of the original rollout explicitly excluded visible polish. This task is a scoped one-off addition to close this specific gap.

## Scope

Single-file, single-component change. Affects every screen that renders `HistoryCard`:

- `BreastfeedingScreen.kt`
- `BreastfeedingHistoryScreen.kt`
- `SleepTrackingScreen.kt`
- `SleepHistoryScreen.kt`

No call-site changes — `HistoryCard` has no size-related public parameter, so the fix lives entirely inside the component.

## Files to modify

- `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt`

## Exact changes

### Before (lines 46–56)

```kotlin
Box(
    modifier = Modifier
        .size(36.dp)
        .background(
            color = badgeColor,
            shape = MaterialTheme.shapes.small
        ),
    contentAlignment = Alignment.Center
) {
    Text(text = badgeEmoji, style = MaterialTheme.typography.bodyLarge)
}
```

### After

```kotlin
Box(
    modifier = Modifier
        .size(44.dp)
        .background(
            color = badgeColor,
            shape = MaterialTheme.shapes.small
        ),
    contentAlignment = Alignment.Center
) {
    Text(text = badgeEmoji, fontSize = 20.sp)
}
```

Required new import:

```kotlin
import androidx.compose.ui.unit.sp
```

### Rationale for choosing 44dp (not 40dp)

The original delta note said "40–44dp." `Screens.jsx` is unambiguous at 44. 44dp is also Material Design's recommended minimum interactive target size — while the badge isn't interactive, matching the spec exactly avoids another round of tweaking later.

### Rationale for `fontSize = 20.sp` (not a typography role)

The design spec's `fontSize: 20` doesn't map cleanly to any of our typography roles (`bodyLarge` is 16sp; `titleMedium` is 18sp; `titleLarge` is 22sp). Emoji glyphs aren't body text — they're graphical content rendered as text for convenience. A literal `fontSize` is the right level of abstraction here; no semantic typography role is being bypassed in the type system.

## Visual impact

- Breastfeeding history list: emoji badges grow from 36dp to 44dp — ~22% larger. Content density per row decreases slightly.
- Sleep history / today list: same.
- Row height: currently driven by the badge + 12dp vertical padding = 60dp. New row height ≈ 68dp. Expect a small scroll-depth increase on long lists.

Review visually in emulator after the change. If the list feels too loose, revisit the vertical padding from 12dp down to 10dp as a follow-up.

## Acceptance criteria

- [ ] `HistoryCard.kt` badge `Box` uses `.size(44.dp)`.
- [ ] Emoji `Text` uses `fontSize = 20.sp` (literal, not a typography role).
- [ ] `androidx.compose.ui.unit.sp` import present.
- [ ] `./gradlew build` passes.
- [ ] Manual spot-check in emulator:
  - Breastfeeding history screen — badges visibly larger, centered, readable.
  - Sleep history screen — same.
  - Home screen — no effect (does not use `HistoryCard`).
- [ ] No call-site regressions — call sites pass the same parameters as before.

## Rollback

`git revert <sha>`. Pure component-internal change.

## Commit message

```
feat(ui): align HistoryCard badge size with design system (36dp → 44dp)

Matches the 44x44 badge specified in design-System-handoff's Android UI
kit (Screens.jsx line 135). Emoji font size bumped to 20sp to match.
Affects breastfeeding history, sleep history, and active sleep/feeding
session lists — no call-site changes.
```
