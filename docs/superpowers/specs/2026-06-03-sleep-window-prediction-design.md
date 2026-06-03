# Sleep-Window Prediction Engine — Design Spec

**Date:** 2026-06-03
**Status:** Approved for planning (revised after adversarial review)
**Feature area:** `sleep`, `notification`, `db`, `usecase`, `ui`
**Linear project:** Improve Sleep Scheduler

---

## 1. Summary

Build a **sleep-window predictor** for infants 0–12 months. Using the baby's logged sleep, breastfeeding, and (later) cue data, predict the next *sleep opportunity window* — a start–end range plus a best-estimate time — annotated with confidence, plain-language reasons, and a safe-sleep prompt. When a window approaches, notify the parent, mirroring the existing predictive-feed reminder.

This is the BabyTracker counterpart to the feed-prediction feature. It is a wellness/planning aid, not a medical tool.

### Design philosophy (revised)

This spec was revised after an adversarial review. The original plan front-loaded a six-factor scoring engine plus a large data-model migration before any prediction shipped. The review's core objection stands: **the available data (parent-entered sleep start/end, breastfeed sessions, a few cue taps) cannot justify a high-precision scoring model up front, and the riskiest change — a DB migration — should not gate user value.**

The revised approach is **evidence-first and incremental**:

1. **Ship a working baseline predictor with zero schema changes first** (Phase 0). It reuses existing entities and the DataStore profile, delivers the full user-visible feature (prediction card + sleep-screen detail + notification), and is the thing we actually validate.
2. **Build an offline evaluation harness** (Phase 1) before adding any scoring sophistication.
3. **Add scoring factors one at a time, each gated by offline evaluation** showing it beats the baseline (Phase 2). No factor ships on faith.
4. **Introduce data-model changes only when a validated factor needs them** (Phase 3), and do them safely (schema-only migration, no DataStore read inside the migration, per-record timezone provenance, singleton profile — no premature multi-baby surface).
5. **Capture learning telemetry first; defer automatic adaptation** until matching/lifecycle semantics are proven (Phase 4).

### Goals

- Predict the next nap or bedtime as a **window**, not an exact time.
- Personalize from the baby's own logs, blended with age-based priors, **only as far as data quality supports**.
- Surface predictions on Home (compact card) and the Sleep screen (full detail), with honest empty/low-data/overdue states.
- Notify a configurable number of minutes before the window, respecting quiet hours, without spamming when overdue/stale.
- Communicate clearly when there is not yet enough quality data for a prediction.
- Lay groundwork to measure accuracy and improve it deliberately.

### Non-goals

- No machine learning.
- No automatic bias correction in the first cut (telemetry only; adaptation is a later, separately-justified step).
- No replacement of `GenerateSleepScheduleUseCase` (full-day plan). The two coexist with defined precedence (§3).
- No multi-baby product surface. A profile table, if/when added, is a singleton.
- No new remote/cloud integrations. All computation is local.
- No medical/diagnostic claims. Copy follows the research report's §17 guidelines.

---

## 2. Background & source material

Two research documents in `docs/sleep-research/` define the evidence base:

- `pediatric-sleep-science-baby-sleep-scheduling-algorithm.md` — wake-window tables, total-sleep targets, nap-transition staircase, circadian timeline, two-process model.
- `baby_sleep_schedule_algorithm_research_report_with_modeling_suggestions.md` — the engineering report: hybrid rules + scoring model, data-model recommendations (§5A/§5B), derived features (§6), age priors (§7), personalization (§8), candidate generation (§9), scoring (§10), output (§11), confidence rules (§21.6), Kotlin sketch (§21).

The research is the source of priors and the *target* architecture. This spec deliberately reaches that target incrementally rather than all at once.

### Existing code this builds on

