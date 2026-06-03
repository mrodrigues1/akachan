# Sleep-Window Prediction Engine — Design Spec

**Date:** 2026-06-03
**Status:** Approved for planning
**Feature area:** `sleep`, `notification`, `db`, `usecase`, `ui`
**Linear project:** Improve Sleep Scheduler

---

## 1. Summary

Build a real-time **sleep-window predictor** for infants 0–12 months. Using the baby's logged sleep, breastfeeding, and (new) cue/event data, the engine predicts the next *sleep opportunity window* — a start–end range plus a best-estimate time — annotated with a confidence level, plain-language reasons, and safe-sleep / feed prompts. When a window is approaching, the parent receives a notification, mirroring the existing predictive-feed reminder.

This is the BabyTracker counterpart to the existing feed-prediction feature, but backed by a research-grade scoring engine derived from the two evidence reports in `docs/sleep-research/`. It is a wellness/planning aid, not a medical tool.

### Goals

- Predict the next nap or bedtime as a **window**, not an exact time (research is explicit on this).
- Personalize from the baby's own logs using rolling medians and percentiles, blended with age-based priors.
- Surface predictions on the Home screen (compact card) and the Sleep schedule screen (full detail).
- Notify the parent a configurable number of minutes before the predicted window, respecting quiet hours.
- Clearly communicate that **some logged data is required before predictions appear**, and degrade gracefully when data is thin or the baby is very young.
- Capture prediction outcomes and apply a deterministic bias correction so accuracy improves over time.

### Non-goals

- No machine-learning model. Adaptation is a deterministic rolling-bias correction.
- No replacement of the existing `GenerateSleepScheduleUseCase` (full-day plan). The two coexist (see §3).
- No new remote/cloud integrations. All computation is local (the optional partner-sharing feature is untouched).
- No medical/diagnostic claims. Copy follows the research report's guidelines (§17 of the research report).

---

## 2. Background & source material

Two research documents in `docs/sleep-research/` define the evidence base:

- `pediatric-sleep-science-baby-sleep-scheduling-algorithm.md` — wake-window tables, total-sleep targets, nap-transition staircase, circadian timeline, two-process model.
- `baby_sleep_schedule_algorithm_research_report_with_modeling_suggestions.md` — the engineering report: hybrid rules + scoring model, data-model recommendations (§5A/§5B), derived features (§6), age priors (§7), personalization (§8), candidate generation (§9), scoring (§10), output structure (§11), confidence rules (§21.6), and a Kotlin implementation sketch (§21).

Section references below point into that engineering report unless stated otherwise.

### Existing code this builds on

- `GenerateSleepScheduleUseCase` — already produces a full-day schedule (nap times, bedtime, wake windows, regression + nap-transition detection) from age + 7 days of records. Its age-prior tables (`getDefaultWakeWindows`, `getWakeWindowBounds`, `getBedtimeWindow`, `getTotalSleepRecommendation`, `getExpectedNapCount`, `detectRegression`) are reused by the new engine. This use case is **not** removed.
- Feed-prediction stack — the structural template to mirror:
  - `PredictNextFeedUseCase` → `Flow<FeedPrediction?>` (rolling interval average, min-sample gating, freshness horizon, quiet-hours filter, overdue grace).
  - `PredictiveFeedNotificationCoordinator` (combines prediction + settings, debounces, reconciles an AlarmManager reminder with a lead time and quiet-hours guard).
  - `PredictiveFeedScheduler` / `PredictiveFeedSchedulerImpl` (exact/inexact AlarmManager with SecurityException fallback).
  - `PredictiveFeedReceiver` + `PredictiveFeedNotificationHelper`.
  - Settings: `predictiveEnabled`, `predictiveLeadMinutes`, `quietHoursStartMinute`, `quietHoursEndMinute`.
  - `PredictionTuning` constants object.

---

## 3. Relationship to the existing schedule use case

| | `GenerateSleepScheduleUseCase` (existing) | Sleep-window predictor (new) |
|---|---|---|
| Output | Whole-day projected plan (all naps + bedtime) | The single *next* sleep opportunity, right now |
| Trigger | User opens Sleep schedule screen | Continuous flow; drives a notification |
| Anchor | Desired/stored wake time | Actual last wake time from logs |
| Shape | List of clock times | Window + best estimate + confidence + reasons |

