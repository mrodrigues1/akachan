# Research Report: Evidence-Based Algorithm for Infant Sleep/Nap Scheduling (0–12 Months)

**Prepared for:** Baby sleep scheduling app development  
**Date:** 2026-06-03  
**Scope:** Infants from birth to 12 months. The report focuses on how to calculate suggested nap windows and bedtime from age, sleep logs, nap logs, and feeding logs.  
**Important limitation:** This report supports a wellness/planning algorithm. It is not a medical diagnostic tool and should not replace pediatric guidance, especially for premature infants, infants with poor weight gain, medical conditions, breathing concerns, reflux, or clinician-directed feeding schedules.

---

## 1. Executive summary

A scientifically responsible baby-sleep scheduler should **not** output a rigid “ideal schedule” as if every baby at the same age should sleep at the same time. The strongest evidence supports:

1. **Age-based total sleep ranges**, especially from 4–12 months.
2. **Developmental trends** across the first year: total sleep decreases, nighttime sleep consolidates, daytime sleep decreases, longest nighttime stretch increases, and circadian rhythm emerges gradually.
3. **High individual variability**, especially under 4 months.
4. **Responsive feeding and safe sleep constraints** that must override schedule optimization.
5. **Personalization from the baby’s own logs** rather than applying universal wake windows as hard rules.

For the app, the recommended approach is a **hybrid algorithm**:

- Use **age-based evidence ranges** as priors.
- Use the infant’s recent sleep and feeding logs to learn their individual pattern.
- Generate a **sleep opportunity window** rather than a single exact time.
- Score candidate nap/bedtime windows using age, time since last wake, recent sleep debt, nap count, time of day, feed timing, sleepy cues, and parental constraints.
- Continuously update the model as more logs arrive.

The app should frame output as:

> “Suggested next sleep window: 09:20–09:50. Best estimate: 09:35. Confidence: medium. Reason: 2h10 since wake-up, previous nap was short, 24h sleep is below recent target, and feed is due soon, so offer feed before nap if hunger cues appear.”

---

## 2. Evidence hierarchy used

The sources were weighted in this order:

1. **Consensus sleep-duration guidelines**: American Academy of Sleep Medicine (AASM), National Sleep Foundation.
2. **Pediatric safety guidance**: American Academy of Pediatrics (AAP).
3. **Systematic reviews and longitudinal studies** on infant sleep development.
4. **Clinical/public-health feeding guidance**: CDC, AAP/HealthyChildren.
5. **Parent-facing pediatric sleep resources** only when clearly separated from evidence-based claims.

---

## 3. Scientific findings relevant to the algorithm

### 3.1 Sleep duration targets

The AASM consensus statement recommends that **infants 4–12 months sleep 12–16 hours per 24 hours, including naps**. The same statement explicitly does **not** provide a recommendation for infants younger than 4 months because of wide normal variability and insufficient evidence linking a precise duration to health outcomes in that age group.[^aasm]

The National Sleep Foundation recommends **14–17 hours** for newborns 0–3 months and **12–15 hours** for infants 4–11 months.[^nsf]

**Algorithm implication**

- For **0–3 months**, use broad guidance only. Avoid “you should nap at X time” scheduling.
- For **4–12 months**, use a daily sleep target range of **12–16 h/24h**, then personalize within that range.
- Never treat a baby outside the range as automatically abnormal. Use red-flag logic and trend monitoring.

---

### 3.2 First-year developmental sleep trajectory

Recent pediatric sleep review evidence describes the first year as highly dynamic. Reported patterns include:

- Newborn total sleep time often around **16–17 h/day**.
- By ~6 months, total sleep time decreases to about **13–14 h/day**, with the longest sleep period averaging **5–6 h**.
- By ~12 months, total sleep time decreases to about **12–13 h/day**, with the longest sleep period averaging **8–9 h**.
- After the neonatal period, sleep episodes decrease across the first year.
- Circadian rhythm is not robust in newborns and typically strengthens after roughly **6–12 weeks**.[^oconnor]

A systematic review of infant sleep-wake behavior during the first 12 months found that 24-hour sleep duration, daytime sleep, and nighttime sleep periods decrease over the first year, while the longest night sleep period increases during the first 6 months. It also emphasized high discrepancy across studies and cautioned against overgeneralizing exact reference values.[^dias]

A longitudinal cohort of 704 infants found high inter-individual variability during the first year, with the greatest sleep changes in the first 6 months and more stability from 6–12 months.[^bruni]

**Algorithm implication**

The app should model age as a continuous developmental variable, but it should not expect linear, perfectly predictable change. The most important personalization period is the first 2–4 weeks of logs.

---

### 3.3 Nap count and nap distribution

The research is stronger for **total sleep trends** than for exact nap times. Still, common developmental patterns are useful as priors:

| Age | Evidence-based interpretation | Engineering prior for scheduling |
|---:|---|---|
| 0–6 weeks | No stable circadian rhythm; sleep distributed day and night; feed/sleep cycles dominate | No fixed schedule. Predict next sleep from last wake + cues + feed timing. |
| 6–12 weeks | Circadian rhythm begins emerging; day/night pattern starts strengthening | Gentle windows only; use morning wake anchor if stable. |
| 3–4 months | Patterns become more predictable but still variable | 3–5 naps; short wake intervals; bedtime may remain variable. |
| 4–5 months | AASM range applies; many infants still use 3–4 naps | Start calculating next nap windows; avoid hard clock schedule. |
| 6–8 months | More nighttime consolidation; many babies shift from 3 naps toward 2 | Support 2–3 nap schedules; detect transition readiness. |
| 9–12 months | More predictable nighttime sleep; many babies use 2 naps; some begin moving toward 1 near/after first birthday | Default 2 naps; support 1 nap only if logs consistently support it. |

The Canadian Paediatric Society notes that by 4 months many babies need three naps and that between 6 and 12 months babies often move from three naps to two longer naps, with substantial individual differences.[^cps]

**Algorithm implication**

Nap count should be an adaptive parameter. Do not hard-code “a 7-month-old always takes 2 naps.” Instead, use age priors and recent logs.

---

### 3.4 Wake windows: useful heuristic, not a scientific law

“Wake windows” are popular in parent guidance, but the evidence base does not support precise age-specific wake-window values as universal biological rules. Pediatric sleep physician Craig Canapari notes that wake windows have some basis in sleep pressure but little evidence that wake-window systems improve naps.[^canapari]

**Algorithm implication**

Use wake windows only as **soft priors**. The app should calculate a recommended **sleep opportunity range** from:

- Time since last sleep ended.
- Previous nap length.
- 24h sleep amount.
- Recent night quality.
- Age.
- Time of day.
- Feeding timing.
- Baby-specific historical tolerance.
- Sleepy cues.

The app should not state that a baby “must” sleep after exactly a specific number of minutes.

---

### 3.5 Feeding and sleep

Feeding must be treated as a physiological constraint, not merely a scheduling variable.

The CDC states that most exclusively breastfed babies feed every **2–4 hours**, with some feeding as often as every hour during cluster feeding and some having a longer sleep interval of 4–5 hours.[^cdc_bf] HealthyChildren/AAP guidance emphasizes **responsive feeding**: caregivers should recognize and respond to hunger and fullness cues rather than relying only on a strict schedule.[^aap_feed]

Research on 6–12-month-old infants found night waking and night feeding remain common: in one study, **78.6%** of infants woke at least once at night and **61.4%** received one or more milk feeds; both night wakings and night feeds decreased with age, and no difference was found between currently breastfed and formula-fed infants in that age range.[^brown]

**Algorithm implication**

- Under ~4–6 months, do not delay feeds to force sleep timing.
- Feed recommendations should be cue-based and pediatrician-safe.
- If a nap/bedtime candidate would conflict with expected feeding needs, suggest feeding before the sleep window if hunger cues are present.
- Do not assume that eliminating night feeds is developmentally appropriate for all 6–12-month-olds.
- Do not claim that adding solids, cereal, fats, or larger feeds will reliably improve sleep unless a clinician recommends it for nutrition reasons.

---

### 3.6 Bedtime routine evidence

A controlled study found that a consistent nightly bedtime routine improved multiple infant/toddler sleep outcomes, including sleep onset latency and night waking measures, and improved maternal mood.[^mindell]

**Algorithm implication**

The scheduling engine should not only output times. It should optionally attach a **routine prompt**, for example:

- “Start wind-down 15–20 min before suggested sleep.”
- “Use the same short sequence before naps/bedtime.”
- “Dim lights for evening sleep.”
- “Keep nighttime feeds low-stimulation.”

This is a behavioral support layer, not the core schedule calculation.

---

### 3.7 Safe sleep must be non-negotiable

AAP safe sleep guidance recommends placing infants on their backs, in their own sleep space, on a firm flat mattress with fitted sheet, and keeping loose blankets, pillows, stuffed toys, bumpers, and soft objects out of the sleep space. The AAP also warns against sleep on couches, armchairs, and seating devices such as swings or car seats except while riding in a car.[^aap_safe]

**Algorithm implication**

Every sleep recommendation should be safety-aware:

- If user logs sleep in a car seat/swing/couch, classify it as a sleep event for sleep accounting but prompt safe-sleep guidance.
- Do not recommend unsafe locations.
- If the baby falls asleep in a feeding or holding context, the app should distinguish “logged sleep” from “recommended safe sleep environment.”

---

## 4. Recommended algorithm model

### 4.1 High-level design

Use a **hybrid rules + personalization scoring model**:

1. **Age prior layer**
   - Defines broad sleep range, nap count range, and approximate wake tolerance.
2. **Personal pattern layer**
   - Learns the baby’s actual sleep total, nap length, morning wake time, bedtime, feed intervals, and response to wake duration.
3. **Constraint layer**
   - Applies safe sleep, feeding safety, parental schedule, and medical flags.
4. **Candidate generation layer**
   - Creates possible nap/bedtime start times.
5. **Scoring layer**
   - Scores candidates and returns a ranked sleep window.
6. **Explanation layer**
   - Explains the recommendation in caregiver-friendly language.

---

## 5. Inputs required

The full research model below includes ideal inputs. Section 5A then maps the algorithm to the app’s current Room entities and clearly separates what is available now from fields that may be added later.

### 5.1 Baby profile

```json
{
  "dateOfBirth": "YYYY-MM-DD",
  "dueDate": "YYYY-MM-DD | null",
  "wasPremature": true,
  "useCorrectedAgeUntilMonths": 24,
  "timezone": "America/Sao_Paulo",
  "feedingMode": "breast | formula | mixed | solids_plus_milk",
  "clinicianFeedIntervalMaxMinutes": null,
  "safeSleepEducationShown": true
}
```

**Corrected age:** For premature infants, scheduling priors should use corrected age unless a clinician or product policy says otherwise.

### 5.2 Sleep log — full/future model

The current `SleepEntity` already supports the required minimum fields: start, end, and nap/night type. Location, source, onset assistance, and quality are recommended future fields.

```json
{
  "sleepStart": "ISO timestamp",
  "sleepEnd": "ISO timestamp",
  "type": "nap | night | unknown",
  "location": "crib | bassinet | caregiver_arms | stroller | car_seat | swing | bedshare | other",
  "sleepOnsetAssistance": ["feeding", "rocking", "pacifier", "independent"],
  "quality": "normal | restless | unknown",
  "source": "manual | wearable | inferred"
}
```

### 5.3 Feeding log — full/future model

The current app model only covers breastfeeding sessions. This fuller shape is useful if the app later adds bottle, formula, solids, hunger cues, or volume.

```json
{
  "feedStart": "ISO timestamp",
  "feedEnd": "ISO timestamp",
  "type": "breast | bottle_breastmilk | formula | solids",
  "volumeMl": null,
  "side": "left | right | both | null",
  "hungerCues": true,
  "fullnessCues": true,
  "nightFeed": true
}
```

### 5.4 Optional cue/event log

```json
{
  "timestamp": "ISO timestamp",
  "cue": "yawn | eye_rub | fussiness | calm_alert | hyperactive | rooting | illness | teething | vaccine | travel"
}
```


---

## 5A. App data model integration: current Room entities

This section adapts the algorithm to the app’s current models. The goal is to avoid designing an algorithm that depends on fields the app does not yet store.

### 5A.1 Current app models

```kotlin
@Entity(tableName = "breastfeeding_sessions")
data class BreastfeedingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "starting_side") val startingSide: String,
    @ColumnInfo(name = "switch_time") val switchTime: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "paused_at") val pausedAt: Long? = null,
    @ColumnInfo(name = "paused_duration_ms") val pausedDurationMs: Long = 0
)
```

```kotlin
@Entity(tableName = "sleep_records")
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "sleep_type") val sleepType: String,
    @ColumnInfo(name = "notes") val notes: String? = null
)

enum class SleepType(val label: String, val emoji: String) {
    NAP("Nap", "😴"),
    NIGHT_SLEEP("Night Sleep", "🌙"),
}
```

### 5A.2 What the algorithm can calculate from the current models

The current entities are sufficient for a useful MVP because the algorithm can calculate:

| Feature | Source | Calculation |
|---|---|---|
| Last sleep end / last wake time | `SleepEntity.endTime` | End time of most recent completed sleep record. |
| Current sleep status | `SleepEntity.endTime == null` | If a sleep is open, baby is currently sleeping; do not recommend another sleep. |
| Sleep duration | `SleepEntity.startTime`, `endTime` | `endTime - startTime`, excluding invalid negative durations. |
| Nap count today | `SleepEntity.sleepType == NAP` | Count completed naps whose start or end falls within the local day. |
| Day sleep today | `sleepType == NAP` | Sum completed nap durations for the local day. |
| Night sleep last night | `sleepType == NIGHT_SLEEP` | Sum night sleep overlapping the previous night interval. |
| Total sleep last 24h | all sleep records | Sum overlap between each sleep interval and `[now - 24h, now]`. |
| Median nap duration | `NAP` records | Rolling median from last 7–14 valid days. |
| Median bedtime | `NIGHT_SLEEP.startTime` | Rolling median start time of night sleep records. |
| Median morning wake | `NIGHT_SLEEP.endTime` | Rolling median end time of night sleep records ending in the morning. |
| Last feed start/end | `BreastfeedingEntity` | Most recent breastfeeding session. Use `endTime` for completed feeds. |
| Current feed status | `BreastfeedingEntity.endTime == null` | If feed is open, treat baby as currently feeding or feed session not finished. |
| Feed duration | breastfeeding start/end/pause fields | `(effectiveEnd - startTime) - pausedDurationMs`, with active pause handling. |
| Median feed interval | breastfeeding start times | Median start-to-start interval from recent completed sessions. |
| Feed due estimate | last feed + median interval | Use as a soft prompt only, never as a hard rule. |

### 5A.3 What the current models cannot reliably infer

The current models do **not** store several clinically or behaviorally meaningful variables. The app should not infer them too aggressively.

| Missing variable | Why it matters | Product decision |
|---|---|---|
| Bottle/formula/solid feeds | Feeding mode changes sleep/feed timing interpretation | Current algorithm should say “based on breastfeeding logs” rather than “all feeds.” |
| Feed volume or milk transfer | Breastfeeding duration is not a reliable volume measure | Do not assume longer feed = fuller baby. |
| Hunger/sleepy cues | Cues are central for 0–4 months | Use optional note parsing only as low-confidence signal, or add explicit cue fields later. |
| Sleep location | Needed for safe-sleep contextual prompts | Show generic safe-sleep prompts; do not classify unsafe sleep from current data. |
| Sleep onset latency | Needed to evaluate whether recommendation was good | Approximate only if the user accepts a recommendation and logs start. |
| Night wakes inside a night sleep record | A single `NIGHT_SLEEP` can hide wakings | Do not overclaim “slept through the night.” |
| Timezone per record | Epoch millis need local-day grouping | Use the user/baby profile timezone consistently for day boundaries. |

### 5A.4 Important timestamp assumptions

The models use `Long` timestamps. The implementation should standardize them as **epoch milliseconds** and convert them with `Instant.ofEpochMilli(...)`.

Recommended handling:

```kotlin
val instant = Instant.ofEpochMilli(entity.startTime)
val localDateTime = instant.atZone(babyZoneId).toLocalDateTime()
```

Do not calculate “today,” “last night,” bedtime, or morning wake using UTC boundaries unless the baby’s timezone is UTC. Sleep scheduling is local-time dependent.

### 5A.5 Handling open records

Both sleep and breastfeeding can have `endTime = null`. These are in-progress records and should change recommendation behavior.

#### Open sleep record

If the latest sleep record has `endTime == null`:

```text
recommendationType = current_sleeping
message = "Baby is currently sleeping. The next sleep window can be calculated after this sleep ends."
```

For metrics, you can optionally include the ongoing sleep duration in “current sleep duration,” but do **not** use it as a completed nap/night sleep duration until it ends.

#### Open breastfeeding record

If the latest breastfeeding session has `endTime == null`:

```text
feedState = active_or_unfinished_feed
sleep recommendation = "After the feed, watch for sleepy cues / start wind-down if the wake window is already near target."
```

Do not schedule a nap start inside an active feed. Instead, generate candidates from `max(now, estimatedFeedEndOrNow + transitionBuffer)`.

### 5A.6 Breastfeeding duration with pause fields

For completed feeds:

```kotlin
val rawDurationMs = endTime - startTime
val activeDurationMs = (rawDurationMs - pausedDurationMs).coerceAtLeast(0)
```

For an active feed that is currently paused:

```kotlin
val effectiveNow = pausedAt ?: now
val rawDurationMs = effectiveNow - startTime
val activeDurationMs = (rawDurationMs - pausedDurationMs).coerceAtLeast(0)
```

This is useful for displaying feed duration and detecting very recent feeds, but the sleep algorithm should mostly use **feed timing**, not feed duration, because breastfeeding duration is not equivalent to intake volume.

### 5A.7 Interpreting `startingSide` and `switchTime`

For sleep scheduling, `startingSide` and `switchTime` should be treated as breastfeeding details, not sleep predictors.

Possible low-risk uses:

- Show “last feed started on left/right” in the explanation if already shown elsewhere in the app.
- Detect whether a feed was likely both-side or one-side using `switchTime != null`.

Avoid:

- Inferring that a both-side feed means baby will sleep longer.
- Inferring milk volume from side switching.
- Inferring hunger/fullness without explicit caregiver input.

### 5A.8 Mapping `SleepType`

The model stores `sleepType` as a `String`. The algorithm should parse defensively:

```kotlin
fun parseSleepType(value: String): SleepType? =
    SleepType.entries.firstOrNull { it.name == value || it.label == value }
```

Recommended storage going forward: store `SleepType.name` (`NAP`, `NIGHT_SLEEP`) rather than label text (`Nap`, `Night Sleep`) to avoid localization and emoji/display changes affecting logic.

### 5A.9 Night sleep records that cross midnight

