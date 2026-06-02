# Tests (ObserveInventoryWithExpirationUseCase + InventorySettingsRepositoryImpl) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-82

**Goal:** Add focused unit tests for the two pieces of pure logic that carry the feature's correctness risk: the expiration boundary math in `ObserveInventoryWithExpirationUseCase`, and the defaults/coercion of `InventorySettingsRepositoryImpl`.

**Architecture:** JUnit 5 + MockK + Turbine for the use case (mock `GetInventoryUseCase` + `InventorySettingsRepository`, drive a `MutableStateFlow<LocalDate>`). For the repository, use a real DataStore backed by a `@TempDir` file via `PreferenceDataStoreFactory.create` — runs on the JVM (no device needed), giving true round-trip/coercion coverage.

**Tech Stack:** JUnit 5, MockK 1.13.13, Turbine 1.2.0, DataStore Preferences 1.1.1, Coroutines test.

**Dependencies:** AKA-76 (`ObserveInventoryWithExpirationUseCase`), AKA-75 (`InventorySettingsRepositoryImpl`), AKA-74 (models). Lands after those.

**Suggested implementation branch:** `feat/aka-82-tests`

---

## File Structure

- Test: `app/src/test/java/com/babytracker/domain/usecase/inventory/ObserveInventoryWithExpirationUseCaseTest.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/InventorySettingsRepositoryImplTest.kt`

> Both placed under `src/test/` (JVM) rather than `src/androidTest/`. The repo test uses `PreferenceDataStoreFactory.create`, which runs on the JVM — keeping the suite in the fast `-PfastTests` loop and avoiding an emulator. (The issue says "instrumentation"; a JVM DataStore test gives the same round-trip coverage without a device.)

---

## Task 1: ObserveInventoryWithExpirationUseCase tests

**Files:**
- Test: `app/src/test/java/com/babytracker/domain/usecase/inventory/ObserveInventoryWithExpirationUseCaseTest.kt`

- [ ] **Step 1: Write the tests**

```kotlin
package com.babytracker.domain.usecase.inventory

import app.cash.turbine.test
import com.babytracker.domain.model.ExpirationStatus
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventorySettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ObserveInventoryWithExpirationUseCaseTest {

    private lateinit var getInventory: GetInventoryUseCase
    private lateinit var settings: InventorySettingsRepository
    private lateinit var useCase: ObserveInventoryWithExpirationUseCase

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 6, 2)
    private val days = 4

    @BeforeEach
    fun setup() {
        getInventory = mockk()
        settings = mockk()
        useCase = ObserveInventoryWithExpirationUseCase(getInventory, settings)
        every { settings.getExpirationDays() } returns flowOf(days)
    }

    /** A bag whose expiry (collectionDate + [days]) lands exactly on [expiryDate]. */
    private fun bagExpiringOn(expiryDate: LocalDate): MilkBag {
        val collection = expiryDate.minusDays(days.toLong()).atStartOfDay(zone).toInstant()
        return MilkBag(id = 1, collectionDate = collection, volumeMl = 100, createdAt = Instant.now())
    }

    @Test
    fun `feature disabled maps every bag to NONE`() = runTest {
        every { getInventory() } returns flowOf(
            listOf(bagExpiringOn(today.minusDays(10)), bagExpiringOn(today)),
        )
        every { settings.getExpirationEnabled() } returns flowOf(false)

        useCase(flowOf(today)).test {
            val result = awaitItem()
            assertEquals(listOf(ExpirationStatus.NONE, ExpirationStatus.NONE), result.map { it.status })
            awaitComplete()
        }
    }

    @Test
    fun `bag expiring today is EXPIRING_OR_EXPIRED`() = runTest {
        every { getInventory() } returns flowOf(listOf(bagExpiringOn(today)))
        every { settings.getExpirationEnabled() } returns flowOf(true)

        useCase(flowOf(today)).test {
            assertEquals(ExpirationStatus.EXPIRING_OR_EXPIRED, awaitItem().first().status)
            awaitComplete()
        }
    }

    @Test
    fun `bag expired yesterday is EXPIRING_OR_EXPIRED`() = runTest {
        every { getInventory() } returns flowOf(listOf(bagExpiringOn(today.minusDays(1))))
        every { settings.getExpirationEnabled() } returns flowOf(true)

        useCase(flowOf(today)).test {
            assertEquals(ExpirationStatus.EXPIRING_OR_EXPIRED, awaitItem().first().status)
            awaitComplete()
        }
    }

    @Test
    fun `bag expiring tomorrow is EXPIRING_SOON`() = runTest {
        every { getInventory() } returns flowOf(listOf(bagExpiringOn(today.plusDays(1))))
        every { settings.getExpirationEnabled() } returns flowOf(true)

        useCase(flowOf(today)).test {
            assertEquals(ExpirationStatus.EXPIRING_SOON, awaitItem().first().status)
            awaitComplete()
        }
    }

    @Test
    fun `bag expiring in two days is NONE`() = runTest {
        every { getInventory() } returns flowOf(listOf(bagExpiringOn(today.plusDays(2))))
        every { settings.getExpirationEnabled() } returns flowOf(true)

        useCase(flowOf(today)).test {
            assertEquals(ExpirationStatus.NONE, awaitItem().first().status)
            awaitComplete()
        }
    }

    @Test
    fun `empty bag list emits empty list`() = runTest {
        every { getInventory() } returns flowOf(emptyList())
        every { settings.getExpirationEnabled() } returns flowOf(true)

        useCase(flowOf(today)).test {
            assertEquals(emptyList(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `recomputes when date flow emits a new day`() = runTest {
        // A bag expiring "today" is EXPIRING_OR_EXPIRED on its expiry day, and EXPIRING_SOON
        // the day before. Emitting the prior date then the expiry date proves recomputation.
        val expiry = today
        every { getInventory() } returns flowOf(listOf(bagExpiringOn(expiry)))
        every { settings.getExpirationEnabled() } returns flowOf(true)
        val dateFlow = MutableStateFlow(expiry.minusDays(1))

        useCase(dateFlow).test {
            assertEquals(ExpirationStatus.EXPIRING_SOON, awaitItem().first().status)
            dateFlow.value = expiry
            assertEquals(ExpirationStatus.EXPIRING_OR_EXPIRED, awaitItem().first().status)
        }
    }
}
```