They share the **age-prior tables**. To avoid duplication, the prior tables move into a shared `domain/sleep/prior` source (e.g. `SleepAgePriors`) that both consumers call. `GenerateSleepScheduleUseCase` is refactored to delegate to it; behavior is preserved (covered by its existing tests).

---

## 4. Architecture

New domain sub-packages, following the research report's target architecture (§21) and the project's existing flat-package, no-Mapper conventions:

```
domain/
├── model/
│   ├── SleepRecommendation.kt        # engine output (window, estimate, confidence, reasons, prompts)
│   ├── RecommendationType.kt         # NAP, BEDTIME, OPTIONAL_CATNAP, NO_SLEEP_YET,
│   │                                 #   CURRENTLY_SLEEPING, AFTER_ACTIVE_FEED, NEED_MORE_DATA
│   ├── Confidence.kt                 # LOW, MEDIUM, HIGH
│   ├── SleepPredictionTuning.kt      # threshold/weight constants (sibling of PredictionTuning)
│   ├── BabyEvent.kt + BabyEventType.kt
│   └── (SleepInterval, BreastfeedInterval, SleepMetrics, BreastfeedingMetrics — feature models)
├── sleep/
│   ├── prior/        # SleepAgePriors — shared age-prior tables (extracted from GenerateSleepScheduleUseCase)
│   ├── feature/      # entity→interval conversion + metric computation (pure)
│   ├── personalization/  # Bayesian target + wake-percentile blend
│   ├── recommendation/   # candidate generation + scoring + reason/confidence builders
│   └── feedback/     # bias-correction computation
└── usecase/sleep/
    └── PredictSleepWindowUseCase.kt  # realtime Flow<SleepRecommendation?>

data/local/
├── entity/   BabyProfileEntity, BabyEventEntity, SleepRecommendationEntity,
│             SleepRecommendationFeedbackEntity  (+ babyId/timestamps on existing entities)
└── dao/      BabyProfileDao, BabyEventDao, SleepRecommendationDao

manager/   PredictiveSleepScheduler (+Impl), PredictiveSleepNotificationCoordinator
receiver/  PredictiveSleepReceiver
util/      PredictiveSleepNotificationHelper (or extend NotificationHelper)
ui/
├── home/        prediction card + cue quick-tap row
└── sleep/       recommendation section (window/reasons/confidence/safety)
```

**Layering rule (research §5B.15):** Room entities = stored facts; domain feature/recommendation models = validated intervals + derived signals; recommendation entities = algorithm output + explanations; UI models = labels/emoji/copy. The `feature`, `personalization`, `recommendation`, and `feedback` packages contain **pure Kotlin with zero Android/framework imports** and are unit-tested with a fixed `java.time.Clock`.

---

## 5. Data model changes

### 5.1 `BabyProfileEntity` (new table `babies`)

The engine must be age-aware and timezone-aware. Today the baby's birth date lives in DataStore and there is no baby table.

```kotlin
@Entity(tableName = "babies")
data class BabyProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String? = null,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: Long,      // epoch ms
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,        // epoch ms; corrected age for preemies
    @ColumnInfo(name = "timezone_id") val timezoneId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

`dueDate` enables **corrected age** for premature infants (use corrected age for priors until ~24 months). `timezoneId` is used for all local-day / bedtime / morning-wake grouping — never UTC boundaries (research §5A.4).

### 5.2 `babyId` + audit timestamps on existing entities

Add to both `sleep_records` and `breastfeeding_sessions`:

```kotlin
@ColumnInfo(name = "baby_id", index = true) val babyId: Long,
@ColumnInfo(name = "created_at") val createdAt: Long,
@ColumnInfo(name = "updated_at") val updatedAt: Long
```

Indices on `baby_id`, `start_time`, `end_time`.

### 5.3 Stable enum persistence

Persist `SleepType.name` (`NAP`, `NIGHT_SLEEP`), never the localized label/emoji. Harden the converter to `runCatching { SleepType.valueOf(value) }` with a safe fallback and a defensive label-parsing path for any legacy rows. UI label/emoji become extension properties outside persistence (research §5B.3).

### 5.4 `baby_events` (new table)

```kotlin
@Entity(
    tableName = "baby_events",
    indices = [Index("baby_id"), Index("timestamp"), Index("event_type")]
)
data class BabyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "baby_id") val babyId: Long,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "event_type") val eventType: String,   // BabyEventType.name
    @ColumnInfo(name = "intensity") val intensity: Int? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