- `GenerateSleepScheduleUseCase` — produces a full-day schedule (nap times, bedtime, wake windows, regression + nap-transition detection) from age + 7 days of records. Its age-prior tables (`getDefaultWakeWindows`, `getWakeWindowBounds`, `getBedtimeWindow`, `getTotalSleepRecommendation`, `getExpectedNapCount`, `detectRegression`) are extracted into a shared source and reused. The use case is **not** removed.
- Feed-prediction stack — the structural template to mirror:
  - `PredictNextFeedUseCase` → `Flow<FeedPrediction?>` (rolling interval average, min-sample gating, freshness horizon, quiet-hours filter, overdue grace).
  - `PredictiveFeedNotificationCoordinator` (combines prediction + settings, debounces, reconciles an AlarmManager reminder with a lead time and quiet-hours guard).
  - `PredictiveFeedScheduler` / `PredictiveFeedSchedulerImpl` (exact/inexact AlarmManager with SecurityException fallback).
  - `PredictiveFeedReceiver` + `PredictiveFeedNotificationHelper`.
  - Settings: `predictiveEnabled`, `predictiveLeadMinutes`, `quietHoursStartMinute`, `quietHoursEndMinute`.
  - `PredictionTuning` constants object.

The baseline predictor diverges from the feed template in two deliberate ways the review called out: it emits an **explicit state** rather than `null` on error/absence (§7.4), and it carries **richer non-window states** (overdue, stale, need-more-data, cue-led).

---

## 3. Relationship to the existing schedule use case

| | `GenerateSleepScheduleUseCase` (existing) | Sleep-window predictor (new) |
|---|---|---|
| Output | Whole-day projected plan (all naps + bedtime) | The single *next* sleep opportunity, right now |
| Trigger | User opens Sleep schedule screen | Continuous flow; drives a notification |
| Anchor | Desired/stored wake time | Actual last wake time from logs |
| Shape | List of clock times | Window + best estimate + confidence + reasons |

They share the **age-prior tables** (extracted to `SleepAgePriors`). Because they anchor differently (desired wake time vs. actual last wake), they *can* disagree after a missed or late nap — sharing priors does not prevent that. **Precedence is explicit:** once the day's actual logs diverge from the plan, the real-time predictor is authoritative. The Sleep screen frames the schedule as the "original day plan" and the predictor as the "next adjusted window."

---

## 4. Architecture

New domain sub-packages, following the research report's target architecture (§21) and the project's flat-package, no-Mapper conventions. Pure layers (`prior`, `feature`, `predictor`, later `scoring`/`eval`/`feedback`) contain **zero Android/framework imports** and are unit-tested with an injected `java.time.Clock` and `ZoneId`.

```
domain/
├── model/
│   ├── SleepPredictionState.kt   # sealed: Window | NeedMoreData | CueLed | CurrentlySleeping
│   │                             #         | AfterActiveFeed | Overdue | Unavailable
│   ├── SleepWindow.kt            # windowStart/End, bestEstimate, confidence, reasons, feedPrompt, safetyPrompt
│   ├── Confidence.kt             # LOW, MEDIUM, HIGH (computed multi-dimensionally — §6.3)
│   ├── SleepPredictionTuning.kt  # threshold/weight constants (sibling of PredictionTuning)
│   └── (SleepInterval, BreastfeedInterval, SleepMetrics, EvidenceQuality — feature models)
├── sleep/
│   ├── prior/        # SleepAgePriors — shared age-prior tables (extracted from GenerateSleepScheduleUseCase)
│   ├── feature/      # entity→interval conversion + metric/quality computation (pure)
│   ├── predictor/    # baseline predictor (Phase 0); scoring factors added incrementally (Phase 2)
│   └── eval/         # offline evaluation harness (Phase 1)
└── usecase/sleep/
    └── PredictSleepWindowUseCase.kt   # realtime Flow<SleepPredictionState>

manager/   PredictiveSleepScheduler (+Impl), PredictiveSleepNotificationCoordinator
receiver/  PredictiveSleepReceiver
util/      PredictiveSleepNotificationHelper (or extend NotificationHelper)
ui/
├── home/        prediction card (+ cue quick-tap row — Phase 3)
└── sleep/       recommendation section (window/reasons/confidence/safety + states)

# Phase 3+ only (added when a validated factor needs them):
data/local/
├── entity/   BabyProfileEntity (singleton), BabyEventEntity,
│             SleepRecommendationEntity, SleepRecommendationFeedbackEntity
└── dao/      BabyProfileDao, BabyEventDao, SleepRecommendationDao
```