A night sleep often starts on one date and ends on the next. For calculations such as “last 24h sleep,” use interval overlap, not calendar-day start time.

Example overlap function:

```kotlin
fun overlapMs(start: Long, end: Long, windowStart: Long, windowEnd: Long): Long {
    val overlapStart = maxOf(start, windowStart)
    val overlapEnd = minOf(end, windowEnd)
    return (overlapEnd - overlapStart).coerceAtLeast(0)
}
```

This prevents undercounting night sleep that started yesterday and ended today.

### 5A.10 Minimum viable algorithm using only these entities

The current models support the following MVP output:

```kotlin
data class SleepRecommendation(
    val recommendationType: RecommendationType,
    val windowStartMillis: Long?,
    val windowEndMillis: Long?,
    val bestEstimateMillis: Long?,
    val confidence: Confidence,
    val reasons: List<String>,
    val feedPrompt: String?,
    val safetyPrompt: String
)

enum class RecommendationType {
    NAP,
    BEDTIME,
    OPTIONAL_CATNAP,
    NO_SLEEP_YET,
    CURRENTLY_SLEEPING,
    AFTER_ACTIVE_FEED
}

enum class Confidence { LOW, MEDIUM, HIGH }
```

Core recommendation flow using the current entities:

```pseudo
function recommendNextSleepFromRoomModels(
    babyDateOfBirth,
    optionalDueDate,
    zoneId,
    sleepEntities,
    breastfeedingEntities,
    nowMillis
): SleepRecommendation

    completedSleeps = sleepEntities where endTime != null and endTime > startTime
    openSleep = latest sleep where endTime == null
    openFeed = latest breastfeeding where endTime == null

    if openSleep exists:
        return CURRENTLY_SLEEPING recommendation

    age = correctedAgeIfAvailable(babyDateOfBirth, optionalDueDate, nowMillis)
    band = getAgeBand(age)

    metrics = computeSleepMetricsFromSleepEntities(completedSleeps, zoneId, nowMillis)
    feedMetrics = computeBreastfeedingMetrics(breastfeedingEntities, nowMillis)

    if metrics.lastWakeTime is null:
        return LOW confidence generic cue-led recommendation

    if openFeed exists:
        return AFTER_ACTIVE_FEED recommendation if baby is near/over wake target

    targetSleep = personalizeSleepTarget(band, metrics)
    wakeTarget = personalizeWakeTarget(band, metrics)

    nextType = decideNapVsBedtime(band, metrics, nowMillis, zoneId)
    candidates = generateCandidatesFromLastWake(metrics.lastWakeTime, wakeTarget, band, nowMillis)
    candidates = adjustCandidatesForSleepDebt(candidates, metrics, targetSleep)
    candidates = adjustCandidatesForFeedTiming(candidates, feedMetrics)

    scored = scoreCandidates(candidates, nextType, metrics, feedMetrics, band)
    best = highestScore(scored)

    return buildWindowRecommendation(best, scored, metrics, feedMetrics, confidence)
```

### 5A.11 Deciding nap vs bedtime from the current sleep model

Because the app already labels sleep as `NAP` or `NIGHT_SLEEP`, the recommendation can use a hybrid approach:

1. Use recent `NIGHT_SLEEP.startTime` as the personalized bedtime anchor.
2. Use age-based nap-count priors to decide how many naps are expected today.
3. Use the last completed sleep type and current local time to decide whether the next sleep is likely a nap or bedtime.

Practical MVP rules:

```text
If local time is before 16:30:
  usually recommend NAP, unless age is newborn and schedule is cue-led.

If local time is 16:30–18:30:
  recommend NAP only if:
    age band supports 3+ naps,
    nap count today is below expected,
    and last wake was too early for bedtime.
  otherwise recommend early BEDTIME.

If local time is after 18:30:
  usually recommend BEDTIME, unless recent bedtime is much later and baby is not near wake target.
```

For newborns and young infants, this clock logic should be weak. For older infants, learned bedtime should matter more than fixed clock thresholds.

### 5A.12 Feed-aware logic using only breastfeeding records

Because only breastfeeding sessions are available, the feed model should be named clearly:

```text
breastfeeding_due_probability
```

not:

```text
feeding_due_probability
```

unless bottle/formula/solid models are added.

Recommended feed interval calculation:

```pseudo
recentCompletedFeeds = breastfeeding sessions with endTime != null from last 3–7 days
intervals = difference between consecutive feed.startTime values
medianFeedInterval = median(intervals), grouped by day/night if enough data
lastFeedStart = latest feed.startTime
lastFeedEnd = latest feed.endTime
nextBreastfeedDue = lastFeedStart + medianFeedInterval
```

Why start-to-start? Parent guidance often describes breastfeeding frequency as feed starts across the day. For sleep readiness, also keep `minutesSinceFeedEnd`, because a baby who just finished feeding may be ready to sleep even if the start time was 30 minutes ago.

MVP feed conflict rule:

```pseudo
if candidateStart > nextBreastfeedDue - 20min:
    lower score slightly
    add prompt: "A breastfeed may be due before or during this sleep window. Offer a feed first if hunger cues appear."

if minutesSinceFeedEnd <= 20 and timeAwakeNearTarget:
    increase score slightly
    add reason: "Recent feed may support settling for sleep."
```

Use this only as a soft scheduling feature. Do not imply that breastfeeding should be delayed, stretched, or manipulated to force sleep.

### 5A.13 Recommended helper data classes for the domain layer

Keep Room entities separate from the scheduling domain. Convert them before calculating recommendations.

```kotlin
data class SleepInterval(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long,
    val type: SleepType,
    val notes: String?
) {
    val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0)
}

data class BreastfeedInterval(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long?,
    val activeDurationMillis: Long,
    val startingSide: String,
    val switchedSide: Boolean,
    val notes: String?
)

data class SleepMetrics(
    val lastWakeMillis: Long?,
    val lastSleepDurationMillis: Long?,
    val sleepLast24hMillis: Long,
    val daySleepTodayMillis: Long,
    val nightSleepLastNightMillis: Long,
    val napCountToday: Int,
    val medianNapDurationMillis: Long?,
    val medianBedtimeMinuteOfDay: Int?,
    val medianMorningWakeMinuteOfDay: Int?,
    val validLogDays: Int
)

data class BreastfeedingMetrics(
    val lastFeedStartMillis: Long?,
    val lastFeedEndMillis: Long?,
    val activeFeedInProgress: Boolean,
    val medianStartToStartIntervalMillis: Long?,
    val minutesSinceFeedStart: Long?,
    val minutesSinceFeedEnd: Long?,
    val nextFeedDueEstimateMillis: Long?
)
```

### 5A.14 Notes parsing: optional and low confidence

Both entities include `notes`. For MVP, notes should not be required for the algorithm. If the app later parses notes, treat parsed signals as low confidence because notes are inconsistent and multilingual.

Possible note keywords:

```text
sleepy cues: yawn, rubbing eyes, fussy, cranky, tired, sleepy
hunger cues: rooting, hungry, crying, cluster feeding
context flags: sick, vaccine, travel, teething, car, stroller
```

For Portuguese support, include terms such as:

```text
sono, soninho, bocejo, irritado, fome, mamada, vacina, doente, viagem, dente
```

Any parsed note signal should be explanatory, not decisive.

### 5A.15 Data validation rules

Before running recommendations, filter or flag records:

```text
Reject or ignore sleep if:
  endTime != null and endTime <= startTime
  duration > 16h for a NAP
  duration > 24h for any sleep record unless manually confirmed

Flag as unusual if:
  NAP duration > 4h
  NIGHT_SLEEP duration < 2h for older infants
  overlapping sleep records exist
  overlapping breastfeeding records exist
  sleep and breastfeeding overlap for long periods
```

Overlap between breastfeeding and sleep should not always be rejected. Babies can fall asleep while feeding. For scheduling metrics, however, long overlaps can distort wake-time calculations, so the app should keep the sleep log but mark confidence lower.

### 5A.16 Current model limitations and recommended future fields

The current models are enough for an MVP, but these additions would make the algorithm safer and more accurate:

```kotlin
// Sleep additions
val source: String // manual, inferred, wearable
val location: String? // crib, bassinet, stroller, carSeat, arms, etc.
val createdAt: Long
val updatedAt: Long

// Cue/event model
@Entity(tableName = "baby_events")
data class BabyEventEntity(
    val timestamp: Long,
    val eventType: String, // sleepy_cue, hunger_cue, illness, vaccine, travel, teething
    val intensity: Int? = null,
    val notes: String? = null
)

// Feeding additions, if app expands beyond breastfeeding
@Entity(tableName = "bottle_feeds")
data class BottleFeedEntity(...)

@Entity(tableName = "solid_feeds")
data class SolidFeedEntity(...)
```

Do not block the MVP on these additions. Instead, design the algorithm so these fields can become new scoring inputs later.


---

## 5B. Recommended data model improvements for a stronger scheduling algorithm

The current Room entities are enough to build a useful MVP, but the sleep recommendation engine will become more accurate, safer, and easier to evolve if the data model separates **raw user logs**, **derived algorithm features**, and **recommendation feedback**.

The improvements below are organized by priority. The app does not need all of them before the first version of the algorithm.

### 5B.1 Priority summary