enum class BabyEventType { SLEEPY_CUE, HUNGER_CUE, FUSSY, SICK, TEETHING, TRAVEL }
```

MVP quick-tap set: sleepy, fussy, sick, teething, travel (hunger cue available to the model; surfaced minimally). `SICK`/`TEETHING`/`TRAVEL` are *disruption* flags that lower confidence.

### 5.5 Learning-loop tables

```kotlin
@Entity(
    tableName = "sleep_recommendations",
    indices = [Index("baby_id"), Index("generated_at"), Index("recommendation_type")]
)
data class SleepRecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "baby_id") val babyId: Long,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
    @ColumnInfo(name = "recommendation_type") val recommendationType: String,
    @ColumnInfo(name = "window_start") val windowStart: Long,
    @ColumnInfo(name = "window_end") val windowEnd: Long,
    @ColumnInfo(name = "best_estimate") val bestEstimate: Long,
    @ColumnInfo(name = "confidence") val confidence: String,
    @ColumnInfo(name = "score") val score: Double,
    @ColumnInfo(name = "algorithm_version") val algorithmVersion: String
)

@Entity(
    tableName = "sleep_recommendation_feedback",
    indices = [Index("recommendation_id"), Index("actual_sleep_record_id")]
)
data class SleepRecommendationFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recommendation_id") val recommendationId: Long,
    @ColumnInfo(name = "actual_sleep_record_id") val actualSleepRecordId: Long? = null,
    @ColumnInfo(name = "error_minutes") val errorMinutes: Int,        // actualStart - bestEstimate
    @ColumnInfo(name = "user_action") val userAction: String,        // RecommendationUserAction.name
    @ColumnInfo(name = "created_at") val createdAt: Long
)

enum class RecommendationUserAction { ACCEPTED, DISMISSED, IGNORED, STARTED_SLEEP_TIMER, EDITED_WINDOW }
```

### 5.6 Migration

Room **v2 → v3** single migration:

1. Create `babies`; insert one default baby row backfilled from the DataStore birth date (and due date if present) with `timezoneId = ZoneId.systemDefault()`.
2. Add `baby_id`, `created_at`, `updated_at` to `sleep_records` and `breastfeeding_sessions`; set `baby_id` to the default baby; set timestamps to `start_time` (best available) for existing rows; create indices.
3. Create `baby_events`, `sleep_recommendations`, `sleep_recommendation_feedback`.
4. Normalize any `sleep_type` rows storing label text to enum names.

Covered by Room migration tests. `room_master_table` schema-hash identity must remain valid (only additive changes + data backfill).

---

## 6. Feature-extraction layer (`domain/sleep/feature`)

Pure functions converting repository data into derived signals (research §6, §21).

**Domain intervals** (`SleepInterval`, `BreastfeedInterval`) — validated copies of entities with `durationMillis` helpers; invalid rows (`end <= start`, absurd durations) dropped or flagged.

**`SleepMetrics`:** `lastWakeMillis`, `lastSleepDurationMillis`, `sleepLast24hMillis`, `sleepLast7dAvgMillis`, `daySleepTodayMillis`, `nightSleepLastNightMillis`, `napCountToday`, `medianNapDurationMillis`, `medianBedtimeMinuteOfDay`, `medianMorningWakeMinuteOfDay`, `validLogDays`, plus successful-wake-interval percentiles (p25/p50/p75) for personalization.

**`BreastfeedingMetrics`:** `lastFeedStart/End`, `activeFeedInProgress`, `medianStartToStartIntervalMillis`, `minutesSinceFeedStart/End`, `nextFeedDueEstimateMillis`.

**`CueScores`:** recent sleepy/hunger-cue scores + active disruption flags from `baby_events`.

Critical rules:
- **Local timezone** for all day/night grouping (from `BabyProfileEntity.timezoneId`).
- **Interval overlap** for "last 24h" and cross-midnight night sleep (don't undercount; research §5A.9).
- **Corrected age** when `dueDate` is set.
- Validation thresholds per research §5A.15 (reject negative durations; flag naps > 4h, very short nights, overlaps) → lower confidence rather than hard-fail.

A **valid log day** = a local day containing ≥1 completed sleep record (nap or night) with a sane duration. `validLogDays` drives gating and confidence.

---

## 7. Personalization (`domain/sleep/personalization`, research §8)

**Target sleep (Bayesian blend):**
```
c = min(validLogDays / 14, 1.0)
personalizedTarget = (1 - c) * agePriorMidpoint + c * clamp(observedMedian24h, ageMin, ageMax)
```
Clamps are broad warnings under 3 months; AASM 12–16h range for 4–12 months unless overridden.

**Wake target (percentile blend):**
```
successfulWakeIntervals = wake intervals before sleeps where onset looked successful
                          and no disruption flag