---

## 5. Phase 0 — Baseline predictor (vertical slice, NO schema changes)

This phase delivers the entire user-visible feature using only existing data. It is what we validate before building anything more sophisticated.

### 5.1 Data sources (existing only)

- `SleepEntity` (sleep_records) and `BreastfeedingEntity` (breastfeeding_sessions) via existing repositories.
- Birth date from the existing DataStore profile (read in the use case, **not** in any migration).
- No new tables. No migration in this phase.

### 5.2 Shared age priors

Extract the age-prior tables from `GenerateSleepScheduleUseCase` into `domain/sleep/prior/SleepAgePriors`. Refactor `GenerateSleepScheduleUseCase` to delegate; behavior preserved (its existing tests are the guardrail). Both consumers now share one source of truth.

### 5.3 Feature extraction (`domain/sleep/feature`, pure)

Convert entities → validated `SleepInterval` / `BreastfeedInterval`, then compute:

- `lastWakeMillis`, `lastSleepType`, `lastSleepDurationMillis`.
- Completed **wake→sleep intervals** (gap between a sleep's end and the next sleep's start), filtered to plausible ranges.
- Rolling **median wake interval** and IQR over recent valid intervals.
- `sleepLast24hMillis` (interval overlap, cross-midnight safe), `daySleepTodayMillis`, `napCountToday`.
- `medianBedtimeMinuteOfDay`, `medianMorningWakeMinuteOfDay`.
- `EvidenceQuality` (§6.3 inputs): recent-last-wake recency, completed-interval count, interval IQR/variance, invalid-record rate, local-day coverage.

All day/night grouping uses an **injected `ZoneId`** with `ZonedDateTime`/`LocalDate` boundaries — never manual millisecond day buckets, never UTC. Phase 0 uses the device's current zone (the only signal available without schema); Phase 3 adds per-record timezone provenance and downgrades confidence for ambiguous legacy rows.

A completed interval is only counted when both endpoints are valid completed sleeps with sane durations (reject `end <= start`, naps > 4h, etc. per research §5A.15).

### 5.4 Baseline prediction

```
wakeTarget = blend(agePriorWakeMidpoint, recentMedianWakeInterval)   // see §6.2 for blend weights
bestEstimate = lastWake + wakeTarget
window = [bestEstimate - HALF_WINDOW, bestEstimate + HALF_WINDOW]      // HALF_WINDOW ≈ 15 min
```

No multi-factor scoring in this phase. The only personalization is the median-wake-interval blend, which is directly grounded in the baby's own completed intervals. Reasons are generated from observable facts ("awake 2h05", "previous nap shorter than usual", "based on recent wake patterns"). `safetyPrompt` always available for the detail surface (compact/collapsible on the card — §8). Feed is a **prompt only** (§5.6).

### 5.5 Prediction state (no `emit(null)`)

`PredictSleepWindowUseCase` returns `Flow<SleepPredictionState>` — a sealed type, never bare null. Precedence:

1. Open sleep record → `CurrentlySleeping`.
2. Corrected/chronological age < `CUE_LED_MAX_AGE_WEEKS` → `CueLed` (cue-led message, no clock window).
3. `EvidenceQuality` below threshold (§6.3) → `NeedMoreData(progress)` with a concrete "what's needed" hint.
4. Open feed → `AfterActiveFeed` (anchor after the feed; window generated from `max(now, feedEnd + buffer)`).
5. `now > windowEnd + OVERDUE_GRACE` → `Overdue` (low confidence, "watch for cues / next opportunity soon"); notifications suppressed.
6. Engine/parse/data error → `Unavailable(reason)` via `catchAndLog` (visible in logs and tests, recoverable in UI). **Not** silently null.
7. Otherwise → `Window(...)`.

