# AI Progress Tasks — SleepPredictionCard Accessibility

Source: `/impeccable audit SleepPredictionCard accessibility`
Score: **18/20 Excellent** — accessibility is the only weak dimension (2/4)

Systemic root cause: `.copy(alpha < 0.9f)` on `onSecondaryContainer` text fails WCAG AA on saturated container surfaces. All tasks below address this or related a11y gaps. Run each task independently in a separate session.

---

## Task 1 — Fix header text contrast (P1)

**Command:** `/impeccable harden SleepPredictionCard header label contrast`

**File:** `app/src/main/java/com/babytracker/ui/sleep/SleepPredictionCard.kt`

**Finding:** The "SLEEP PREDICTION" label (line 111) and Bedtime icon tint (line 104) use `MaterialTheme.colorScheme.secondary` (`Blue700` #1976D2) on `secondaryContainer` (`Blue200` #B3E5FC). Measured contrast: **3.18:1 light mode**. WCAG AA requires 4.5:1. `secondary` on `secondaryContainer` is not a guaranteed M3 contrast pair.

**Fix:** Change both the icon tint and the label color from `MaterialTheme.colorScheme.secondary` to `MaterialTheme.colorScheme.onSecondaryContainer` (5.74:1 measured). The card's blue container background already communicates the sleep domain — the header text does not need to be a different hue.

**WCAG:** 1.4.3 (text contrast AA), 1.4.11 (non-text contrast for icon)

**Scope:** Lines 104 and 111 only. Do not change ConfidenceDots or any other uses of `secondary`.

**Also affects:** `SleepRecommendationSection.kt` has the same pattern (lines 83 and 89) — fix both files in the same pass.

---

## Task 2 — Fix alpha-reduced text contrast (P1)

**Command:** `/impeccable harden SleepPredictionCard alpha-reduced text contrast`

**File:** `app/src/main/java/com/babytracker/ui/sleep/SleepPredictionCard.kt`

**Findings (all fail WCAG AA in light mode):**

| Location | Alpha | Measured contrast | Text |
|---|---|---|---|
| `WindowCardContent` line 166 | 0.65f | 2.89:1 | Window time range |
| `NeedMoreDataCardContent` line 323 | 0.65f | 2.89:1 | Session count label |
| `NeedMoreDataCardContent` line 330 | 0.65f | 2.89:1 | Day progress line |
| `WindowCardContent` line 187 | 0.85f | 4.36:1 | Reasons bullet text |
| `WindowCardContent` line 207 | 0.85f | 4.36:1 | Feed prompt text |
| `StatusRow` via `CurrentlySleepingCardContent` line 354 | 0.80f | 3.89:1 | "Baby is sleeping" |

**Fix:**
- Lines 166, 323, 330: Remove `.copy(alpha = 0.65f)`. Use `onSecondaryContainer` at full opacity. Visual hierarchy is already established by typography scale (`headlineLarge` vs `bodyLarge`/`labelSmall`).
- Lines 187, 207: Remove `.copy(alpha = 0.85f)`. Use full `onSecondaryContainer`.
- `StatusRow` `textAlpha` parameter at line 354 (`textAlpha = 0.8f`): Remove the alpha. The "sleeping" state's quiet feel is conveyed by the icon's dimmed alpha (0.7f, which is decorative and not subject to text-contrast WCAG rules) — text doesn't need to drop too.

**WCAG:** 1.4.3 (AA)

**Scope:** Only the specific `.copy(alpha = X)` calls on text color listed above. Do not change alpha on decorative elements (bullet dots, unfilled progress dots, icon alpha in `StatusRow.iconAlpha`).

---

## Task 3 — Add liveRegion to safe-sleep expanded content (P1) ✓ DONE

**Command:** `/impeccable harden SleepPredictionCard safe-sleep liveRegion`

**File:** `app/src/main/java/com/babytracker/ui/sleep/SleepPredictionCard.kt`

**Finding:** When TalkBack users activate "Expand safe-sleep tip" (line 219), the toggle's `contentDescription` flips to "Collapse safe-sleep tip" — that part is correct. But the actual safety tip text (`window.safetyPrompt`, lines 242-246) appears via `AnimatedVisibility` with no announcement. TalkBack users have no feedback that new content appeared.

**Fix:** Add `liveRegion = LiveRegionMode.Polite` to the safety prompt `Text`:

```kotlin
AnimatedVisibility(
    visible = safetyExpanded,
    enter = fadeIn(tween(150, easing = EaseOutQuart)),
    exit = fadeOut(tween(120, easing = EaseOutQuart)),
) {
    Text(
        text = window.safetyPrompt,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
```

Add import: `androidx.compose.ui.semantics.LiveRegionMode`

**WCAG:** 4.1.3 Status Messages (AA)

**Scope:** Single `Text` composable inside `WindowCardContent`. No other changes.

---

## Task 4 — Make card contentDescription state-aware (P2) ✓ DONE

**Command:** `/impeccable harden SleepPredictionCard state-aware contentDescription`

**File:** `app/src/main/java/com/babytracker/ui/sleep/SleepPredictionCard.kt`

**Finding:** The card's `contentDescription` is always `"Sleep prediction. Open sleep screen."` (line 88) regardless of which state is showing. TalkBack users focused at the card summary level cannot distinguish a Window prediction (has a specific time), NeedMoreData (collecting patterns), Overdue (needs attention), or CurrentlySleeping.

**Fix:** Derive the description from `visibleState` before the `AnimatedVisibility`:

```kotlin
val cardDescription = when (visibleState) {
    is SleepPredictionState.Window ->
        "Next sleep around ${(visibleState as SleepPredictionState.Window).window.bestEstimate.formatTime()}. Open sleep screen."
    is SleepPredictionState.NeedMoreData ->
        "Sleep prediction: still learning patterns. Open sleep screen."
    SleepPredictionState.Overdue ->
        "Sleep prediction: watch for cues. Open sleep screen."
    SleepPredictionState.CurrentlySleeping ->
        "Baby is sleeping. Open sleep screen."
    SleepPredictionState.CueLed ->
        "Sleep prediction: cue-led. Open sleep screen."
    SleepPredictionState.AfterActiveFeed ->
        "Sleep prediction: window appears after feed. Open sleep screen."
    is SleepPredictionState.Unavailable ->
        "Sleep prediction. Open sleep screen."
}
```

Then pass `cardDescription` into:
```kotlin
modifier = Modifier
    .fillMaxWidth()
    .semantics { contentDescription = cardDescription },
```

**WCAG:** 4.1.2 Name, Role, Value (AA)

**Scope:** `SleepPredictionCard` composable only, lines 63-147. No changes to inner content composables.

---

## Task 5 — Add stateDescription to safe-sleep toggle (P3) ✓ DONE

**Command:** `/impeccable polish SleepPredictionCard safe-sleep toggle stateDescription`

**File:** `app/src/main/java/com/babytracker/ui/sleep/SleepPredictionCard.kt`

**Finding:** The safe-sleep toggle (lines 220-222) uses a `contentDescription` override that changes on expand/collapse — this works for TalkBack. However, Switch Access and Voice Access read `stateDescription` separately from the button name. Current code has no `stateDescription`, so those ATs cannot convey expanded/collapsed state independently.

**Fix:** Split the semantics into a stable name + dynamic state:

```kotlin
.semantics {
    contentDescription = "Safe sleep tip"
    stateDescription = if (safetyExpanded) "Expanded" else "Collapsed"
},
```

**WCAG:** 4.1.2 Name, Role, Value (AA) — minor gap, mostly affects non-TalkBack ATs

**Scope:** Lines 220-222 only. One-liner change.

---

## Systemic Note

Tasks 1 and 2 expose a codebase-wide pattern: `onXContainer.copy(alpha < 0.9f)` on text fails WCAG AA wherever the container is a saturated hue (pink, blue, green). After completing Tasks 1-2 for SleepPredictionCard, apply the same audit to:

- `SleepRecommendationSection.kt` — same `secondary`-on-`secondaryContainer` header pattern
- `HomeScreen.kt` — summary cards use `primaryContainer`/`secondaryContainer`/`tertiaryContainer` backgrounds with alpha-reduced text
- `BreastfeedingScreen.kt`, `PumpingScreen.kt` — check for alpha-reduced text on container surfaces

Consider defining explicit `onSecondaryContainerSubtle` / `onPrimaryContainerSubtle` tokens that are pre-verified to pass 4.5:1 on their respective containers, so the design system enforces contrast at the token level.