| Priority | Improvement | Why it matters for the algorithm |
|---|---|---|
| P0 | Add `babyId` to sleep and feed records | Supports multiple children and prevents future migration pain. |
| P0 | Add a `BabyProfileEntity` | Age, corrected age, timezone, and sleep priors depend on the baby profile. |
| P0 | Standardize enum persistence | Prevents localized labels or UI text from breaking algorithm logic. |
| P0 | Add `createdAt` and `updatedAt` | Needed for sync, debugging, auditability, and distinguishing log time from edit time. |
| P1 | Add sleep location/source fields | Enables safe-sleep prompts and quality/confidence handling. |
| P1 | Add generic feeding model beyond breastfeeding | The sleep algorithm should reason about all feeds, not only breastfeeding. |
| P1 | Add cue/event model | Sleepy cues, hunger cues, illness, vaccines, teething, and travel affect recommendations. |
| P1 | Add recommendation feedback model | Lets the app learn whether suggested windows worked. |
| P2 | Add sleep interruption/waking model | Prevents overclaiming that a baby slept continuously through the night. |
| P2 | Add aggregate/feature cache | Improves performance and makes recommendation explanations easier. |
| P2 | Add pause segments instead of only cumulative pause duration | Useful for advanced analytics, but not required for MVP. |

---

### 5B.2 Add a `BabyProfileEntity`

The scheduling algorithm cannot be reliably age-aware without a baby profile. Age bands, corrected age, timezone grouping, and medical/safety disclaimers all depend on this entity.

Recommended model:

```kotlin
@Entity(tableName = "babies")
data class BabyProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String? = null,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: Long,
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,
    @ColumnInfo(name = "timezone_id") val timezoneId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

Optional but useful later:

```kotlin
@ColumnInfo(name = "was_premature") val wasPremature: Boolean = false
@ColumnInfo(name = "corrected_age_until_months") val correctedAgeUntilMonths: Int? = 24
@ColumnInfo(name = "clinician_feed_interval_max_minutes") val clinicianFeedIntervalMaxMinutes: Int? = null
@ColumnInfo(name = "safe_sleep_education_shown_at") val safeSleepEducationShownAt: Long? = null
```

Algorithm benefit:

- Uses corrected age for premature babies when appropriate.
- Computes local days/nights using the baby’s timezone, not the device’s temporary timezone.
- Supports multiple children without changing every table later.
- Allows product safety rules for babies with clinician-directed feeding schedules.

Recommended immediate migration:

```kotlin
@ColumnInfo(name = "baby_id", index = true) val babyId: Long
```

Add this to both `SleepEntity` and `BreastfeedingEntity`. If the app currently supports only one baby, create one default baby row during migration and attach all existing logs to it.

---

### 5B.3 Persist enum codes, not labels

The current `SleepType` has display labels and emojis:

```kotlin
enum class SleepType(val label: String, val emoji: String) {
    NAP("Nap", "😴"),
    NIGHT_SLEEP("Night Sleep", "🌙"),
}
```

The database should store stable enum names or explicit codes, not labels. Labels are UI text and may change with localization.

Recommended:

```kotlin
enum class SleepType {
    NAP,
    NIGHT_SLEEP
}
```

UI metadata can live outside persistence:

```kotlin
val SleepType.label: String
    get() = when (this) {
        SleepType.NAP -> "Nap"
        SleepType.NIGHT_SLEEP -> "Night Sleep"
    }

val SleepType.emoji: String
    get() = when (this) {
        SleepType.NAP -> "😴"
        SleepType.NIGHT_SLEEP -> "🌙"
    }
```

Use Room type converters:

```kotlin
class SleepTypeConverter {
    @TypeConverter
    fun fromSleepType(value: SleepType): String = value.name

    @TypeConverter
    fun toSleepType(value: String): SleepType =
        runCatching { SleepType.valueOf(value) }
            .getOrDefault(SleepType.NAP)
}
```

Algorithm benefit:

- No recommendation bug if the app is translated to Portuguese.
- No bug if labels change from `Night Sleep` to `Nighttime Sleep`.
- Easier filtering: `sleepType == SleepType.NAP.name`.

Apply the same idea to breastfeeding side:

```kotlin
enum class BreastSide { LEFT, RIGHT, BOTH, UNKNOWN }
```

---

### 5B.4 Improve `SleepEntity`

Recommended near-term version:

```kotlin
@Entity(
    tableName = "sleep_records",
    indices = [Index("baby_id"), Index("start_time"), Index("end_time")]
)
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "baby_id") val babyId: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "sleep_type") val sleepType: String,
    @ColumnInfo(name = "source") val source: String = SleepSource.MANUAL.name,
    @ColumnInfo(name = "location") val location: String? = null,
    @ColumnInfo(name = "quality") val quality: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

Suggested enums:

```kotlin
enum class SleepSource {
    MANUAL,
    TIMER,
    INFERRED,
    IMPORTED,
    WEARABLE
}

enum class SleepLocation {
    CRIB,
    BASSINET,
    CAREGIVER_ARMS,
    STROLLER,
    CAR_SEAT,
    SWING,
    CO_SLEEPING_BED,
    OTHER,
    UNKNOWN
}

enum class SleepQuality {
    NORMAL,
    RESTLESS,
    SHORT,
    HARD_TO_SETTLE,
    UNKNOWN
}
```

Algorithm benefit:

- `source` lets the model trust manual timers more than inferred records.
- `location` enables safe-sleep prompts and lower confidence for motion sleep, contact naps, car-seat sleep, etc.
- `quality` helps detect whether a nap looked restorative.
- `createdAt` and `updatedAt` help debug user-edited logs.

Important product note: avoid making `location` feel judgmental. Use it for helpful safety prompts and algorithm confidence, not blame.

---

### 5B.5 Consider separating “put down time” from “asleep time”

The current `SleepEntity.startTime` should mean **actual sleep start**. If the app later wants to optimize routines, it should also capture when the caregiver began trying to put the baby to sleep.

Optional future fields:

```kotlin
@ColumnInfo(name = "wind_down_started_at") val windDownStartedAt: Long? = null
@ColumnInfo(name = "put_down_at") val putDownAt: Long? = null
@ColumnInfo(name = "fell_asleep_at") val fellAsleepAt: Long? = null
```

Alternative: keep `SleepEntity.startTime` as actual sleep start and add a separate settling/routine entity:

```kotlin
@Entity(tableName = "sleep_attempts")
data class SleepAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "baby_id") val babyId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "result_sleep_record_id") val resultSleepRecordId: Long? = null,
    @ColumnInfo(name = "method") val method: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null
)
```

Algorithm benefit:

- Calculates sleep onset latency.
- Measures whether a suggested window was too early or too late.
- Avoids confusing “we started bedtime routine at 7:00” with “baby fell asleep at 7:35.”

MVP recommendation: do not add these fields immediately unless the app already has a bedtime routine/timer feature.

---

### 5B.6 Add a generic feeding model

The current app only stores breastfeeding. That is okay for a breastfeeding-only MVP, but a sleep scheduler should eventually reason about all milk feeds and solids.

Recommended architecture:

```kotlin
@Entity(
    tableName = "feeding_sessions",
    indices = [Index("baby_id"), Index("start_time"), Index("feeding_type")]
)
data class FeedingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "baby_id") val babyId: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "feeding_type") val feedingType: String,
    @ColumnInfo(name = "volume_ml") val volumeMl: Double? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

enum class FeedingType {
    BREAST,
    BOTTLE_BREASTMILK,
    FORMULA,
    SOLIDS,
    OTHER
}
```

Then keep breastfeeding-specific details in a related table:

```kotlin
@Entity(
    tableName = "breastfeeding_details",
    foreignKeys = [
        ForeignKey(
            entity = FeedingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["feeding_session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("feeding_session_id")]
)
data class BreastfeedingDetailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "feeding_session_id") val feedingSessionId: Long,
    @ColumnInfo(name = "starting_side") val startingSide: String,
    @ColumnInfo(name = "switch_time") val switchTime: Long? = null
)
```

Algorithm benefit:

- The sleep recommendation can use a true `lastFeed` signal instead of only `lastBreastfeed`.
- The UI can safely say “feed may be due” instead of “breastfeed may be due.”
- Bottle/formula feeds can use volume when available, while breastfeeding remains time-based without pretending duration equals intake.
- Solids can be tracked without using them as a false promise for better sleep.

Migration path from the current `BreastfeedingEntity`:

1. Create one `FeedingSessionEntity` for each existing `BreastfeedingEntity`.
2. Set `feedingType = BREAST`.
3. Copy `startTime`, `endTime`, and `notes`.
4. Create one `BreastfeedingDetailEntity` for each migrated feed.
5. Keep the old table temporarily or replace it with a compatibility DAO/view.

---

### 5B.7 Improve breastfeeding side tracking only if needed

The current breastfeeding model supports one side switch:

```kotlin
startingSide
switchTime
```

That is enough for many parent-facing trackers. If the app later needs accurate left/right active duration or multiple side switches, model each side interval separately:

```kotlin
@Entity(
    tableName = "breastfeeding_side_segments",
    indices = [Index("feeding_session_id")]
)
data class BreastfeedingSideSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "feeding_session_id") val feedingSessionId: Long,
    @ColumnInfo(name = "side") val side: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null
)
```

Algorithm benefit for sleep scheduling is low. This mainly improves breastfeeding analytics. Do not prioritize this over baby profile, generic feeding, cues, or recommendation feedback.

---

### 5B.8 Add a cue/event model

For babies under 4 months, sleepy and hunger cues are often more useful than clock-based scheduling. The current model only has free-text notes, which are too inconsistent for reliable algorithm input.

Recommended model:

```kotlin
@Entity(
    tableName = "baby_events",
    indices = [Index("baby_id"), Index("timestamp"), Index("event_type")]
)
data class BabyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "baby_id") val babyId: Long,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "intensity") val intensity: Int? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

enum class BabyEventType {
    SLEEPY_CUE,
    HUNGER_CUE,
    FUSSINESS,
    CRYING,
    CALM_ALERT,
    ILLNESS,
    VACCINE,
    TEETHING,
    TRAVEL,
    ROUTINE_DISRUPTION,
    OTHER
}
```

Algorithm benefit:

- Stronger recommendations for newborns and young infants.
- Better distinction between hunger-related waking and sleep-pressure-related waking.
- Ability to lower confidence during illness, vaccine days, teething, travel, or major routine disruption.
- Better explanations: “Suggested earlier because sleepy cues were logged 10 minutes ago.”

MVP approach: start with a small set of quick-tap events: sleepy cue, hunger cue, fussy, sick, vaccine, travel.

---

### 5B.9 Add a recommendation feedback model

A scheduling algorithm improves only if the app records whether the recommendation worked. Without recommendation feedback, the app can personalize from sleep logs, but it cannot directly learn if its suggested window was useful.

Recommended model:

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
    @ColumnInfo(name = "algorithm_version") val algorithmVersion: String,
    @ColumnInfo(name = "context_json") val contextJson: String,
    @ColumnInfo(name = "reasons_json") val reasonsJson: String
)
```

Feedback table:

```kotlin
@Entity(
    tableName = "sleep_recommendation_feedback",
    indices = [Index("recommendation_id"), Index("actual_sleep_record_id")]
)
data class SleepRecommendationFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recommendation_id") val recommendationId: Long,
    @ColumnInfo(name = "actual_sleep_record_id") val actualSleepRecordId: Long? = null,
    @ColumnInfo(name = "user_action") val userAction: String,
    @ColumnInfo(name = "feedback") val feedback: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

enum class RecommendationUserAction {
    ACCEPTED,
    DISMISSED,
    SNOOZED,
    IGNORED,
    STARTED_SLEEP_TIMER,
    EDITED_WINDOW
}
```

Algorithm benefit:

- Learns whether the baby usually falls asleep near the suggested window.
- Detects if recommendations are consistently too early or too late.
- Allows offline evaluation of algorithm versions.
- Makes it possible to show transparent explanations and debug recommendations.

Recommended feedback metrics:

```text
actual_sleep_start - recommended_window_start
actual_sleep_start - best_estimate
sleep_onset_success = actual_sleep_start within recommendation window
recommendation_ignored = no sleep logged within X minutes after window
post_recommendation_nap_duration
```

---

### 5B.10 Add night waking/interruption tracking

A single `NIGHT_SLEEP` record from 19:30 to 06:30 may hide multiple wakes. For some app features this is fine, but a sleep-quality algorithm should not treat it as 11 hours of uninterrupted sleep unless wakings are tracked.

Recommended future model:

```kotlin
@Entity(
    tableName = "sleep_interruptions",
    indices = [Index("sleep_record_id"), Index("start_time")]
)
data class SleepInterruptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sleep_record_id") val sleepRecordId: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "reason") val reason: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null
)
```

Possible `reason` values:

```kotlin
enum class WakeReason {
    FEED,
    DIAPER,
    COMFORT,
    SICK,
    NOISE,
    UNKNOWN
}
```

Algorithm benefit:

- Calculates night waking frequency.
- Better predicts morning sleep debt.
- Avoids saying “slept through the night” when the baby had several logged interruptions.
- Allows differentiation between feeding wakes and non-feeding wakes.

MVP alternative: add a simple optional field to `SleepEntity`:

```kotlin
@ColumnInfo(name = "wake_count") val wakeCount: Int? = null
```

This is less precise but much easier to implement.

---

### 5B.11 Improve pause modeling

Current breastfeeding pause fields:

```kotlin
pausedAt: Long?
pausedDurationMs: Long
```

This is enough for displaying an active timer and total active duration. However, it cannot reconstruct multiple pause intervals accurately for analytics.

Recommended only if detailed analytics are needed:

```kotlin
@Entity(
    tableName = "session_pauses",
    indices = [Index("session_type"), Index("session_id")]
)
data class SessionPauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_type") val sessionType: String,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "paused_at") val pausedAt: Long,
    @ColumnInfo(name = "resumed_at") val resumedAt: Long? = null
)

enum class PausableSessionType {
    BREASTFEEDING,
    BOTTLE_FEED,
    SLEEP_ATTEMPT
}
```

Algorithm benefit for sleep scheduling is low to medium. It is more useful for accurate feed/session timers than for deciding sleep windows.

---

### 5B.12 Add user preferences and constraints

Not every good biological sleep window is practical. The algorithm should eventually account for caregiver constraints without pretending they are biological needs.

Recommended model:

```kotlin
@Entity(tableName = "sleep_schedule_preferences")
data class SleepSchedulePreferenceEntity(
    @PrimaryKey val babyId: Long,
    @ColumnInfo(name = "preferred_morning_start_minute") val preferredMorningStartMinute: Int? = null,
    @ColumnInfo(name = "preferred_bedtime_minute") val preferredBedtimeMinute: Int? = null,
    @ColumnInfo(name = "nap_routine_duration_minutes") val napRoutineDurationMinutes: Int = 10,
    @ColumnInfo(name = "bedtime_routine_duration_minutes") val bedtimeRoutineDurationMinutes: Int = 20,
    @ColumnInfo(name = "quiet_hours_start_minute") val quietHoursStartMinute: Int? = null,
    @ColumnInfo(name = "quiet_hours_end_minute") val quietHoursEndMinute: Int? = null
)
```

Algorithm benefit:

- Can suggest “start routine at 19:10” instead of only “sleep at 19:30.”
- Can explain when the recommendation is biologically ideal but outside parent preference.
- Supports future daycare/workday constraints.

Important: keep preference constraints separate from the baby’s physiological sleep model.

---

### 5B.13 Add daily aggregate / feature cache for performance

The algorithm can compute everything from raw logs, but repeated rolling-window calculations can become expensive and hard to debug.

Recommended optional cache:

```kotlin
@Entity(
    tableName = "daily_sleep_features",
    primaryKeys = ["baby_id", "local_date"]
)
data class DailySleepFeatureEntity(
    @ColumnInfo(name = "baby_id") val babyId: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "timezone_id") val timezoneId: String,
    @ColumnInfo(name = "total_sleep_minutes") val totalSleepMinutes: Int,
    @ColumnInfo(name = "day_sleep_minutes") val daySleepMinutes: Int,
    @ColumnInfo(name = "night_sleep_minutes") val nightSleepMinutes: Int,
    @ColumnInfo(name = "nap_count") val napCount: Int,
    @ColumnInfo(name = "first_wake_minute") val firstWakeMinute: Int? = null,
    @ColumnInfo(name = "bedtime_minute") val bedtimeMinute: Int? = null,
    @ColumnInfo(name = "last_computed_at") val lastComputedAt: Long
)
```

Algorithm benefit:

- Faster home-screen recommendations.
- Easier trend charts.
- Easier debugging because derived values are inspectable.

Rule: raw logs remain the source of truth. Recompute aggregates whenever sleep/feed records change.

---

### 5B.14 Add data quality flags instead of silently ignoring records

Some records should be used with lower confidence rather than discarded.

Recommended model:

```kotlin
@Entity(
    tableName = "data_quality_flags",
    indices = [Index("entity_type"), Index("entity_id")]
)
data class DataQualityFlagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "flag_type") val flagType: String,
    @ColumnInfo(name = "severity") val severity: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

Examples:

```text
NEGATIVE_DURATION
UNUSUALLY_LONG_NAP
OVERLAPPING_SLEEP
OPEN_RECORD_TOO_LONG
MISSING_TIMEZONE
POSSIBLE_DUPLICATE
```

Algorithm benefit:

- Recommendation confidence can be lowered transparently.
- The UI can ask the caregiver to fix suspicious records.
- The algorithm remains robust instead of failing on messy real-world data.

---

### 5B.15 Recommended target architecture

A maintainable architecture should separate persistence from algorithm logic:

```text
Room entities
  ↓
Repository queries
  ↓
Domain intervals and events
  ↓
Feature extraction
  ↓
Candidate generation
  ↓
Candidate scoring
  ↓
Recommendation entity + user-facing view model
  ↓
Feedback loop after actual sleep is logged
```

Recommended package separation:

```text
data/local/entity
 data/local/dao
 data/repository
 domain/model
 domain/sleep/feature
 domain/sleep/recommendation
 domain/sleep/scoring
 ui/sleep
```

Keep these rules:

1. Room entities should represent stored facts.
2. Domain models should represent validated intervals and derived signals.
3. Recommendation models should store algorithm outputs and explanations.
4. UI models should format labels, emojis, and caregiver-friendly text.

---

### 5B.16 Suggested migration roadmap

#### Phase 1 — low-risk MVP improvements

Add:

```text
BabyProfileEntity
babyId on sleep records
babyId on breastfeeding records
createdAt / updatedAt
stable enum storage
indices on babyId, startTime, endTime
```

This phase gives the algorithm enough structure to be safe and scalable while changing very little UX.

#### Phase 2 — better recommendation quality

Add:

```text
Sleep location
Sleep source
Generic FeedingSessionEntity
BabyEventEntity for cues/context
SleepRecommendationEntity
SleepRecommendationFeedbackEntity
```

This phase turns the feature from a static calculator into a learning recommendation engine.

#### Phase 3 — advanced analytics

Add:

