# Sleep Prediction — Confidence-Scaled Personalization Rebalance — Design

**Date:** 2026-06-16
**Linear project:** [Improve Sleep Scheduler](https://linear.app/akachan/project/improve-sleep-scheduler-1cbef68a63e8) (`82ea85cb-b127-46c8-8636-ab90ff562abc`, team AKA)
**Algorithm baseline:** `sleep-pred-phase5-later-shift-stale-1`

---

## Problem

In day-to-day use the predicted sleep window lands **too far ahead in time** — later than the baby actually falls asleep. The user's read: the prediction should be driven primarily by the **naps/sleep the parent registers**, age should remain a heavy influence, and the automatic heuristic adjustments ("smart factors") should still help but carry **less weight than today**.

### Root causes (verified in code)

1. **Personalization is capped at 60%.** `SleepWindowPredictor.buildWindow` blends:
   ```
   wakeTarget = (1 − W·c)·priorMidpoint + W·c·babyP50
   ```
   with `W = MAX_PERSONALIZATION_WEIGHT = 0.6` and `c = qualityC = typeIntervalCount / FULL_PERSONALIZATION_INTERVALS (14)`, clamped to `[0,1]`. Even at full history (`c = 1`) the age prior keeps **≥ 40%** weight. The age-prior midpoints are textbook *upper-leaning* values (`SleepAgePriors.getNapWakeWindowMidpoint`, `getPreBedtimeWakeWindowMidpoint`), so a baby with shorter real wake windows is dragged later. **Prime cause.**

2. **Slow personalization ramp.** `c` only reaches 1.0 at 14 type-specific intervals. Bedtime (≈1/day) needs ~2 weeks to personalize at all.

3. **Smart factors fire at full strength regardless of how well the baby is known.** `circadian + sleep-debt + nap-budget` shift the center up to `MAX_TOTAL_FACTOR_SHIFT_MINUTES = 45`. The circadian factor pulls night windows toward a textbook bedtime slot, which can push them *later*. These are population heuristics, yet they keep full weight even for a data-rich baby whose own logged median already encodes the same circadian/debt/nap structure.

### Scientific grounding

Wake windows vary substantially **between babies and day-to-day**; published charts are explicitly ranges, not targets ([Sleep Foundation](https://www.sleepfoundation.org/baby-sleep/newborn-wake-windows), [Cleveland Clinic](https://health.clevelandclinic.org/wake-windows-by-age)). Circadian rhythm emerges ~6–12 wk and matures ~3–4 mo ([Mother-Infant Circadian Rhythm, PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC4312214/)). High inter-individual variability is the scientific justification for trusting a baby's own logged history over population priors once enough history exists — and for keeping age as the anchor when it does not.

---

## Principle

A single confidence signal `c` (the existing `qualityC`) governs trust. As `c → 1`:
- the baby's own median **displaces** the age prior (existing blend, rebalanced), **and**
- the smart factors **fade** toward a floor.

Invariant: at `c = 0` (no logged history) the prediction is 100% age prior + full-strength factors. **Age stays heavy exactly when data is sparse.** This satisfies both "logged data should drive more" and "age should still influence heavily" without contradiction — they apply at opposite ends of the data-richness axis.

---

## Components

### Component 1 — Blend reweighting (logged data drives more)

**File:** `domain/model/SleepPredictionTuning.kt`, consumed by `SleepWindowPredictor.buildWindow`.

- Raise `MAX_PERSONALIZATION_WEIGHT` and lower `FULL_PERSONALIZATION_INTERVALS` so logged data reaches dominance higher and sooner.
- **Final values chosen by eval sweep, not by hand.** Candidate grid:
  - `MAX_PERSONALIZATION_WEIGHT ∈ {0.6, 0.7, 0.8, 0.9}`
  - `FULL_PERSONALIZATION_INTERVALS ∈ {8, 10, 12, 14}`
- Selection rule: minimize short-wake-cohort MAE subject to **no regression** on typical/long cohorts (see §7.1 below).
- Keep the `c = 0 ⇒ 100% prior` invariant. Keep the `MAX_PERSONALIZATION_WEIGHT` shrinkage comment accurate after the value changes.

### Component 2 — Confidence-decay on smart factors (downweight the "cues")

**Files:** `SleepWindowPredictor.buildWindow` (apply), `SleepPredictionTuning.kt` (constants).

Today: `adjustedBestEstimate = bestEstimate + clampTotalShift(Σ factor.adjustment)`.

Introduce a decay weight tied to `c`:
```
factorWeight(c) = FACTOR_FLOOR + (1 − FACTOR_FLOOR)·(1 − c)
totalShift = clampTotalShift(Σ factor.adjustment) · factorWeight(c)
```
- `c = 0` → `factorWeight = 1.0` (factors full strength — nothing else to lean on).
- `c = 1` → `factorWeight = FACTOR_FLOOR` (factors mostly faded — baby's own pattern dominates).
- `FACTOR_FLOOR > 0` keeps a real sleep-debt / nap-deficit day still nudging.
- Re-clamp to `MAX_TOTAL_FACTOR_SHIFT_MINUTES` after scaling so the ceiling still holds.

Eval-swept constants:
- `FACTOR_FLOOR ∈ {0.0, 0.25, 0.3, 0.5}`
- `MAX_TOTAL_FACTOR_SHIFT_MINUTES ∈ {45, 30, 25}` (and, if the sweep favors it, proportional reductions to the per-factor caps `CIRCADIAN_MAX_SHIFT_MINUTES`, `SLEEP_DEBT_MAX_SHIFT_MINUTES`, `NAP_BUDGET_MAX_SHIFT_MINUTES`).

This is the literal "cues improve but with less weight than now": full help when the baby is unknown, fading help as the logged history grows.

### Component 3 — Eval foundation (build FIRST; gate for 1 & 2)

**Files:** `domain/sleep/eval/` test sources; new synthetic cohort generator + parametric sweep test alongside the existing `*EvalComparisonTest` files.

- **Cohort generator** producing deterministic synthetic logs for three babies, each with enough completed intervals + local-day coverage to pass the quality gate and personalize:
  - **short-wake** — logged wake intervals consistently *below* the age-prior midpoint (reproduces the complaint; e.g. prior ≈ 82 min, actual ≈ 55 min), stable.
  - **typical** — wake intervals centered on the age-prior midpoint (regression guard).
  - **long-wake** — wake intervals *above* the prior (guards the opposite-direction regression).
- **Parametric sweep harness:** runs `SleepEvalHarness` over the candidate grids from Components 1 & 2 and reports MAE / in-window% / missed-rate per age×type segment for each candidate.
- This turns "I feel the window is too far ahead" into a **failing, measurable test**; Components 1 & 2 are "done" when it passes.

### Component 4 — (DEFERRED, conditional) prior + circadian calibration

Created as a Linear issue but **only executed if** residual late-bias remains on the *typical* cohort after Components 1 & 2 (i.e. the bias is in the priors themselves, not just their weight). Scope if triggered:
- Nudge `getNapWakeWindowMidpoint` / `getPreBedtimeWakeWindowMidpoint` off their max-leaning values toward the age-range median.
- Make the circadian factor pull *earlier* more freely than *later* (asymmetric), since the reported symptom is one-directional (too late).
- Eval-gated under the same §7.1 criteria.

### Wrap-up (folded into the final component that ships a behavior change)

- Bump `ALGORITHM_VERSION` (referenced by `EvalReport` and `DomainToSnapshot`).
- Lock in the eval-chosen constants with a comment citing the sweep result.
- Update any now-inaccurate `buildReasons` / comment text.

---

## §7.1 Acceptance criteria (per the project's established gate, AKA-90)

A predictor change ships only if, on the synthetic cohorts:
- **short-wake cohort:** MAE improves by ≥ `EVAL_MIN_MAE_GAIN_MIN` (5 min) vs the current algorithm.
- **typical & long-wake cohorts:** no MAE regression beyond `EVAL_MAX_REGRESSION` (0), missed-window-rate within `EVAL_ADVERSE_COHORT_MISSED_RATE_CAP` (0.20).
- All existing `SleepWindowPredictorTest`, `*FactorTest`, and `*EvalComparisonTest` suites stay green.

---

## Architecture notes / isolation

- All math stays in the pure `domain/sleep/` layer — zero framework imports, `Clock`/`ZoneId` injected (matches AKA-87/88 conventions).
- `factorWeight(c)` is a private pure function in `SleepWindowPredictor`; `c` is already computed (`qualityC`) — **no new state, no new persisted data, no new DB migration.**
- Each component is independently evaluable and independently revertible.

## Non-goals

- No EWMA / recency-weighted model or per-time-of-day personal windows (Approach C — possible later phase, not this work).
- No new user input, no new cue semantics, no UI changes.
- No new analytics/remote integrations.
- No change to the `< CUE_LED_MAX_AGE_WEEKS` cue-led behavior or the quality/staleness gates.

---

## Linear issue breakdown (project: Improve Sleep Scheduler)

| # | Issue | Depends on | Notes |
|---|-------|-----------|-------|
| 1 | Eval foundation: short/typical/long-wake synthetic cohorts + parametric sweep harness | — | Build first; the gate for 2–4 |
| 2 | Blend reweighting: eval-swept `MAX_PERSONALIZATION_WEIGHT` + `FULL_PERSONALIZATION_INTERVALS` | #1 | §7.1 gate |
| 3 | Factor confidence-decay: `factorWeight(c)` + `FACTOR_FLOOR` + cap sweep | #1 | §7.1 gate; "downweight cues" |
| 4 | (Deferred/conditional) prior + circadian calibration | #1, #2, #3 | Only if residual late-bias remains |
| 5 | Tuning finalize + `ALGORITHM_VERSION` bump + reason/comment/docs cleanup | #2, #3 | Lock eval-chosen constants |
