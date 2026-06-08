# AI Progress Tasks — Sleep Prediction Home Screen

Source: `/impeccable critique sleep prediction home screen`
Score: **24/40** (Acceptable — significant improvements needed)

---

## Task 1 — Fix Motion Violations (P1)

**Command:** `/impeccable polish motion CueQuickTapRow and InventoryHomeCard`

**Files:**
- `app/src/main/java/com/babytracker/ui/component/CueQuickTapRow.kt`
- `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

**Findings:**
- `CueQuickTapRow.kt:100` — `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)` on the check-icon animation. Bounce/elastic easing is explicitly banned by DESIGN.md. Replace with `tween(160, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))`.
- `CueQuickTapRow.kt:114–117` — tap sequence uses `FastOutSlowInEasing` (symmetric ease-in-out). System signature is `EaseOutQuart`. Replace throughout the four `animateTo` calls.
- `HomeScreen.kt:691` — `InventoryHomeCard.animateContentSize` uses `LinearOutSlowInEasing`. Every other home card uses `EaseOutQuart`. Replace with `tween(200, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))`.

**Status:** [x] Done

---

## Task 2 — CueQuickTapRow: Add Section Header + Confirmation Feedback (P1–P2)

**Command:** `/impeccable clarify CueQuickTapRow header and feedback copy`

**Files:**
- `app/src/main/java/com/babytracker/ui/component/CueQuickTapRow.kt`
- `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

**Findings:**
- CueQuickTapRow appears below SleepPredictionCard with no label. Six filter chips render with no explanation of what tapping them does. First-time users have no mental model.
- Fix: Add a `labelMedium` + UPPERCASE section header `"LOG A CUE"` above the row. Use 8dp gap above the header, 4dp gap between header and chips. Matches the `"SLEEP PREDICTION"` label pattern used in SleepPredictionCard.
- Tapping a chip triggers a scale animation then auto-deselects after 1.5s. No visible confirmation that the event was persisted. Silent logging violates the brand's "Reassuring signals" principle.
- Fix: After a tap, show a fade-in confirmation line (e.g., `"Sleepy logged"`) below the chip row in `bodySmall`. Auto-dismiss after 2s using `AnimatedVisibility`.

**Status:** [x] Done

---

## Task 3 — SleepPredictionCard: Clarify Tap Affordance (P2)

**Command:** `/impeccable harden SleepPredictionCard`

**Files:**
- `app/src/main/java/com/babytracker/ui/sleep/SleepPredictionCard.kt`
- `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

**Findings:**
- SleepPredictionCard uses `Card(...)` with no `onClick`. Visually identical to the four tappable domain cards (same `shapes.large`, same padding, same container fill). Users expect it to be tappable but it is not.
- Two valid fixes:
  - **Option A (preferred):** Add an `onClick = onNavigateToSleep` callback. Expose it via `HomeScreen` where it already has `onNavigateToSleep`. Lets users drill into sleep history from the prediction card.
  - **Option B:** Replace `Card` with a non-elevated `Surface` + `1.dp outlineVariant` border (the "Tip Card" pattern from DESIGN.md) to signal it is informational, not interactive.

**Status:** [x] Done

---

## Task 4 — Home Card Grid: Break Identical Template (P2)

**Command:** `/impeccable layout home summary cards`

**Files:**
- `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

**Findings:**
- All four summary cards use the same structure: emoji top-left + ActiveStatusBadge top-right + `titleMedium` domain name + `bodyMedium` context line. Banned by DESIGN.md: "Identical card grids. Same-sized cards with icon + heading + text, repeated endlessly."
- Pumping and Inventory cards are weaker static variants in a grid of live-state tiles. Inventory has no active state, no badge, no timer — it does not belong in the same visual tier.
- Fix: Remove Inventory from the 2×2 grid. Render it as a compact horizontal strip below the grid (mL count + bag count side by side in a single row card). Frees the grid to three domain-tracking cards (Breastfeeding, Sleep, Pumping) plus one asymmetric slot (e.g., last-night sleep summary).

**Status:** [x] Done

---

## Task 5 — Bottom Bar Navigation Redundancy (P2)

**Command:** `/impeccable layout bottom navigation`

**Files:**
- `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

**Findings:**
- "Feeding" and "Sleep" exist as both persistent bottom bar buttons AND tappable top-grid cards. Pumping and Inventory have only the card. Asymmetry: two domains get two navigation paths, two get one.
- Bottom bar consumes ~60dp of always-visible vertical space.
- Fix options:
  - **Option A:** Remove the bottom bar; rely on the card grid for navigation. Add a context-aware FAB ("Start feeding" / "Start sleep") that surfaces the most likely next action based on elapsed time.
  - **Option B:** Keep the bottom bar, add all four domains using icons + labels (standard Material NavigationBar pattern).

**Status:** [x] Done

---

## Minor Fixes (P3 — bundle into any of the above tasks)

| Location | Issue | Fix |
|---|---|---|
| `HomeScreen.kt` — `FeedingPredictionSubtitle` | Schedule icon tint stays `onPrimaryContainer` when overdue; text shifts to `error` but icon does not | Change icon tint to `primaryColor` (already computed) so both shift together |
| `HomeScreen.kt` — `ActiveStatusBadge` | Play/Pause icons are `10.dp` — barely legible on small/AMOLED screens | Increase to `12.dp` |
| `SleepPredictionCard.kt` — `WindowCardContent` | `~${window.bestEstimate.formatTime()}` — tilde is a programmer's approximation symbol, unfamiliar to parents | Replace with `"Around ${window.bestEstimate.formatTime()}"` or use the word "~" only if consistent with other prediction screens |
| `HomeScreen.kt` — Sleep card idle state | `"No sleep recorded yet"` is flat copy | Consider `"Ready when you are"` or remove the line and rely on the "Sleep" button alone |

---

## Re-run after fixes

```
/impeccable critique sleep prediction home screen
```

Target score after all tasks: **32–35/40**