Injected `Clock` + `ZoneId` for testability (consistent with `PredictNextFeedUseCase`).

### 5.6 Feed handling — prompt only

The predictor does **not** use predicted feed timing to move the sleep window (that would cascade one noisy predictor into another, and for breastfed infants feeding is often part of sleep onset). It only **annotates**: if a breastfeed looks due near the window, attach "a breastfeed may be due near this window — offer a feed first if hunger cues appear." Copy says "breastfeed" (only breastfeeding is tracked), per research §5A.12.

### 5.7 Notifications + settings (Phase 0)

Mirror the feed stack:

- `PredictiveSleepScheduler` (+Impl) — exact/inexact AlarmManager with `SecurityException` fallback.
- `PredictiveSleepNotificationCoordinator` (`@Singleton`, `@ApplicationScope`) — combines the prediction flow with `predictiveSleepEnabled`, `predictiveSleepLeadMinutes`, and shared quiet hours; debounces; reconciles the alarm at `bestEstimate − leadMinutes`. **Only `Window` states schedule**; `Overdue`/`NeedMoreData`/`CueLed`/`CurrentlySleeping`/`Unavailable` cancel any pending reminder. Cancels if past or inside quiet hours.
- `PredictiveSleepReceiver` (`@AndroidEntryPoint`) — fires via a sleep variant in `NotificationHelper` (Sleep Blue accent + sleep small icon). Started from `BabyTrackerApp`.
- New DataStore settings: `predictiveSleepEnabled` (default off), `predictiveSleepLeadMinutes` (default 15). Quiet hours reused. Settings screen gains a "Sleep reminders" section parallel to the feed one.

### 5.8 UI (Phase 0)

- **Home:** compact card mirroring the feed card ("Next sleep ~9:35 · 9:20–9:50") with a confidence hint; renders every `SleepPredictionState` (need-more-data with progress, overdue, cue-led, currently-sleeping).
- **Sleep screen:** a recommendation section — window, best estimate, confidence badge, reason list, feed prompt (if any), safe-sleep prompt — plus the "original day plan vs. next adjusted window" framing (§3).

No new navigation routes; additions to existing screens/ViewModels. Robolectric Compose tests per state.

**Phase 0 is shippable and is the unit of validation.**

---

## 6. Gating, personalization, confidence (evidence-quality based)

### 6.1 Why not bare "valid days"

The review correctly rejected `validLogDays >= 3` as the unlock: three days could be three night-sleeps and zero naps, or one sleep per day from a sparse logger — not enough to infer wake intervals. Gating is by **evidence quality**, not elapsed days.

### 6.2 Wake-interval personalization

```
recentIntervals = completed wake→sleep intervals over last LOOKBACK_DAYS, validity-filtered
babyWakeP50 = median(recentIntervals)
# weight shifts toward personal data as quality grows:
quality_c = clamp(completedIntervalCount / FULL_PERSONALIZATION_INTERVALS, 0..1)
wakeTarget = (1 - 0.6*quality_c) * agePriorMidpoint + (0.6*quality_c) * babyWakeP50
```

(Phase 0 stops here. Phase 2 may add separate nap-vs-bedtime targets and percentile bands if evaluation justifies them.)

### 6.3 Multi-dimensional confidence & gating

`Window` is only produced when `EvidenceQuality` passes a **composite** bar, not a day count:

- Recent last wake exists and is fresh (within `FRESHNESS_HORIZON`).
- At least `MIN_COMPLETED_INTERVALS` completed wake→sleep intervals across at least `MIN_LOCAL_DAYS` local days.
- Interval IQR below an instability ceiling (very erratic logs → lower confidence, not false HIGH).
- Low invalid/overlapping-record rate.
- No active disruption (Phase 3 cues: sick/teething/travel lower it).

Confidence is then computed from these same dimensions (recency, interval count, variance, invalid rate, age band, timezone provenance), **not** from elapsed logging days. Below the bar → `NeedMoreData` with a concrete hint ("log a few more naps with both sleep and wake times"). Under the cue-led age → `CueLed` regardless of data.

This directly answers the product requirement — predictions are withheld until there is enough *quality* signal, and the UI tells the parent what is missing.

---

## 7. Phase 1 — Offline evaluation harness

Before any scoring factor is added, build an internal harness (`domain/sleep/eval`, behind a debug entry point) that:

- Replays a baby's historical logs chronologically, asking the predictor for a window at each wake event using only prior data.
- Computes **MAE** between best estimate and actual next sleep start, and **% of actual starts inside the window**.
- Compares any candidate factor against the Phase 0 baseline on the same data (and on synthetic fixtures).

A factor is added in Phase 2 **only if** it beats the baseline on this harness. This is the gate the review demanded. The harness also doubles as a regression guard for the pure prediction layers.

---

## 8. Phase 2 — Incremental scoring factors (each eval-gated, each its own PR)

Move from the baseline toward the research scoring model (§9–§10) **one factor at a time**, each justified by Phase 1 evaluation:

- Personalized wake percentiles / separate nap-vs-bedtime targets.
- Sleep-debt adjustment (24h sleep vs. personalized target).
- Nap-budget / nap-count adjustment.
- Circadian bias (age-ramped; ≈0 under 6 weeks).
- Baby-history time-of-day similarity.

Candidate generation gains an explicit **overdue/stale** path: if `now > latest + grace`, do not emit "start now" repeatedly — emit `Overdue` and wait for a new anchor event. **Cues and feed remain annotations/confidence inputs, never candidate-shifting inputs**, until evaluation proves otherwise. The final weighted form (research §21.5) is only the *destination*; we arrive at it only for factors that earned their place.

Safe-sleep copy on the card is compact/collapsible to avoid habituation (full text on the detail surface and notification).

---

## 9. Phase 3 — Data-model changes (only when a validated factor needs them)

Introduced **after** Phase 2 shows a factor that requires persisted data. Done safely, per the review:

### 9.1 Singleton baby profile — schema-only migration, no DataStore dependency

```kotlin
@Entity(tableName = "babies")
data class BabyProfileEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,        // fixed singleton key; no multi-baby surface
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: Long? = null,   // NULLABLE — migration cannot read DataStore
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,
    @ColumnInfo(name = "due_date_user_provided") val dueDateUserProvided: Boolean = false,
    @ColumnInfo(name = "home_timezone_id") val homeTimezoneId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

- The Room migration is **schema-only**: it creates the table (and any new nullable columns) with no value backfill. Room never reads DataStore.
- An **app-level bootstrap use case** runs after startup, when both Room and DataStore are readable, and copies `dateOfBirth`/`dueDate`/home timezone into the singleton row transactionally. Prediction stays gated (`NeedMoreData`) until bootstrap completes.
- **No `babyId` propagation** into `sleep_records` / `breastfeeding_sessions` in this project. The app is single-baby; adding `baby_id` everywhere implies a multi-baby product (selection, deletion, sharing, backup) that does not exist. If multi-baby is ever wanted, it is a separate, explicit project.

### 9.2 Per-record timezone provenance

New records store the zone they were created in. Legacy rows have unknown provenance → grouped with the current zone as a best-effort fallback **with lowered confidence**. All boundaries use `ZonedDateTime`; explicit DST (spring-forward/fall-back) and travel tests are required.

### 9.3 Corrected age — optional, provenance-aware

`dueDate` drives corrected age only when user-provided and plausible; it is a user-visible, disableable setting. Missing/suspect → chronological age + lower confidence for very young infants. (App scope is 0–12 months; corrected-age handling is conservative.)

### 9.4 Cue/event model (cues annotate only)

```kotlin
@Entity(tableName = "baby_events",
        indices = [Index("timestamp"), Index("event_type")])