- [ ] **Step 2: Run, expect PASS** (7 tests)

Run (WSL): `./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.inventory.ObserveInventoryWithExpirationUseCaseTest" -PfastTests`

---

## Task 2: InventorySettingsRepositoryImpl tests

**Files:**
- Test: `app/src/test/java/com/babytracker/data/repository/InventorySettingsRepositoryImplTest.kt`

- [ ] **Step 1: Write the tests**

```kotlin
package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.babytracker.domain.repository.InventorySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class InventorySettingsRepositoryImplTest {

    @TempDir lateinit var tempDir: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: InventorySettingsRepository

    @BeforeEach
    fun setup() {
        scope = CoroutineScope(Job())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir, "test.preferences_pb")
        }
        repo = InventorySettingsRepositoryImpl(dataStore)
    }

    @AfterEach
    fun tearDown() = scope.cancel()

    @Test
    fun `defaults when nothing stored`() = runTest {
        assertEquals(false, repo.getExpirationEnabled().first())
        assertEquals(4, repo.getExpirationDays().first())
        assertEquals(false, repo.getExpirationNotifEnabled().first())
        assertEquals(480, repo.getExpirationNotifTimeMinutes().first())
    }

    @Test
    fun `setExpirationDays coerces zero to one`() = runTest {
        repo.setExpirationDays(0)
        assertEquals(1, repo.getExpirationDays().first())
    }

    @Test
    fun `setExpirationDays coerces negative to one`() = runTest {
        repo.setExpirationDays(-5)
        assertEquals(1, repo.getExpirationDays().first())
    }

    @Test
    fun `notif time coerces out-of-range on write`() = runTest {
        repo.setExpirationNotifTimeMinutes(5000)
        assertEquals(1439, repo.getExpirationNotifTimeMinutes().first())
        repo.setExpirationNotifTimeMinutes(-10)
        assertEquals(0, repo.getExpirationNotifTimeMinutes().first())
    }

    @Test
    fun `round-trips each setting`() = runTest {
        repo.setExpirationEnabled(true)
        repo.setExpirationDays(7)
        repo.setExpirationNotifEnabled(true)
        repo.setExpirationNotifTimeMinutes(600)

        assertEquals(true, repo.getExpirationEnabled().first())
        assertEquals(7, repo.getExpirationDays().first())
        assertEquals(true, repo.getExpirationNotifEnabled().first())
        assertEquals(600, repo.getExpirationNotifTimeMinutes().first())
    }
}
```

> Add the import `kotlinx.coroutines.cancel` for `scope.cancel()`. `PreferenceDataStoreFactory.create` and `@TempDir` keep this a JVM test. If the build's DataStore artifact resolves the factory under a different name, mirror whatever an existing JVM DataStore test in the repo uses.

- [ ] **Step 2: Run, expect PASS** (5 tests)

Run (WSL): `./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.InventorySettingsRepositoryImplTest" -PfastTests`

---

## Task 3: Quality gates and commit

- [ ] **Step 1: ktlint + detekt** — `./gradlew ktlintFormat detekt` → BUILD SUCCESSFUL
- [ ] **Step 2: Run both new suites** — both PASS (see commands above)
- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/babytracker/domain/usecase/inventory/ObserveInventoryWithExpirationUseCaseTest.kt \
        app/src/test/java/com/babytracker/data/repository/InventorySettingsRepositoryImplTest.kt
git commit -m "test(inventory): cover expiration use case and settings repository [AKA-82]"
```

---

## Acceptance Criteria

- Use-case boundary cases all covered: disabled → all `NONE`; today/yesterday → `EXPIRING_OR_EXPIRED`; tomorrow → `EXPIRING_SOON`; +2 days → `NONE`; empty → empty.
- A date-flow re-emission test proves statuses recompute (the resume/midnight case).
- Repository: defaults, days coercion (0 and negative → 1), notif-time coercion (over/under range), and full round-trip all asserted against a real DataStore.
- Both suites run on the JVM under `-PfastTests` (no emulator) and pass.