```text
Sleep interruptions / wake count
Sleep attempts / put-down time / sleep onset latency
Daily feature cache
Pause segment table
Caregiver schedule preferences
```

This phase supports stronger personalization, charts, and algorithm evaluation.

---

### 5B.17 Minimum recommended revised models

If only a small model update is possible before building the sleep algorithm, prioritize this version.

```kotlin
@Entity(tableName = "babies")
data class BabyProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: Long,
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,
    @ColumnInfo(name = "timezone_id") val timezoneId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

```kotlin
@Entity(
    tableName = "sleep_records",
    indices = [Index("baby_id"), Index("start_time"), Index("end_time")]
)
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "baby_id") val babyId: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "sleep_type") val sleepType: String,
    @ColumnInfo(name = "source") val source: String = SleepSource.MANUAL.name,
    @ColumnInfo(name = "location") val location: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

```kotlin
@Entity(
    tableName = "breastfeeding_sessions",
    indices = [Index("baby_id"), Index("start_time"), Index("end_time")]
)
data class BreastfeedingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "baby_id") val babyId: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "starting_side") val startingSide: String,
    @ColumnInfo(name = "switch_time") val switchTime: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "paused_at") val pausedAt: Long? = null,
    @ColumnInfo(name = "paused_duration_ms") val pausedDurationMs: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
```

This minimum revision keeps your current app structure almost intact while making the sleep algorithm much easier to build correctly.

---

### 5B.18 Modeling decisions to avoid

Avoid these because they can produce unsafe or misleading recommendations:

1. **Do not infer milk intake from breastfeeding duration.** Duration is not volume.
2. **Do not store UI labels as algorithm values.** Store stable enum names/codes.
3. **Do not treat one long `NIGHT_SLEEP` record as uninterrupted sleep unless wakings are tracked.**
4. **Do not use UTC calendar days for sleep summaries.** Use the baby’s local timezone.
5. **Do not make wake windows hard constraints.** Store recommendations as windows with confidence.
6. **Do not let recommendation data overwrite raw logs.** Recommendations and actual sleep records should be separate.
7. **Do not require every advanced field before shipping.** Missing fields should lower confidence, not block the MVP.



---

## 6. Derived features

For the complete algorithm, derive the following features. With the current app models, the MVP can calculate the sleep and breastfeeding features directly; cue, location, source, bottle, formula, and solids-related features require future fields or must be treated as unavailable/low confidence.

For each calculation, derive:

```text
age_days
corrected_age_days
last_wake_time
last_sleep_duration_minutes
time_awake_minutes
sleep_last_24h_minutes
sleep_last_48h_minutes
sleep_last_7d_avg_minutes
day_sleep_today_minutes
night_sleep_last_night_minutes
nap_count_today
median_nap_duration_7d
median_morning_wake_7d
median_bedtime_7d
sleep_onset_latency_recent
night_wakings_recent
last_feed_time
time_since_feed_minutes
median_feed_interval_by_time_of_day
breastfeeding_due_probability
feeding_due_probability_if_other_feed_models_exist
sleepy_cue_score
hunger_cue_score
```

---

## 7. Age priors for MVP

These priors are intentionally broad. The algorithm should update them with the baby’s own data.

| Age band | Total sleep target | Usual naps/day prior | Wake interval prior | Scheduling rule |
|---:|---:|---:|---:|---|
| 0–6 weeks | ~14–17+ h, highly variable | Many fragmented sleeps | 45–75 min | No clock schedule; feed/cue-led sleep opportunities. |
| 6–12 weeks | ~14–17 h, variable | 4–6 | 60–90 min | Begin gentle prediction; do not enforce schedule. |
| 3–4 months | ~13–16 h | 3–5 | 75–120 min | Use windows; protect against overtiredness. |
| 4–5 months | 12–16 h | 3–4 | 90–150 min | Calculate next nap window from wake time + sleep debt. |
| 5–6 months | 12–16 h | 3 | 120–180 min | Support 3 naps; third nap may be short. |
| 6–8 months | 12–16 h | 2–3 | 135–210 min | Detect 3→2 nap transition. |
| 9–12 months | 12–16 h, often 12–14 h by 12 mo | 2 | 180–240 min | Default 2 naps; bedtime from second nap end. |

**Important:** These wake intervals are engineering priors, not medical rules. They should be learned per baby.

---

## 8. Personalization method

### 8.1 Bayesian-style target sleep estimate

Use age-based targets until there are enough logs, then personalize.

```text
age_prior_target = midpoint(age_band_total_sleep_range)
observed_target = median(total_sleep_24h over last 7–14 valid days)

confidence = min(valid_log_days / 14, 1.0)

personalized_target =
  (1 - confidence) * age_prior_target
  + confidence * clamp(observed_target, age_min_safe, age_max_safe)
```

Recommended clamps:

- 0–3 months: use broad warning thresholds, not strict clamps.
- 4–12 months: clamp broadly around AASM range unless clinician settings override.

### 8.2 Baby-specific wake tolerance

```text
successful_wake_intervals =
  wake intervals before naps/bedtime where:
    sleep_onset_latency <= 20 min
    AND sleep_duration >= minimum_expected_sleep
    AND no major disruption flag

baby_wake_p50 = median(successful_wake_intervals over last 7–14 days)
baby_wake_p25 = 25th percentile
baby_wake_p75 = 75th percentile
```

Then blend with age prior:

```text
wake_target =
  0.60 * age_prior_wake_midpoint
  + 0.40 * baby_wake_p50
```

After 14+ valid days, shift to:

```text
wake_target =
  0.30 * age_prior_wake_midpoint
  + 0.70 * baby_wake_p50
```

---

## 9. Candidate generation

Generate candidate sleep start times at 5-minute intervals over the next 3–5 hours.

```pseudo
function generateCandidates(now, lastWake, ageBand):
    earliest = lastWake + ageBand.wakeMin
    latest   = lastWake + ageBand.wakeMax

    if sleepDebtHigh:
        earliest -= 15 minutes
        latest   -= 20 minutes

    if previousNapShort:
        earliest -= 10 minutes
        latest   -= 15 minutes

    if previousNapLong:
        earliest += 10 minutes

    if sleepyCueScoreHigh:
        earliest = min(earliest, now)
        latest = min(latest, now + 30 minutes)

    return every 5 minutes between max(now, earliest) and latest
```

---

## 10. Candidate scoring

A practical scoring formula:

```text
score(candidate) =
  0.25 * wake_interval_fit
+ 0.20 * sleep_debt_fit
+ 0.15 * circadian_fit
+ 0.15 * nap_budget_fit
+ 0.10 * feed_fit
+ 0.10 * baby_history_fit
+ 0.05 * parent_constraint_fit
```

### 10.1 Wake interval fit

```text
wake_interval_fit =
  1 - abs(candidate_wake_minutes - wake_target_minutes) / wake_tolerance_range
```

Clamp between 0 and 1.

### 10.2 Sleep debt fit

```text
sleep_debt_minutes = personalized_target_24h - sleep_last_24h

if sleep_debt_minutes > 60:
    earlier candidates score higher
elif sleep_debt_minutes < -60:
    later candidates score higher
else:
    neutral
```

### 10.3 Circadian fit

Use only after the baby is developmentally old enough, because circadian rhythm is immature in newborns.

For 0–6 weeks:

```text
circadian_weight = 0.00
```

For 6–12 weeks:

```text
circadian_weight = 0.05
```

For 3–6 months:

```text
circadian_weight = 0.10
```

For 6–12 months:

```text
circadian_weight = 0.15
```

Circadian fit can reward:

- Morning nap in midmorning.
- Afternoon nap early/mid-afternoon.
- Avoiding late third nap that pushes bedtime too late.
- Bedtime near learned bedtime range.

### 10.4 Nap budget fit

```text
remaining_day_sleep_budget =
  personalized_day_sleep_target - day_sleep_today
```

If remaining budget is low, prefer shorter/optional nap or earlier bedtime. If remaining budget is high, prefer a nap window rather than forcing bedtime too early.

### 10.5 Feed fit

Feed fit should not decide sleep alone. It only prevents recommendations that conflict with likely hunger.

```text
next_feed_due_estimate =
  last_feed_time + personalized_feed_interval

if candidate_sleep_start > next_feed_due_estimate - feed_buffer:
    feed_fit = 0.4
    attachRecommendation("Offer feed before sleep if hunger cues appear.")
else:
    feed_fit = 1.0
```

For newborns, use wider feed sensitivity and clinician max feed interval if configured.

### 10.6 Baby history fit

Reward candidate windows similar to successful historical nap/bedtime starts.

```text
baby_history_fit =
  similarity(candidate_time_of_day, successful_sleep_starts_last_14d)
```

---

## 11. Output structure

The app should output a window, not only a single time.

```json
{
  "recommendationType": "nap | bedtime | optional_catnap | no_sleep_yet",
  "windowStart": "2026-06-03T09:20:00-03:00",
  "windowEnd": "2026-06-03T09:50:00-03:00",
  "bestEstimate": "2026-06-03T09:35:00-03:00",
  "confidence": "low | medium | high",
  "reasons": [
    "2h10 since last wake",
    "previous nap was 28 min, shorter than usual",
    "last 24h sleep is 55 min below target",
    "feed may be due before this sleep window"
  ],
  "safetyPrompt": "Place baby on back in a firm, flat, empty sleep space.",
  "feedPrompt": "Offer a feed before the nap if hunger cues appear.",
  "nextCheckInMinutes": 15
}
```

---

## 12. Nap transition detection

### 12.1 4→3 naps