babyWakeP50 = median(successfulWakeIntervals, last 7–14 valid days)
< 14 valid days:  wakeTarget = 0.60*agePriorMidpoint + 0.40*babyWakeP50
>= 14 valid days: wakeTarget = 0.30*agePriorMidpoint + 0.70*babyWakeP50
```
Age priors come from the shared `SleepAgePriors` (§3).

---

## 8. Scoring engine (`domain/sleep/recommendation`, research §9–§11)

**Candidate generation:** 5-minute steps from `lastWake + wakeMin` to `lastWake + wakeMax`. Shift earlier for high sleep debt, short previous nap, or strong sleepy cues; later for long previous nap (research §9). Floor at `max(now, earliest)`. If an active feed is open, generate from `max(now, estimatedFeedEnd + buffer)` (research §5A.5).

**Scoring (research §21.5):**
```
score = 0.30*wakeFit + 0.20*sleepDebtFit + 0.15*circadianFit
      + 0.15*napBudgetFit + 0.10*feedFit + 0.10*historyFit
```
- `wakeFit` — closeness of candidate's wake interval to `wakeTarget`.
- `sleepDebtFit` — earlier if 24h sleep is below target by >60 min; later if above by >60.
- `circadianFit` — weight ramps with age (≈0 under 6 wk → 0.15 at 6–12 mo); rewards mid-morning nap 1, early-afternoon nap 2, learned bedtime, and penalizes a late third nap.
- `napBudgetFit` — remaining day-sleep budget vs. nap count prior; prefers earlier bedtime when budget is spent.
- `feedFit` — penalize windows that collide with the next breastfeed-due estimate; attach a feed prompt (soft only, never delay a feed).
- `historyFit` — similarity to the baby's successful sleep-start times of day.

Cue weight (absent in the base formula) is redistributed into wake/debt/history when cues exist; strong cues also shift candidate generation earlier (research §21.5).

**Output — `SleepRecommendation`:**
```kotlin
data class SleepRecommendation(
    val type: RecommendationType,
    val windowStartMillis: Long?,        // best ± ~15 min (research default window 30 min)
    val windowEndMillis: Long?,
    val bestEstimateMillis: Long?,
    val confidence: Confidence,
    val reasons: List<String>,
    val feedPrompt: String?,
    val safetyPrompt: String,
    val nextCheckInMinutes: Int
)
```

**Reasons** are generated from the dominant factors (research §16.3): time awake vs. usual, short previous nap, 24h sleep below target, feed due soon, recent sleepy cues. **Copy** follows research §17: "Suggested sleep window", "Based on recent patterns", "Offer a nap if sleepy cues appear" — never "must sleep now" / "sleep deprived". `safetyPrompt` is always present (back, firm flat empty space).

---

## 9. Realtime use case

`PredictSleepWindowUseCase` returns `Flow<SleepRecommendation?>`, mirroring `PredictNextFeedUseCase`. It `combine`s recent sleep records, recent breastfeeding sessions, recent baby events, the baby profile, and the predictive-sleep settings; recomputes on any change; `catch { emit(null) }`.

State precedence:
1. Open sleep record → `CURRENTLY_SLEEPING` (no new window).
2. `validLogDays < 3` → `NEED_MORE_DATA` (carries progress count for the UI).
3. Corrected age < 6 weeks → cue-led message (no clock window).
4. Open feed → `AFTER_ACTIVE_FEED` (generate from after the feed).
5. Otherwise → scored window.

Uses an injected `Clock` and `ZoneId` (consistent with `PredictNextFeedUseCase`) for testability.

---

## 10. Notification + settings (mirror feed-prediction stack)

- `PredictiveSleepScheduler` (interface) + `PredictiveSleepSchedulerImpl` — exact/inexact AlarmManager with `SecurityException` fallback, identical pattern to `PredictiveFeedSchedulerImpl`.
- `PredictiveSleepNotificationCoordinator` (`@Singleton`, `@ApplicationScope`) — `combine`s the prediction flow with `predictiveSleepEnabled`, `predictiveSleepLeadMinutes`, and shared quiet hours; debounces; reconciles the alarm at `bestEstimate − leadMinutes`, cancelling if disabled, past, or inside quiet hours. Started from `BabyTrackerApp` alongside the feed coordinator.
- `PredictiveSleepReceiver` (`@AndroidEntryPoint`) — fires the notification via a sleep variant in `NotificationHelper` / a `PredictiveSleepNotificationHelper`, using the Sleep (Blue) accent + sleep small icon per the design system.
- New DataStore settings: `predictiveSleepEnabled` (default off until first prediction is possible), `predictiveSleepLeadMinutes` (default 15). Quiet hours reused. Settings screen gains a "Sleep reminders" section parallel to the feed one.

The reminder only schedules when the recommendation is a real window (not `NEED_MORE_DATA` / `CURRENTLY_SLEEPING` / cue-led).

---

## 11. UI surfaces

**Home screen** (`ui/home`):
- Compact prediction card mirroring the feed card: e.g. "Next sleep ~9:35 · window 9:20–9:50", with a confidence hint. When `NEED_MORE_DATA`, the card shows the unlock message + progress (§12).
- Cue quick-tap chip row writing `BabyEventEntity`: `[😪 Sleepy] [😣 Fussy] [🤒 Sick] [🦷 Teething] [✈️ Travel]`. Tapping logs an event at `now`. Non-judgmental styling.

**Sleep schedule screen** (`ui/sleep`):
- A recommendation section above/within the existing schedule: window, best estimate, a confidence badge, the reason list, the feed prompt (if any), and the safe-sleep prompt. Reuses Material 3 tokens (Sleep Blue family).
- Empty/low-data and cue-led states rendered here too.

No new navigation routes; both are additions to existing screens/ViewModels.

---

## 12. Data-gating behavior ("some data is needed")

Thresholds (research §19 + §21.6):

| Condition | Behavior |
|---|---|
| `validLogDays < 3` | **No prediction.** Show: *"Keep logging naps and night sleep — sleep predictions unlock after about 3 days of tracking."* plus progress (e.g. "2 of 3 days logged"). |
| `validLogDays` 3–6 | **MEDIUM** confidence. Window shown with a "still learning your baby's pattern" note. |
| `validLogDays >= 7`, last sleep known, no open record | **HIGH** confidence ("good" predictions). |
| Corrected age < 6 weeks | Cue-led message regardless of data: *"At this age, sleep is best guided by sleepy cues rather than the clock."* |
| Open sleep / open feed / many invalid records | Confidence forced down or state-specific message per §9. |

Confidence is also lowered by active disruption flags (sick/teething/travel) and recent source/timezone anomalies. This directly answers the product requirement: predictions are withheld until ~3 valid days of logs, become reliable around 7 days, and the UI tells the parent exactly how much more logging is needed.

---

## 13. Learning loop (deterministic bias correction)

1. When a window-type recommendation is produced and scheduled, persist a `SleepRecommendationEntity` (with `algorithm_version`).
2. When the parent actually starts a sleep, match the nearest recent recommendation, compute `errorMinutes = actualStart − bestEstimate`, and store a `SleepRecommendationFeedbackEntity` with the user action (accepted / edited / ignored).
3. The engine reads recent feedback and applies `bias = clamp(rollingMedian(errorMinutes, last 14 valid days), ±maxBiasMinutes)` to the next best estimate (and window).

No ML; fully deterministic and unit-testable. It only nudges; per-baby percentiles remain the primary personalization. `algorithm_version` allows offline evaluation across versions later.

---

## 14. Tuning constants

A `SleepPredictionTuning` object (sibling of `PredictionTuning`), seeded from research §19:

```
RECOMMEND_WINDOW_MINUTES = 30
CANDIDATE_STEP_MINUTES = 5
MIN_VALID_DAYS_FOR_PREDICTION = 3
FULL_PERSONALIZATION_DAYS = 14
HIGH_CONFIDENCE_VALID_DAYS = 7
SLEEP_DEBT_EARLIER_SHIFT_MINUTES = 15
SHORT_NAP_THRESHOLD_RATIO = 0.6
SLEEP_ONSET_LATENCY_SUCCESS_MINUTES = 20
NAP_TRANSITION_MIN_PATTERN_DAYS = 7
CUE_LED_MAX_AGE_WEEKS = 6
LOOKBACK_DAYS = 14
MAX_BIAS_MINUTES = 30
ALGORITHM_VERSION = "sleep-pred-1"
```

---

## 15. Decomposition into Linear issues (one PR each)

**Phase 1 — data-model foundation**
1. **AKA-### `BabyProfileEntity` + babyId/timestamps + stable enums (Room v2→v3 migration).** Includes default-baby backfill from DataStore, indices, hardened `SleepType` converter, migration tests.
2. **AKA-### `baby_events` model + DAO/repository + Home quick-tap cue row.**

**Phase 2 — engine**
3. **AKA-### Feature-extraction layer** (`SleepInterval`/`BreastfeedInterval`/`SleepMetrics`/`BreastfeedingMetrics`/`CueScores`), local-tz day logic, overlap, validation — pure, exhaustively unit-tested.
4. **AKA-### Shared `SleepAgePriors` + personalization** (Bayesian target, wake percentiles). Refactor `GenerateSleepScheduleUseCase` to delegate to the shared priors.
5. **AKA-### Candidate generation + scoring engine → `SleepRecommendation`** (reasons, confidence, prompts, copy guidelines).
6. **AKA-### `PredictSleepWindowUseCase`** realtime `Flow`, state precedence, gating.

**Phase 3 — surface**
7. **AKA-### Predictive-sleep notification stack** (`PredictiveSleepScheduler`/Coordinator/Receiver/helper) + settings (`predictiveSleepEnabled`, `predictiveSleepLeadMinutes`) + Settings UI + app startup wiring.
8. **AKA-### Home prediction card + Sleep-screen recommendation section + gating/empty/cue-led states.**

**Phase 4 — learning**
9. **AKA-### Recommendation + feedback tables + rolling bias correction** wired into the engine.

Each issue gets its own branch, adversarial review before commit, and a dedicated conventional commit (`feat(sleep|db|notification|usecase|ui): …`). Phases are sequential; within a phase, issues 3–6 are mostly independent once the data model lands.

---

## 16. Testing strategy

- **Pure domain** (feature, personalization, recommendation, feedback): JUnit 5 + MockK + a fixed `Clock`/`ZoneId`. Cover age bands, corrected age, sleep-debt shifts, short-nap adjustment, circadian ramp, feed collision, cross-midnight overlap, gating thresholds, bias correction, and copy-safety (no forbidden phrases — extend the existing `PredictionCopyTest` style).
- **Migrations**: Room migration tests (v2→v3) verifying default-baby backfill, `baby_id` population, new tables, enum normalization, and schema-hash validity.
- **Notification**: coordinator unit tests (enabled/disabled, lead, quiet hours, past-trigger cancel) mirroring `PredictiveFeedNotificationCoordinatorTest`; receiver instrumentation test mirroring `PredictiveFeedReceiverTest`.
- **UI**: Robolectric Compose tests for the Home card states (need-more-data / window / cue-led) and the Sleep-screen section, mirroring `HomeScreenPredictionTest` / `SettingsScreenPredictionTest`.
- Architecture tests use the shared `productionScope` and `@Tag("architecture")`; run the fast loop (`-PfastTests`) during development and the full suite before each PR.

---

## 17. Risks & mitigations

- **Migration on real user data** (birth date currently only in DataStore) → default-baby backfill is the riskiest step; cover with migration tests and verify DataStore→profile parity; keep the change additive.
- **Over-promising accuracy early** → strict gating (§12) and confidence labels; copy guidelines prevent prescriptive/medical phrasing.
- **Timezone correctness** → all day/night logic flows through `BabyProfileEntity.timezoneId`; no UTC day boundaries; explicit cross-midnight tests.
- **Scope creep** → wake windows and cues stay *soft priors/low-confidence signals*, never hard rules (research §3.4, §5B.18); no ML.
- **Two schedule features confusing users** → the predictor is framed as "next sleep" and the existing screen as the day plan; they share priors so they never contradict.

---

## 18. Open questions (deferred, non-blocking)

- Whether to later promote breastfeeding into a generic `FeedingSessionEntity` (research §5B.6) so the prompt can say "feed" instead of "breastfeed". Out of scope here; the engine reads breastfeeding only and labels accordingly.
- Sleep-onset-latency capture (put-down vs. asleep) for richer feedback (research §5B.5) — deferred to a future project; the bias loop uses start-time error for now.
