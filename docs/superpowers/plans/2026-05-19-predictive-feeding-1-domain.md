# Predictive Feeding — Task 1: Settings preferences + `FeedPrediction` domain model

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 4 DataStore preferences (toggle, lead minutes, quiet-hours start, quiet-hours end), centralise tuning constants, and add the `FeedPrediction` domain model. No UI, no behaviour wired up yet.

**Architecture:** Pure data-layer change. Extends `SettingsRepository` interface and `SettingsRepositoryImpl` with four new preferences. Adds two new domain types: `FeedPrediction` (algorithm output) and `PredictionTuning` (constants from the spec).

**Tech Stack:** Kotlin · DataStore Preferences · JUnit 5.

**Branch:** `feat/predictive-feeding-1-domain`
**Linear issue:** Predictive feeding — settings prefs + domain model
**Overview:** [`2026-05-19-predictive-feeding-overview.md`](./2026-05-19-predictive-feeding-overview.md)
**Source spec:** [`docs/superpowers/specs/2026-05-19-predictive-feeding-reminders-design.md`](../specs/2026-05-19-predictive-feeding-reminders-design.md)

---

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/FeedPrediction.kt`
- Create: `app/src/main/java/com/babytracker/domain/model/PredictionTuning.kt`
- Modify: `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt`
- Modify (instrumentation fakes — must implement the new interface members or `compileDebugAndroidTestKotlin` fails):
  - `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt`
  - `app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/SettingsRepositoryPredictivePrefsTest.kt`

## Steps

- [ ] **Step 1: Create `FeedPrediction.kt`**

```kotlin
package com.babytracker.domain.model

import java.time.Instant

data class FeedPrediction(
    val predictedAt: Instant,
    val averageIntervalMinutes: Int,
    val sampleSize: Int,
    val isOverdue: Boolean,
    val minutesUntil: Int,
)
```

- [ ] **Step 2: Create `PredictionTuning.kt`**

```kotlin
package com.babytracker.domain.model

object PredictionTuning {
    const val LOOKBACK_LIMIT: Int = 20
    const val FRESHNESS_HORIZON_HOURS: Long = 12
    const val INTERVAL_MAX_MINUTES: Int = 360
    const val SAMPLE_SIZE_TARGET: Int = 5
    const val SAMPLE_SIZE_MIN: Int = 3
    const val OVERDUE_GRACE_MINUTES: Long = 90
}
```

- [ ] **Step 3: Extend `SettingsRepository` interface**

Add these methods to `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`:

```kotlin
fun getPredictiveEnabled(): Flow<Boolean>
suspend fun setPredictiveEnabled(enabled: Boolean)
fun getPredictiveLeadMinutes(): Flow<Int>
suspend fun setPredictiveLeadMinutes(minutes: Int)
fun getQuietHoursStartMinute(): Flow<Int>
suspend fun setQuietHoursStartMinute(minuteOfDay: Int)
fun getQuietHoursEndMinute(): Flow<Int>
suspend fun setQuietHoursEndMinute(minuteOfDay: Int)
```

- [ ] **Step 4: Write the failing test**

Create `app/src/test/java/com/babytracker/data/repository/SettingsRepositoryPredictivePrefsTest.kt`. Use an in-memory `DataStore<Preferences>` via `PreferenceDataStoreFactory.create` with `@TempDir` (or the existing helper if one already exists in the test source set). Cover:

```kotlin
@Test
fun `predictiveEnabled defaults to false`() = runTest { /* asserts first() == false */ }

@Test
fun `predictiveEnabled persists`() = runTest { /* set true, expect true */ }

@Test
fun `predictiveLeadMinutes defaults to 15`() = runTest { }

@Test
fun `predictiveLeadMinutes persists an allowed value`() = runTest {
    // set(30) -> first() == 30
}

@Test
fun `predictiveLeadMinutes falls back to 15 when stored value is not in the allowlist`() = runTest {
    // set(7) -> first() == 15
    // set(-5) -> first() == 15
    // set(0) -> first() == 15
    // set(120) -> first() == 15
}

@Test
fun `quietHoursStartMinute defaults to 0`() = runTest { }

@Test
fun `quietHoursEndMinute defaults to 480`() = runTest { }

@Test
fun `quietHoursStartMinute persists set value`() = runTest { }
```

Rationale for the allowlist test: `setPredictiveLeadMinutes` ultimately drives `triggerAt = predictedAt - leadMinutes` in the coordinator (Task 5). A negative, zero, or oversized value (e.g., from a buggy caller, migration drift, or a future settings export/import) would either schedule reminders **after** the predicted feed or get cancelled as stale before firing. Constrain to the spec's allowlist `{5, 10, 15, 30}` at the repository boundary and fall back to the default on mismatch.

- [ ] **Step 5: Run test, verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.SettingsRepositoryPredictivePrefsTest"
```

Expected: FAIL with "unresolved reference" for the new methods.

- [ ] **Step 6: Implement the prefs in `SettingsRepositoryImpl`**