Possible signs:

- Baby is older than ~4 months corrected.
- Fourth nap repeatedly starts very late.
- Fourth nap causes bedtime to drift later.
- Total sleep remains adequate without fourth nap.
- Sleep onset latency for fourth nap is repeatedly high.

### 12.2 3→2 naps

Possible signs:

- Baby is usually 6–9 months corrected.
- Third nap is refused or pushes bedtime late for 7–10 days.
- First two naps are lengthening.
- Baby can tolerate longer wake periods without extreme fussiness.
- Night sleep remains stable or improves with two naps.

### 12.3 2→1 naps

Usually more relevant after 12 months, but the app can detect early attempts cautiously.

Possible signs:

- Baby is close to or older than 12 months corrected.
- Morning nap is consistently refused for 10–14 days.
- One nap becomes long enough to preserve total sleep.
- Bedtime can remain reasonable.

**Algorithm rule:** Do not trigger transition advice from one or two bad days. Require a consistent pattern.

---

## 13. Feeding-aware scheduling logic

### 13.1 Under 4 months

- Schedule should be **feed/cue-led**.
- The app predicts “likely sleep soon” rather than “nap at 10:00.”
- If baby has been awake beyond the age prior and recently fed, suggest calming sleep opportunity.
- If baby has been awake beyond the age prior and feed is likely due, suggest feeding first if hunger cues appear.

### 13.2 4–6 months

- Begin using nap windows.
- Use feed timing as a constraint:
  - Do not schedule a nap so late that hunger is likely to interrupt it.
  - Do not push a tired baby awake only to align with a feed unless clinician guidance requires it.

### 13.3 6–12 months

- Milk remains important; solids are complementary in much of this period.
- Night waking/night feeding can remain normal.
- App can show patterns:
  - “When last feed is >3h before bedtime, first wake tends to happen earlier.”
  - “Long late nap correlates with later bedtime.”
- Avoid prescriptive claims such as “feed more solids to sleep through the night.”

---

## 14. Handling missing, noisy, or conflicting data

### 14.1 Manual logs

Parent-entered sleep logs often miss brief wakes. Use them for scheduling, but do not overinterpret wake frequency.

### 14.2 Wearables/actigraphy

Actigraphy can over-detect movement as waking in infants because active infant sleep includes movement. Research comparing actigraphy and parent diaries found more night wakes by actigraphy and longer sleep durations by diaries; discrepancies may reflect movement during active sleep and self-soothing, not necessarily true wakefulness.[^hall]

### 14.3 Recommended data-quality flags

```text
low_confidence if:
  fewer than 3 valid log days
  missing last wake time
  illness/travel/vaccine flag active
  sleep log source changed recently
  nap skipped due to external event
  feed log missing for newborn
```

---

## 15. Red flags and escalation

The app should recommend contacting a healthcare professional if caregivers report:

- Poor weight gain or clinician concern about feeding.
- Baby difficult to wake for feeds.
- Fewer wet diapers than expected.
- Breathing pauses, blue color, persistent noisy breathing, or repeated choking.
- Frequent snoring or labored breathing.
- Extreme lethargy.
- Persistent sleep far outside broad age ranges with developmental/feeding concerns.
- Any concern in a premature or medically complex infant.
- Caregiver exhaustion or unsafe sleep risk due to sleep deprivation.

---

## 16. Suggested MVP implementation

### 16.1 MVP feature set

1. Age-band prior table.
2. Sleep/feeding log ingestion.
3. Rolling 7-day sleep metrics.
4. Next nap/bedtime window recommendation.
5. Feeding-aware prompt.
6. Safe sleep prompt.
7. Confidence indicator.
8. Explanation text.

### 16.2 MVP pseudocode

```pseudo
function recommendNextSleep(profile, sleepLogs, feedLogs, cues, now):
    age = correctedAge(profile, now)
    band = getAgeBand(age)

    metrics = computeSleepMetrics(sleepLogs, now)
    feedMetrics = computeFeedMetrics(feedLogs, now)
    cueScores = computeCueScores(cues, now)

    targetSleep = personalizeSleepTarget(band, metrics)
    wakeTarget = personalizeWakeTarget(band, metrics)

    if age < 42 days:
        mode = "cue_led"
    else:
        mode = "window_based"

    candidates = generateCandidates(
        now = now,
        lastWake = metrics.lastWakeTime,
        band = band,
        targetSleep = targetSleep,
        wakeTarget = wakeTarget,
        metrics = metrics,
        cues = cueScores
    )

    scored = []
    for candidate in candidates:
        score = scoreCandidate(
            candidate,
            band,
            metrics,
            feedMetrics,
            cueScores,
            targetSleep,
            wakeTarget,
            profile.parentConstraints
        )
        scored.append({candidate, score})

    best = max(scored by score)

    recommendation = buildRecommendation(best, scored, metrics, feedMetrics, cues)

    recommendation.safetyPrompt = safeSleepPrompt(profile)
    recommendation.confidence = computeConfidence(metrics, feedMetrics, profile, cues)

    return recommendation
```

### 16.3 Candidate explanation generator

```pseudo
function buildReasons(best, metrics, feedMetrics, cues):
    reasons = []

    if metrics.timeAwake > metrics.personalWakeP50:
        reasons.add("Baby has been awake longer than their recent usual interval.")

    if metrics.previousNapDuration < metrics.medianNapDuration * 0.6:
        reasons.add("Previous nap was shorter than usual, so the next window is earlier.")

    if metrics.sleepLast24h < metrics.targetSleep - 45min:
        reasons.add("Sleep in the last 24 hours is below target.")

    if feedMetrics.feedDueSoon:
        reasons.add("A feed may be due before this sleep window.")

    if cues.sleepyCueScoreHigh:
        reasons.add("Sleepy cues were logged recently.")

    return reasons
```

---

## 17. Product copy guidelines

Avoid:

- “Your baby must sleep now.”
- “Ideal nap time.”
- “Your baby is sleep deprived.”
- “Stop night feeds.”
- “A 6-month-old should sleep through the night.”

Prefer:

- “Suggested sleep window.”
- “Based on recent patterns.”
- “Offer a nap if sleepy cues appear.”
- “Consider starting wind-down.”
- “Feed first if hunger cues appear.”
- “Night waking can still be normal at this age.”

---

## 18. Validation plan

### 18.1 Offline validation

Use de-identified historical logs and evaluate:

- Mean absolute error between recommended sleep window and actual sleep start.
- Percentage of actual sleep starts inside suggested window.
- Rate of recommendations conflicting with feed due.
- User correction rate.
- False “transition readiness” rate.

### 18.2 Online validation

Track:

- Caregiver acceptance.
- Sleep onset latency after accepted recommendation.
- Night waking trend.
- Total sleep trend.
- Caregiver stress rating.
- Safety prompt exposure and unsafe location reduction.

### 18.3 Clinical safety review

Before release, have a pediatrician or pediatric sleep specialist review:

- Age priors.
- Red-flag logic.
- Feeding constraints.
- Safe sleep prompts.
- Copy that might imply medical advice.

---

## 19. Recommended algorithm defaults

```json
{
  "recommendExactTime": false,
  "recommendWindowMinutes": 30,
  "candidateStepMinutes": 5,
  "minValidDaysForPersonalization": 3,
  "fullPersonalizationDays": 14,
  "sleepDebtEarlierShiftMinutes": 15,
  "shortNapThresholdRatio": 0.6,
  "sleepOnsetLatencySuccessMinutes": 20,
  "napTransitionMinimumPatternDays": 7,
  "safeSleepPromptAlways": true,
  "responsiveFeedingOverride": true
}
```

---

## 20. Main conclusions for development

1. Use **sleep windows**, not exact times.
2. For 0–3 months, use **cue-led prediction**, not a fixed schedule.
3. From 4–12 months, anchor total sleep to **12–16 h/24h** while personalizing.
4. Learn from the baby’s own data using rolling medians and successful sleep intervals.
5. Treat wake windows as **soft priors**, not rules.
6. Feed timing should prevent bad recommendations but should not override hunger/fullness cues.
7. Night waking and night feeding can remain normal throughout the first year.
8. Always include safe-sleep guidance.
9. Require repeated patterns before recommending nap transitions.
10. Show confidence and reasoning so caregivers understand the recommendation.


---

## 21. Kotlin implementation sketch for current Room models

This sketch shows how the algorithm can be implemented without waiting for new tables.

### 21.1 Repository query shape

The recommendation engine needs recent sleep and breastfeeding records, not the entire database.

```kotlin
// Suggested DAO windows
val fromMillis = nowMillis - Duration.ofDays(14).toMillis()
val sleepRecords = sleepDao.getSleepRecordsSince(fromMillis)
val breastfeeds = breastfeedingDao.getSessionsSince(fromMillis)
```

For some metrics, 7 days is enough. Keeping 14 days available gives better medians and transition detection.

### 21.2 Convert Room entities to domain intervals

```kotlin
fun SleepEntity.toCompletedIntervalOrNull(): SleepInterval? {
    val end = endTime ?: return null
    val parsedType = parseSleepType(sleepType) ?: return null
    if (end <= startTime) return null
    return SleepInterval(
        id = id,
        startMillis = startTime,
        endMillis = end,
        type = parsedType,
        notes = notes
    )
}

fun BreastfeedingEntity.toBreastfeedInterval(nowMillis: Long): BreastfeedInterval {
    val effectiveEnd = endTime ?: pausedAt ?: nowMillis
    val rawDuration = (effectiveEnd - startTime).coerceAtLeast(0)
    val activeDuration = (rawDuration - pausedDurationMs).coerceAtLeast(0)
    return BreastfeedInterval(
        id = id,
        startMillis = startTime,
        endMillis = endTime,
        activeDurationMillis = activeDuration,
        startingSide = startingSide,
        switchedSide = switchTime != null,
        notes = notes
    )
}
```