data class BabyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "event_type") val eventType: String,   // BabyEventType.name
    @ColumnInfo(name = "intensity") val intensity: Int? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
enum class BabyEventType { SLEEPY_CUE, HUNGER_CUE, FUSSY, SICK, TEETHING, TRAVEL }
```

Home quick-tap row: `[😪 Sleepy] [😣 Fussy] [🤒 Sick] [🦷 Teething] [✈️ Travel]`. Cues **annotate reasons and lower confidence during disruptions** (sick/teething/travel); they do **not** shift candidate generation (cue logging is biased toward times parents already suspect sleep — using it to move the window creates a confirmation loop). Stable enum names persisted; UI labels/emoji are extension properties.

### 9.5 Stable enum persistence

Persist `SleepType.name`; harden the converter with a safe fallback + legacy-label normalization. Any migration that touches `sleep_records` normalizes legacy label rows. Schema-hash identity preserved (additive only). Migration tests cover missing/corrupt DataStore, incomplete onboarding, legacy labels, and timezone-changed-since-creation.

---

## 10. Phase 4 — Learning loop (telemetry-first; automatic adaptation deferred)

### 10.1 Capture telemetry with explicit identity & lifecycle

```kotlin
@Entity(tableName = "sleep_recommendations",
        indices = [Index("generated_at"), Index("anchor_sleep_id"), Index("recommendation_type")])
data class SleepRecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "anchor_sleep_id") val anchorSleepId: Long,     // identity: the wake we anchored on
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
    @ColumnInfo(name = "recommendation_type") val recommendationType: String,
    @ColumnInfo(name = "window_start") val windowStart: Long,
    @ColumnInfo(name = "window_end") val windowEnd: Long,
    @ColumnInfo(name = "best_estimate") val bestEstimate: Long,
    @ColumnInfo(name = "confidence") val confidence: String,
    @ColumnInfo(name = "lifecycle") val lifecycle: String,    // GENERATED, DISPLAYED, SCHEDULED, FIRED, SUPERSEDED
    @ColumnInfo(name = "algorithm_version") val algorithmVersion: String
)

@Entity(tableName = "sleep_recommendation_feedback",
        indices = [Index("recommendation_id"), Index("actual_sleep_record_id")])
data class SleepRecommendationFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recommendation_id") val recommendationId: Long,
    @ColumnInfo(name = "actual_sleep_record_id") val actualSleepRecordId: Long? = null,
    @ColumnInfo(name = "error_minutes") val errorMinutes: Int? = null,   // null when no sleep occurred
    @ColumnInfo(name = "outcome") val outcome: String,   // ACTED_IN_WINDOW, ACTED_OUTSIDE, NO_SLEEP, DISMISSED, QUIET_HOURS_SUPPRESSED, SUPERSEDED
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

Key fixes from the review:

- **Deduplicated identity:** one stable recommendation per wake anchor (`anchorSleepId` + type + algorithm version), persisted only after debounce — not one per Flow recomputation.
- **Full lifecycle**, including recommendations that were displayed but not scheduled (quiet hours), and **negative outcomes** (no sleep within N minutes, dismissed, superseded). Feedback is not limited to "scheduled" recommendations, so it is not selection-biased.
- **Matching:** one recommendation per actual sleep, via the anchor relationship and lifecycle state — not "nearest recent."

### 10.2 Automatic bias correction — deferred and constrained

No automatic adjustment ships with telemetry. Bias correction is a **later, separately-justified step**, enabled only after the telemetry validates that matching is reliable and the harness shows it helps. When/if enabled it must:

- Be scoped to a homogeneous segment (same recommendation type, age band, nap ordinal / bedtime, disruption-free).
- Use **shrinkage toward zero** by sample count: `bias = medianError * min(n / SHRINK_N, 1)`, with a conservative cap (well under the ±30 min the review flagged as dangerous for young infants).
- Be **disabled during disruptions** and when feedback is dominated by parent-compliance signals.
- Account for the feedback loop (recommendations change behavior); convergence is monitored on the harness, not assumed.

---

## 11. Tuning constants

`SleepPredictionTuning` (sibling of `PredictionTuning`), seeded from research §19 but treated as defaults to be validated:

```
HALF_WINDOW_MINUTES = 15            # 30-minute window
CANDIDATE_STEP_MINUTES = 5          # Phase 2+
MIN_COMPLETED_INTERVALS = 5         # quality gate, not day count
MIN_LOCAL_DAYS = 3
FULL_PERSONALIZATION_INTERVALS = 14
FRESHNESS_HORIZON_HOURS = 12
OVERDUE_GRACE_MINUTES = 45
CUE_LED_MAX_AGE_WEEKS = 6
LOOKBACK_DAYS = 14
SHRINK_N = 10                       # Phase 4 bias shrinkage (when/if enabled)
MAX_BIAS_MINUTES = 15               # conservative cap (Phase 4)
ALGORITHM_VERSION = "sleep-pred-baseline-1"
```

---

## 12. Decomposition into Linear issues (one PR each)

Phases are sequential; **user value ships at the end of Phase 0** (issues 1–5). Later phases are gated by evaluation and need.

**Phase 0 — baseline predictor (shippable vertical slice, NO schema changes)**
1. **AKA-### Extract `SleepAgePriors`; refactor `GenerateSleepScheduleUseCase` to delegate.** Pure, behavior-preserving (existing tests guard).
2. **AKA-### Feature-extraction layer** (`SleepInterval`/`BreastfeedInterval`/`SleepMetrics`/`EvidenceQuality`) — local-tz day logic via injected `ZoneId`, overlap, validity filters. Pure, exhaustively tested.
3. **AKA-### Baseline `PredictSleepWindowUseCase`** → `Flow<SleepPredictionState>` (sealed states, evidence-quality gating §6.3, median-wake blend §6.2, feed-as-prompt §5.6, explicit `Unavailable`).
4. **AKA-### Predictive-sleep notification stack + settings + Settings UI + startup wiring.** Schedules only on `Window`; overdue/quiet-hours/past suppression.
5. **AKA-### Home prediction card + Sleep-screen section** for all states (window / need-more-data+progress / overdue / cue-led / currently-sleeping / after-feed), plus plan-vs-next-window framing.

**Phase 1 — evaluation**
6. **AKA-### Offline evaluation harness** (`domain/sleep/eval`): chronological replay, MAE + in-window %, baseline vs. candidate comparison, synthetic fixtures. Internal/debug entry point.

**Phase 2 — incremental scoring factors (each PR gated by issue 6 results)**
7. **AKA-### Personalized wake percentiles / nap-vs-bedtime targets** (if it beats baseline).
8. **AKA-### Sleep-debt + nap-budget adjustments** (each justified; may split into two PRs).
9. **AKA-### Circadian + history factors** (age-ramped; each justified; may split). Adds explicit overdue/stale candidate handling.

**Phase 3 — data model (only when a Phase 2 factor needs persistence)**
10. **AKA-### Singleton `BabyProfileEntity` (schema-only migration) + app-level DataStore→Room bootstrap + per-record timezone provenance + stable-enum hardening.** Migration-failure + DST/travel tests. No `babyId` propagation.
11. **AKA-### `baby_events` + Home quick-tap cue UI** (cues annotate/confidence only).

**Phase 4 — learning (telemetry-first)**
12. **AKA-### Recommendation + feedback tables** with deterministic identity, full lifecycle, and negative outcomes — **telemetry only, no automatic bias correction.** (A later, separate issue may add constrained bias correction once telemetry + harness justify it.)