In the `private companion object` block of `SettingsRepositoryImpl`, add:

```kotlin
val PREDICTIVE_ENABLED = booleanPreferencesKey("predictive_enabled")
val PREDICTIVE_LEAD_MINUTES = intPreferencesKey("predictive_lead_minutes")
val QUIET_HOURS_START_MINUTE = intPreferencesKey("quiet_hours_start_minute")
val QUIET_HOURS_END_MINUTE = intPreferencesKey("quiet_hours_end_minute")
const val DEFAULT_LEAD_MINUTES = 15
val ALLOWED_LEAD_MINUTES = setOf(5, 10, 15, 30)
```

Append implementations at the bottom of the class:

```kotlin
override fun getPredictiveEnabled(): Flow<Boolean> =
    dataStore.data.map { it[PREDICTIVE_ENABLED] ?: false }

override suspend fun setPredictiveEnabled(enabled: Boolean) {
    dataStore.edit { it[PREDICTIVE_ENABLED] = enabled }
}

override fun getPredictiveLeadMinutes(): Flow<Int> =
    dataStore.data.map { prefs ->
        val stored = prefs[PREDICTIVE_LEAD_MINUTES] ?: DEFAULT_LEAD_MINUTES
        if (stored in ALLOWED_LEAD_MINUTES) stored else DEFAULT_LEAD_MINUTES
    }

override suspend fun setPredictiveLeadMinutes(minutes: Int) {
    // Allowlist enforced at the repository boundary so the coordinator never
    // computes triggerAt with a bogus offset (negative, zero, or huge). Invalid
    // input collapses to the default (15) rather than corrupting persistent state.
    val sanitized = if (minutes in ALLOWED_LEAD_MINUTES) minutes else DEFAULT_LEAD_MINUTES
    dataStore.edit { it[PREDICTIVE_LEAD_MINUTES] = sanitized }
}

override fun getQuietHoursStartMinute(): Flow<Int> =
    dataStore.data.map { it[QUIET_HOURS_START_MINUTE] ?: 0 }

override suspend fun setQuietHoursStartMinute(minuteOfDay: Int) {
    dataStore.edit { it[QUIET_HOURS_START_MINUTE] = minuteOfDay.coerceIn(0, 1439) }
}

override fun getQuietHoursEndMinute(): Flow<Int> =
    dataStore.data.map { it[QUIET_HOURS_END_MINUTE] ?: 480 }

override suspend fun setQuietHoursEndMinute(minuteOfDay: Int) {
    dataStore.edit { it[QUIET_HOURS_END_MINUTE] = minuteOfDay.coerceIn(0, 1439) }
}
```

- [ ] **Step 7: Run tests, verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.SettingsRepositoryPredictivePrefsTest"
```

Expected: PASS.

- [ ] **Step 8: Update existing androidTest fakes**

Two instrumentation tests declare anonymous-object or named-class fakes implementing `SettingsRepository`. Adding 8 abstract members to the interface breaks `compileDebugAndroidTestKotlin` until each fake implements them:

- `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt`
- `app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt`

For each fake, append the 8 new members. Default each to the production default so existing test behaviour is preserved:

```kotlin
override fun getPredictiveEnabled(): Flow<Boolean> = flowOf(false)
override suspend fun setPredictiveEnabled(enabled: Boolean) = Unit
override fun getPredictiveLeadMinutes(): Flow<Int> = flowOf(15)
override suspend fun setPredictiveLeadMinutes(minutes: Int) = Unit
override fun getQuietHoursStartMinute(): Flow<Int> = flowOf(0)
override suspend fun setQuietHoursStartMinute(minuteOfDay: Int) = Unit
override fun getQuietHoursEndMinute(): Flow<Int> = flowOf(480)
override suspend fun setQuietHoursEndMinute(minuteOfDay: Int) = Unit
```

Verify both instrumentation source sets compile cleanly:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: BUILD SUCCESSFUL. Do not let Task 1 ship with broken instrumentation source — the unit-test-only check in Step 7 will not catch this.

- [ ] **Step 9: Lint + detekt**

```bash
./gradlew ktlintFormat detekt
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/FeedPrediction.kt \
        app/src/main/java/com/babytracker/domain/model/PredictionTuning.kt \
        app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt \
        app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt \
        app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt \
        app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt \
        app/src/test/java/com/babytracker/data/repository/SettingsRepositoryPredictivePrefsTest.kt
git commit -m "feat(settings): add predictive-feeding prefs and FeedPrediction model"
```

- [ ] **Step 11: Push branch and open PR**

```bash
git push -u origin feat/predictive-feeding-1-domain
gh pr create --title "feat(settings): predictive-feeding prefs + FeedPrediction model" \
  --body "Adds 4 DataStore prefs (predictiveEnabled, predictiveLeadMinutes, quietHoursStartMinute, quietHoursEndMinute) plus FeedPrediction model and PredictionTuning constants. Task 1 of 6. Spec: docs/superpowers/specs/2026-05-19-predictive-feeding-reminders-design.md"
```