### 21.3 Sleep metrics from `SleepEntity`

```kotlin
fun computeSleepMetrics(
    sleeps: List<SleepInterval>,
    nowMillis: Long,
    zoneId: ZoneId
): SleepMetrics {
    val lastSleep = sleeps.maxByOrNull { it.endMillis }
    val lastWake = lastSleep?.endMillis

    val sleepLast24h = sleeps.sumOf {
        overlapMs(
            it.startMillis,
            it.endMillis,
            nowMillis - Duration.ofHours(24).toMillis(),
            nowMillis
        )
    }

    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val todayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val tomorrowStart = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    val napsToday = sleeps.filter { it.type == SleepType.NAP }
        .filter { overlapMs(it.startMillis, it.endMillis, todayStart, tomorrowStart) > 0 }

    val daySleepToday = napsToday.sumOf {
        overlapMs(it.startMillis, it.endMillis, todayStart, tomorrowStart)
    }

    val napDurations = sleeps
        .filter { it.type == SleepType.NAP }
        .map { it.durationMillis }
        .filter { it in Duration.ofMinutes(10).toMillis()..Duration.ofHours(4).toMillis() }

    return SleepMetrics(
        lastWakeMillis = lastWake,
        lastSleepDurationMillis = lastSleep?.durationMillis,
        sleepLast24hMillis = sleepLast24h,
        daySleepTodayMillis = daySleepToday,
        nightSleepLastNightMillis = computeLastNightSleep(sleeps, nowMillis, zoneId),
        napCountToday = napsToday.size,
        medianNapDurationMillis = napDurations.medianOrNull(),
        medianBedtimeMinuteOfDay = computeMedianBedtimeMinute(sleeps, zoneId),
        medianMorningWakeMinuteOfDay = computeMedianMorningWakeMinute(sleeps, zoneId),
        validLogDays = computeValidLogDays(sleeps, zoneId, nowMillis)
    )
}
```

### 21.4 Breastfeeding metrics from `BreastfeedingEntity`

```kotlin
fun computeBreastfeedingMetrics(
    feeds: List<BreastfeedInterval>,
    nowMillis: Long
): BreastfeedingMetrics {
    val latest = feeds.maxByOrNull { it.startMillis }
    val completed = feeds.filter { it.endMillis != null }
        .sortedBy { it.startMillis }

    val intervals = completed.zipWithNext { a, b -> b.startMillis - a.startMillis }
        .filter { it in Duration.ofMinutes(30).toMillis()..Duration.ofHours(6).toMillis() }

    val medianInterval = intervals.medianOrNull()
    val lastStart = latest?.startMillis
    val lastEnd = latest?.endMillis

    return BreastfeedingMetrics(
        lastFeedStartMillis = lastStart,
        lastFeedEndMillis = lastEnd,
        activeFeedInProgress = latest?.endMillis == null,
        medianStartToStartIntervalMillis = medianInterval,
        minutesSinceFeedStart = lastStart?.let { (nowMillis - it) / 60_000 },
        minutesSinceFeedEnd = lastEnd?.let { (nowMillis - it) / 60_000 },
        nextFeedDueEstimateMillis = if (lastStart != null && medianInterval != null) {
            lastStart + medianInterval
        } else null
    )
}
```

### 21.5 Candidate scoring with current data only

```kotlin
fun scoreCandidateWithCurrentData(
    candidateStart: Long,
    metrics: SleepMetrics,
    feed: BreastfeedingMetrics,
    band: AgeBand,
    nowMillis: Long
): Double {
    val wakeMinutes = ((candidateStart - (metrics.lastWakeMillis ?: nowMillis)) / 60_000).toDouble()
    val wakeFit = fitToRange(wakeMinutes, band.wakeMinMinutes, band.wakeMaxMinutes)

    val sleepDebtFit = computeSleepDebtFit(candidateStart, metrics, band, nowMillis)
    val napBudgetFit = computeNapBudgetFit(candidateStart, metrics, band)
    val circadianFit = computeCircadianFit(candidateStart, metrics, band)
    val feedFit = computeBreastfeedFit(candidateStart, feed)
    val historyFit = computeHistoryFit(candidateStart, metrics)

    return 0.30 * wakeFit +
           0.20 * sleepDebtFit +
           0.15 * napBudgetFit +
           0.15 * circadianFit +
           0.10 * feedFit +
           0.10 * historyFit
}
```

Because the current app has no explicit cue/event model, the MVP should redistribute cue-score weight into wake interval, sleep debt, and history. When cue fields are added, they can become an additional high-impact input, especially for 0–4 months.

### 21.6 MVP confidence rules for the current models

```text
HIGH confidence:
  7+ valid sleep log days
  last completed sleep exists
  no open sleep record
  breastfeeding logs available if baby is under 4 months
  recommendation is not during unusual overlapping records

MEDIUM confidence:
  3–6 valid sleep log days
  last completed sleep exists
  breastfeeding data incomplete but baby is older than 4 months

LOW confidence:
  fewer than 3 valid sleep log days
  no known last wake time
  open sleep record
  active feed record
  many invalid/overlapping records
  baby is 0–6 weeks and app has no cue data
```

### 21.7 Recommended MVP user-facing output using current models

Example:

```text
Suggested nap window: 9:20–9:50 AM
Best estimate: 9:35 AM
Confidence: medium

Why:
- Baby has been awake for 2h 05m.
- The previous nap was shorter than recent naps.
- Sleep in the last 24 hours is slightly below the recent target.
- A breastfeed may be due soon, so offer a feed first if hunger cues appear.

Safety:
Place baby on their back in a firm, flat, empty sleep space.
```

The wording should say “breastfeed may be due” when only breastfeeding records are available. If bottle/formula/solids are added later, the wording can become “feed may be due.”


---

## References

[^aasm]: Paruthi S, Brooks LJ, D’Ambrosio C, Hall WA, Kotagal S, Lloyd RM, et al. *Recommended Amount of Sleep for Pediatric Populations: A Consensus Statement of the American Academy of Sleep Medicine.* Journal of Clinical Sleep Medicine. 2016. https://sleepeducation.org/wp-content/uploads/2021/04/pediatric-sleep-consensus.pdf

[^nsf]: Hirshkowitz M, Whiton K, Albert SM, Alessi C, Bruni O, DonCarlos L, et al. *National Sleep Foundation’s sleep time duration recommendations: methodology and results summary.* Sleep Health. 2015. https://pubmed.ncbi.nlm.nih.gov/29073412/

[^aap_safe]: American Academy of Pediatrics. *Safe Sleep.* https://www.aap.org/en/patient-care/safe-sleep/

[^oconnor]: O’Connor C, Ventura S, Proietti J, O’Sullivan MP, Boylan GB. *Sleep and infant development in the first year.* Pediatric Research. 2026. https://www.nature.com/articles/s41390-026-04780-4

[^dias]: Dias CC, Figueiredo B, Rocha M, Field T. *Reference values and changes in infant sleep-wake behaviour during the first 12 months of life: a systematic review.* Journal of Sleep Research. 2018. https://ciencia.ucp.pt/en/publications/reference-values-and-changes-in-infant-sleep-wake-behaviour-durin/

[^bruni]: Bruni O, Baumgartner E, Sette S, Ancona M, Caso G, Di Cosimo ME, et al. *Longitudinal Study of Sleep Behavior in Normal Infants during the First Year of Life.* Journal of Clinical Sleep Medicine. 2014. Summary: https://www.babysleep.com/research/study-of-sleep-behavior-during-the-first-year-of-life/

[^cps]: Canadian Paediatric Society. *Healthy sleep for your baby and child.* https://caringforkids.cps.ca/handouts/pregnancy-and-babies/healthy_sleep_for_your_baby_and_child

[^canapari]: Canapari C. *Do Wake Windows Help Babies and Kids Nap Better?* https://drcraigcanapari.com/do-wake-windows-help-kids-nap-better/

[^cdc_bf]: Centers for Disease Control and Prevention. *How Much and How Often to Breastfeed.* https://www.cdc.gov/infant-toddler-nutrition/breastfeeding/how-much-and-how-often.html

[^aap_feed]: American Academy of Pediatrics / HealthyChildren.org. *Is Your Baby Hungry or Full? Responsive Feeding Explained.* https://www.healthychildren.org/English/ages-stages/baby/feeding-nutrition/Pages/Is-Your-Baby-Hungry-or-Full-Responsive-Feeding-Explained.aspx

[^brown]: Brown A, Harries V. *Infant sleep and night feeding patterns during later infancy: association with breastfeeding frequency, daytime complementary food intake, and infant weight.* Breastfeeding Medicine. 2015. https://pubmed.ncbi.nlm.nih.gov/25973527/

[^mindell]: Mindell JA, Telofski LS, Wiegand B, Kurtz ES. *A nightly bedtime routine: impact on sleep in young children and maternal mood.* Sleep. 2009. https://pubmed.ncbi.nlm.nih.gov/19480226/

[^hall]: Hall WA, Liva S, Moynihan M, Saunders R. *A comparison of actigraphy and sleep diaries for infants’ sleep behavior.* Frontiers in Psychiatry. 2015. https://pmc.ncbi.nlm.nih.gov/articles/PMC4325935/
