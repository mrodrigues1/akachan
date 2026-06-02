# Domain Layer (ExpirationStatus, MilkBagWithExpiration, InventorySettingsRepository) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-74

**Goal:** Add the pure-Kotlin domain contracts for the milk stash expiration feature: an `ExpirationStatus` enum, a `MilkBagWithExpiration` wrapper, and the `InventorySettingsRepository` interface.

**Architecture:** Domain layer only — zero framework imports per the project's DDD principle. The repository is declared as a pure interface (Flow getters + suspend setters); its DataStore implementation lands in a later plan (AKA-75). No logic, no Android types.

**Tech Stack:** Kotlin 2.3.20, Kotlinx Coroutines `Flow`. No Android dependencies.

**Dependencies:** None. This is the first plan and unblocks AKA-75 (data), AKA-76 (use case), AKA-77 (settings UI), AKA-79 (notification).

**Suggested implementation branch:** `feat/aka-74-domain-layer`

---

## File Structure

- Create: `app/src/main/java/com/babytracker/domain/model/ExpirationStatus.kt` — enum with 3 states
- Create: `app/src/main/java/com/babytracker/domain/model/MilkBagWithExpiration.kt` — pairs a `MilkBag` with its computed status
- Create: `app/src/main/java/com/babytracker/domain/repository/InventorySettingsRepository.kt` — settings contract (4 get Flows + 4 set suspends)

These mirror the existing domain conventions: `domain/model/` holds pure data classes/enums (see `domain/model/MilkBag.kt`, `domain/model/SleepType.kt`), `domain/repository/` holds interfaces (see `domain/repository/InventoryRepository.kt`).

---

## Task 1: ExpirationStatus enum

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/ExpirationStatus.kt`

- [ ] **Step 1: Create the enum**

```kotlin
package com.babytracker.domain.model

/**
 * Visual expiration state of a milk stash bag, derived from its collection date,
 * the configured expiration window, and the current date.
 *
 * - [NONE] more than one day until expiration (no visual treatment)
 * - [EXPIRING_SOON] expires exactly tomorrow
 * - [EXPIRING_OR_EXPIRED] expires today or any past date
 */
enum class ExpirationStatus {
    NONE,
    EXPIRING_SOON,
    EXPIRING_OR_EXPIRED,
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 2: MilkBagWithExpiration model

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/MilkBagWithExpiration.kt`

- [ ] **Step 1: Create the data class**

```kotlin
package com.babytracker.domain.model

/**
 * A milk stash bag paired with its computed expiration status.
 * Produced by ObserveInventoryWithExpirationUseCase and consumed by the inventory UI.
 */
data class MilkBagWithExpiration(
    val bag: MilkBag,
    val status: ExpirationStatus,
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 3: InventorySettingsRepository interface

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/repository/InventorySettingsRepository.kt`

- [ ] **Step 1: Create the interface**

The four settings mirror the DataStore keys in the spec: master toggle, expiration days, notif toggle, notif time (minute-of-day). Time is stored as minute-of-day (Int) to match the existing `WAKE_TIME_MINUTES` / `QUIET_HOURS_*_MINUTE` convention in `SettingsRepositoryImpl`.

```kotlin
package com.babytracker.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Settings for the milk stash expiration feature, persisted via DataStore.
 * Kept separate from [SettingsRepository] because it is an opt-in, self-contained feature.
 */
interface InventorySettingsRepository {
    fun getExpirationEnabled(): Flow<Boolean>
    suspend fun setExpirationEnabled(enabled: Boolean)

    fun getExpirationDays(): Flow<Int>
    suspend fun setExpirationDays(days: Int)

    fun getExpirationNotifEnabled(): Flow<Boolean>
    suspend fun setExpirationNotifEnabled(enabled: Boolean)

    fun getExpirationNotifTimeMinutes(): Flow<Int>
    suspend fun setExpirationNotifTimeMinutes(minuteOfDay: Int)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 4: Verify domain purity and commit

- [ ] **Step 1: Run the architecture tests**

The project enforces "domain models have zero framework imports" via Konsist architecture tests (`app/src/test/java/com/babytracker/architecture/`). Run them to confirm the new files comply.

Run (WSL): `./gradlew :app:testDebugUnitTest --tests "com.babytracker.architecture.*"`
Expected: BUILD SUCCESSFUL (no Android/framework imports detected in the new domain files)

- [ ] **Step 2: Run ktlint + detekt**

Run: `./gradlew ktlintFormat detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/ExpirationStatus.kt \
        app/src/main/java/com/babytracker/domain/model/MilkBagWithExpiration.kt \
        app/src/main/java/com/babytracker/domain/repository/InventorySettingsRepository.kt
git commit -m "feat(inventory): add expiration domain contracts [AKA-74]"
```

---

## Acceptance Criteria

- All three files compile with `./gradlew :app:compileDebugKotlin`.
- `ExpirationStatus` has exactly three values: `NONE`, `EXPIRING_SOON`, `EXPIRING_OR_EXPIRED`.
- `MilkBagWithExpiration` is a `data class(val bag: MilkBag, val status: ExpirationStatus)`.
- `InventorySettingsRepository` is a pure interface with no DataStore / Android imports — only `kotlinx.coroutines.flow.Flow`.
- Architecture (Konsist) tests pass — domain purity preserved.
