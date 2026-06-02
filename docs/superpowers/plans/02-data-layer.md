# Data Layer (InventorySettingsRepositoryImpl + InventorySettingsModule) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-75

**Goal:** Implement `InventorySettingsRepository` against the existing `DataStore<Preferences>`, and create the Hilt module that binds the interface to the implementation.

**Architecture:** Reuses the single app-wide `DataStore<Preferences>` provided by `DataStoreModule` (name `baby_tracker_prefs`). Keys are prefixed `stash_` to namespace them away from `SettingsRepositoryImpl`'s keys. Defaults and coercion live in the impl, exactly like `SettingsRepositoryImpl` (e.g. `setNapReminderDelayMinutes` coerces). A new `InventorySettingsModule` binds the interface — kept separate from `RepositoryModule` so the feature is self-contained.

**Tech Stack:** Kotlin, DataStore Preferences 1.1.1, Hilt 2.59, Coroutines Flow.

**Dependencies:** AKA-74 (domain interface must exist).

**Suggested implementation branch:** `feat/aka-75-data-layer`

---

## File Structure

- Create: `app/src/main/java/com/babytracker/data/repository/InventorySettingsRepositoryImpl.kt` — `@Singleton` DataStore-backed impl
- Create: `app/src/main/java/com/babytracker/di/InventorySettingsModule.kt` — `@Binds` the repository interface

Pattern source: `data/repository/SettingsRepositoryImpl.kt` (DataStore key + map + edit pattern, coercion) and `di/RepositoryModule.kt` (`@Binds @Singleton` style).

> **Note for AKA-79:** the `StashExpirationScheduler` binding will be **added** to this same `InventorySettingsModule` later. This plan creates the module with only the repository binding.

---

## Task 1: InventorySettingsRepositoryImpl

**Files:**
- Create: `app/src/main/java/com/babytracker/data/repository/InventorySettingsRepositoryImpl.kt`

DataStore keys and defaults (from spec):

| Key | Type | Default |
|-----|------|---------|
| `stash_expiration_enabled` | Boolean | `false` |
| `stash_expiration_days` | Int | `4` |
| `stash_expiration_notif_enabled` | Boolean | `false` |
| `stash_expiration_notif_time_minutes` | Int | `480` (08:00) |

Coercion rules:
- `setExpirationDays(days)` — coerce to **min 1** (`days.coerceAtLeast(1)`); a 0- or negative-day window is nonsensical and would mark every bag expired.
- `setExpirationNotifTimeMinutes(minuteOfDay)` — coerce into `0..1439` (`coerceIn(0, 1439)`), matching `SettingsRepositoryImpl`'s `MAX_MINUTE_OF_DAY` handling for time-of-day values.

- [ ] **Step 1: Create the implementation**

```kotlin
package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.babytracker.domain.repository.InventorySettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventorySettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : InventorySettingsRepository {

    private companion object {
        val EXPIRATION_ENABLED = booleanPreferencesKey("stash_expiration_enabled")
        val EXPIRATION_DAYS = intPreferencesKey("stash_expiration_days")
        val NOTIF_ENABLED = booleanPreferencesKey("stash_expiration_notif_enabled")
        val NOTIF_TIME_MINUTES = intPreferencesKey("stash_expiration_notif_time_minutes")

        const val DEFAULT_DAYS = 4
        const val MIN_DAYS = 1
        const val DEFAULT_NOTIF_TIME_MINUTES = 480 // 08:00
        const val MAX_MINUTE_OF_DAY = 1439
    }

    override fun getExpirationEnabled(): Flow<Boolean> =
        dataStore.data.map { it[EXPIRATION_ENABLED] ?: false }

    override suspend fun setExpirationEnabled(enabled: Boolean) {
        dataStore.edit { it[EXPIRATION_ENABLED] = enabled }
    }

    override fun getExpirationDays(): Flow<Int> =
        dataStore.data.map { (it[EXPIRATION_DAYS] ?: DEFAULT_DAYS).coerceAtLeast(MIN_DAYS) }

    override suspend fun setExpirationDays(days: Int) {
        dataStore.edit { it[EXPIRATION_DAYS] = days.coerceAtLeast(MIN_DAYS) }
    }

    override fun getExpirationNotifEnabled(): Flow<Boolean> =
        dataStore.data.map { it[NOTIF_ENABLED] ?: false }

    override suspend fun setExpirationNotifEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIF_ENABLED] = enabled }
    }

    override fun getExpirationNotifTimeMinutes(): Flow<Int> =
        dataStore.data.map {
            (it[NOTIF_TIME_MINUTES] ?: DEFAULT_NOTIF_TIME_MINUTES).coerceIn(0, MAX_MINUTE_OF_DAY)
        }

    override suspend fun setExpirationNotifTimeMinutes(minuteOfDay: Int) {
        dataStore.edit { it[NOTIF_TIME_MINUTES] = minuteOfDay.coerceIn(0, MAX_MINUTE_OF_DAY) }
    }
}
```

> Both `getExpirationDays()` and `getExpirationNotifTimeMinutes()` coerce on **read** as well as write, so a value written by an earlier buggy build, a manual DataStore edit, or version skew across upgrades still reads as a safe in-range value before it can reach the scheduler. Read-side clamping is the defensive boundary; write-side clamping keeps stored state clean.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 2: InventorySettingsModule

**Files:**
- Create: `app/src/main/java/com/babytracker/di/InventorySettingsModule.kt`

- [ ] **Step 1: Create the Hilt module**

```kotlin
package com.babytracker.di

import com.babytracker.data.repository.InventorySettingsRepositoryImpl
import com.babytracker.domain.repository.InventorySettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InventorySettingsModule {

    @Binds
    @Singleton
    abstract fun bindInventorySettingsRepository(
        impl: InventorySettingsRepositoryImpl,
    ): InventorySettingsRepository
}
```

- [ ] **Step 2: Verify Hilt graph builds**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Hilt resolves `InventorySettingsRepository` — no missing-binding error)

---

## Task 3: Quality gates and commit

- [ ] **Step 1: ktlint + detekt**

Run: `./gradlew ktlintFormat detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/babytracker/data/repository/InventorySettingsRepositoryImpl.kt \
        app/src/main/java/com/babytracker/di/InventorySettingsModule.kt
git commit -m "feat(inventory): add InventorySettings DataStore repository [AKA-75]"
```

---

## Acceptance Criteria

- `InventorySettingsRepositoryImpl` is `@Singleton`, injects the shared `DataStore<Preferences>`.
- All four keys are prefixed `stash_` and return the spec defaults when unset.
- `setExpirationDays(0)` persists `1` (coerced); `getExpirationDays()` also never emits below `1`.
- `setExpirationNotifTimeMinutes` clamps to `0..1439` on write, and `getExpirationNotifTimeMinutes` also clamps on read (defends against version skew / corrupt prefs reaching the scheduler).
- `InventorySettingsModule` binds the interface; the app's Hilt graph compiles.
- Round-trip behaviour (set then get) is covered by tests in AKA-82 (instrumentation) — not in this plan.