Each issue: own branch, adversarial review before commit, dedicated conventional commit (`feat(sleep|db|notification|usecase|ui): …`).

---

## 13. Testing strategy

- **Pure domain** (prior, feature, predictor, eval, later scoring/feedback): JUnit 5 + MockK + fixed `Clock`/`ZoneId`. Cover age bands, median-wake blend, evidence-quality gating, every `SleepPredictionState` branch (including overdue, after-feed, currently-sleeping, cue-led, unavailable), cross-midnight overlap, and copy-safety (extend the `PredictionCopyTest` style — no forbidden phrases).
- **Sparse-logger & noisy-data tests:** one sleep/day, only nights, only naps, stale last wake, edited records, overlapping records → assert `NeedMoreData`/low confidence rather than false windows.
- **DST & travel tests:** spring-forward, fall-back, device-timezone change between record creation and prediction.
- **Evaluation harness** doubles as a regression guard (baseline MAE/in-window % on fixtures must not regress).
- **Migration (Phase 3):** Room migration tests for schema-only creation, then bootstrap-use-case tests for missing/corrupt DataStore, incomplete onboarding, legacy-label normalization, timezone-changed-since-creation. Room never reads DataStore.
- **Notification:** coordinator unit tests (enabled/disabled, lead, quiet hours, only-Window-schedules, overdue-cancels, past-trigger cancel) mirroring `PredictiveFeedNotificationCoordinatorTest`; receiver instrumentation test mirroring `PredictiveFeedReceiverTest`.
- **Feedback (Phase 4):** matching/lifecycle tests — multiple recomputations in one wake period dedupe to one record; quiet-hours-suppressed and dismissed and no-sleep outcomes captured; sleeps outside the window classified correctly.
- **UI:** Robolectric Compose tests for each card/section state, mirroring `HomeScreenPredictionTest` / `SettingsScreenPredictionTest`.
- Architecture tests use the shared `productionScope` and `@Tag("architecture")`; fast loop (`-PfastTests`) during dev, full suite before each PR.

---

## 14. Risks & mitigations

- **Over-precision on weak data** → baseline-first + offline evaluation gate every factor; confidence reflects data quality, not elapsed days; copy avoids prescriptive/medical phrasing.
- **Migration on real user data** → deferred to Phase 3, schema-only, no DataStore read in the migration, app-level transactional bootstrap, prediction gated until bootstrap completes; comprehensive failure tests.
- **Timezone / DST correctness** → injected `ZoneId` + `ZonedDateTime` boundaries; per-record provenance (Phase 3); explicit DST/travel tests; legacy rows lower confidence.
- **Feedback that learns the parent, not the baby** → telemetry-first; negative outcomes captured; automatic bias correction deferred, scoped, shrunk, disruption-disabled, and convergence-monitored when/if enabled.
- **Notification spam when overdue** → explicit `Overdue` state; only `Window` schedules; suppress until a new anchor event.
- **Silent failures** → explicit `Unavailable(reason)` + `catchAndLog`, never `emit(null)`.
- **Scope creep into multi-baby** → singleton profile, no `babyId` propagation; multi-baby is a separate explicit project.
- **Two schedule features contradicting** → defined precedence (§3); real-time window supersedes the day plan after divergence.

---

## 15. Deferred / out of scope

- Multi-baby support (singleton profile only here).
- Generic `FeedingSessionEntity` (bottle/formula/solids) — engine reads breastfeeding only and labels accordingly (research §5A.12, §5B.6).
- Sleep-onset-latency capture (put-down vs. asleep) for richer feedback (research §5B.5).
- Automatic bias correction (telemetry captured in Phase 4; adaptation is a later, separately-justified step — §10.2).
- The full six-factor weighted model as a single deliverable — reached incrementally and only for factors that beat the baseline.
