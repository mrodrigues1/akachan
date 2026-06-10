# Bottle Feed Use Cases + Inventory Link Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-110

**Goal:** Add the business-logic layer for bottle feeds — log, observe, edit, delete — mirroring the milk-stash use cases, including consuming a linked milk-stash bag when a breast-milk bottle is logged.

**Architecture:** Thin single-responsibility use cases over `BottleFeedRepository` (plan 01). `LogBottleFeedUseCase` validates input and, when the feed is linked to a stash bag, reuses the existing `MarkBagUsedUseCase` to mark that bag consumed (which already triggers an INVENTORY Firestore sync). Edit/Delete mirror `UpdateMilkBagUseCase`/`DeleteMilkBagUseCase` shapes. Bottle-feed Firestore sync (its own SyncType) is deliberately deferred to plan 07 to avoid a forward dependency.

**Tech Stack:** Kotlin, Hilt, Coroutines/Flow, JUnit 5 + MockK + Turbine.

**Dependencies:** Plan 01 (`BottleFeed`, `FeedType`, `BottleFeedRepository`). Reuses `MarkBagUsedUseCase` + `MilkBag` (existing). Consumed by plans 04/05/06.

**Suggested implementation branch:** `feat/bottle-feed-usecases`

---

## File Structure

All under `app/src/main/java/com/babytracker/domain/usecase/bottlefeed/`:
- Create `LogBottleFeedUseCase.kt`
- Create `ObserveBottleFeedsUseCase.kt`
- Create `EditBottleFeedUseCase.kt`
- Create `DeleteBottleFeedUseCase.kt`

Tests under `app/src/test/java/com/babytracker/domain/usecase/bottlefeed/`:
- `LogBottleFeedUseCaseTest.kt`
- `EditBottleFeedUseCaseTest.kt`
- `DeleteBottleFeedUseCaseTest.kt`
- `ObserveBottleFeedsUseCaseTest.kt`

> `now: () -> Instant` is provided by Hilt the same way the inventory use cases receive it (see `MarkBagUsedUseCase`, `UpdateMilkBagUseCase`). Reuse the existing provider — do not add a new one.

---

## Task 1: LogBottleFeedUseCase (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/bottlefeed/LogBottleFeedUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/bottlefeed/LogBottleFeedUseCaseTest.kt`

Behaviour: validate `volumeMl > 0` and `timestamp` not in the future; insert the feed; if `linkedBag != null` (a breast-milk bottle drawn from the stash) mark that bag used via `MarkBagUsedUseCase`; return the new feed id.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.usecase.inventory.MarkBagUsedUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class LogBottleFeedUseCaseTest {

    private lateinit var repository: BottleFeedRepository
    private lateinit var markBagUsed: MarkBagUsedUseCase
    private val now = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: LogBottleFeedUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        markBagUsed = mockk(relaxed = true)
        useCase = LogBottleFeedUseCase(repository, markBagUsed) { now }
    }

    @Test
    fun `logs formula feed without touching inventory`() = runTest {
        val captured = slot<BottleFeed>()
        coEvery { repository.insert(capture(captured)) } returns 5

        val id = useCase(
            timestamp = Instant.ofEpochMilli(9_000),
            volumeMl = 120,
            type = FeedType.FORMULA,
            linkedBag = null,
            notes = "evening",
        )

        assertEquals(5, id)
        assertEquals(FeedType.FORMULA, captured.captured.type)
        assertEquals(null, captured.captured.linkedMilkBagId)
        coVerify(exactly = 0) { markBagUsed(any()) }
    }

    @Test
    fun `logs breast-milk feed and consumes the linked bag`() = runTest {
        val bag = MilkBag(
            id = 7,
            collectionDate = Instant.ofEpochMilli(1_000),
            volumeMl = 120,
            createdAt = Instant.ofEpochMilli(1_000),
        )
        val captured = slot<BottleFeed>()
        coEvery { repository.insert(capture(captured)) } returns 9

        val id = useCase(
            timestamp = Instant.ofEpochMilli(9_000),
            volumeMl = 100,
            type = FeedType.BREAST_MILK,
            linkedBag = bag,
            notes = null,
        )

        assertEquals(9, id)
        assertEquals(7, captured.captured.linkedMilkBagId)
        coVerify(exactly = 1) { markBagUsed(bag) }
    }

    @Test
    fun `rejects non-positive volume`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(Instant.ofEpochMilli(9_000), 0, FeedType.FORMULA, null, null)
            }
        }
    }

    @Test
    fun `rejects future timestamp`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(Instant.ofEpochMilli(20_000), 100, FeedType.FORMULA, null, null)
            }
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.bottlefeed.LogBottleFeedUseCaseTest"`
Expected: FAIL — `LogBottleFeedUseCase` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.usecase.inventory.MarkBagUsedUseCase
import java.time.Instant
import javax.inject.Inject

class LogBottleFeedUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
    private val markBagUsed: MarkBagUsedUseCase,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedBag: MilkBag?,
        notes: String?,
    ): Long {
        require(volumeMl > 0) { "Volume must be greater than 0" }
        require(!timestamp.isAfter(now())) { "Feed time cannot be in the future" }

        val id = repository.insert(
            BottleFeed(
                timestamp = timestamp,
                volumeMl = volumeMl,
                type = type,
                linkedMilkBagId = linkedBag?.id,
                notes = notes,
                createdAt = now(),
            ),
        )
        if (linkedBag != null) {
            markBagUsed(linkedBag)
        }
        return id
    }
}
```

> A bag can only be linked to a `BREAST_MILK` feed; the UI (plan 04) enforces that the bag picker is shown only for that type. The use case stays type-agnostic about the picker and simply consumes whatever bag it is given.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.bottlefeed.LogBottleFeedUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/bottlefeed/LogBottleFeedUseCase.kt app/src/test/java/com/babytracker/domain/usecase/bottlefeed/LogBottleFeedUseCaseTest.kt
git commit -m "feat(usecase): add LogBottleFeedUseCase with stash consume"
```

---

## Task 2: ObserveBottleFeedsUseCase (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/bottlefeed/ObserveBottleFeedsUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/bottlefeed/ObserveBottleFeedsUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ObserveBottleFeedsUseCaseTest {

    @Test
    fun `emits feeds from repository`() = runTest {
        val repository = mockk<BottleFeedRepository>()
        val feed = BottleFeed(
            id = 1, timestamp = Instant.ofEpochMilli(1_000), volumeMl = 90,
            type = FeedType.BREAST_MILK, createdAt = Instant.ofEpochMilli(1_000),
        )
        every { repository.getAll() } returns flowOf(listOf(feed))

        val result = ObserveBottleFeedsUseCase(repository)().first()

        assertEquals(listOf(feed), result)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.bottlefeed.ObserveBottleFeedsUseCaseTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.repository.BottleFeedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveBottleFeedsUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
) {
    operator fun invoke(): Flow<List<BottleFeed>> = repository.getAll()
}
```

- [ ] **Step 4: Run to verify it passes** → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/bottlefeed/ObserveBottleFeedsUseCase.kt app/src/test/java/com/babytracker/domain/usecase/bottlefeed/ObserveBottleFeedsUseCaseTest.kt
git commit -m "feat(usecase): add ObserveBottleFeedsUseCase"
```

---

## Task 3: EditBottleFeedUseCase (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/bottlefeed/EditBottleFeedUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/bottlefeed/EditBottleFeedUseCaseTest.kt`

Mirrors `UpdateMilkBagUseCase`: validate, call `repository.updateDetails(...)`, `check(...)` that a row was updated.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class EditBottleFeedUseCaseTest {

    private lateinit var repository: BottleFeedRepository
    private val now = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: EditBottleFeedUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = EditBottleFeedUseCase(repository) { now }
    }

    @Test
    fun `updates details when row exists`() = runTest {
        coEvery {
            repository.updateDetails(any(), any(), any(), any(), any(), any())
        } returns true

        useCase(
            id = 3,
            timestamp = Instant.ofEpochMilli(9_000),
            volumeMl = 150,
            type = FeedType.FORMULA,
            linkedMilkBagId = null,
            notes = "edited",
        )

        coVerify {
            repository.updateDetails(3, Instant.ofEpochMilli(9_000), 150, FeedType.FORMULA, null, "edited")
        }
    }

    @Test
    fun `throws when no row updated`() = runTest {
        coEvery { repository.updateDetails(any(), any(), any(), any(), any(), any()) } returns false

        assertThrows(IllegalStateException::class.java) {
            runTest {
                useCase(99, Instant.ofEpochMilli(9_000), 100, FeedType.FORMULA, null, null)
            }
        }
    }

    @Test
    fun `rejects non-positive volume`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { useCase(3, Instant.ofEpochMilli(9_000), 0, FeedType.FORMULA, null, null) }
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails** → FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import java.time.Instant
import javax.inject.Inject

class EditBottleFeedUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        id: Long,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedMilkBagId: Long?,
        notes: String?,
    ) {
        require(volumeMl > 0) { "Volume must be greater than 0" }
        require(!timestamp.isAfter(now())) { "Feed time cannot be in the future" }

        check(
            repository.updateDetails(
                id = id,
                timestamp = timestamp,
                volumeMl = volumeMl,
                type = type,
                linkedMilkBagId = linkedMilkBagId,
                notes = notes,
            ),
        ) { "Bottle feed no longer exists" }
    }
}
```

> Editing does **not** re-run bag consumption. Changing the linked bag on an existing feed is out of scope (the picker in plan 04 is disabled in edit mode for already-linked feeds). This matches the stash behaviour where `updateDetails` never flips `usedAt`.

- [ ] **Step 4: Run to verify it passes** → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/bottlefeed/EditBottleFeedUseCase.kt app/src/test/java/com/babytracker/domain/usecase/bottlefeed/EditBottleFeedUseCaseTest.kt
git commit -m "feat(usecase): add EditBottleFeedUseCase"
```

---

## Task 4: DeleteBottleFeedUseCase (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/bottlefeed/DeleteBottleFeedUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/bottlefeed/DeleteBottleFeedUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class DeleteBottleFeedUseCaseTest {

    @Test
    fun `delegates delete to repository`() = runTest {
        val repository = mockk<BottleFeedRepository>(relaxed = true)
        val feed = BottleFeed(
            id = 4, timestamp = Instant.ofEpochMilli(1_000), volumeMl = 90,
            type = FeedType.FORMULA, createdAt = Instant.ofEpochMilli(1_000),
        )

        DeleteBottleFeedUseCase(repository)(feed)

        coVerify { repository.delete(feed) }
    }
}
```

- [ ] **Step 2: Run to verify it fails** → FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.repository.BottleFeedRepository
import javax.inject.Inject

class DeleteBottleFeedUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
) {
    suspend operator fun invoke(feed: BottleFeed) {
        repository.delete(feed)
    }
}
```

> Deleting a feed does not restore a consumed stash bag (consumption is one-way, matching the stash model). Note this in the PR description.

- [ ] **Step 4: Run to verify it passes** → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/bottlefeed/DeleteBottleFeedUseCase.kt app/src/test/java/com/babytracker/domain/usecase/bottlefeed/DeleteBottleFeedUseCaseTest.kt
git commit -m "feat(usecase): add DeleteBottleFeedUseCase"
```

---

## Task 5: Full validation

- [ ] **Step 1: Run the package tests**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.bottlefeed.*"`
Expected: PASS.

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (use cases are Hilt-injectable; no module changes needed — constructor `@Inject` is sufficient).

---

## Acceptance Criteria

- `LogBottleFeedUseCase` validates volume/time, inserts the feed, and consumes a linked bag via `MarkBagUsedUseCase` only when one is supplied; returns the new id.
- `EditBottleFeedUseCase` validates and updates via `repository.updateDetails`, throwing if no row matched.
- `DeleteBottleFeedUseCase` delegates to `repository.delete`.
- `ObserveBottleFeedsUseCase` exposes `repository.getAll()`.
- All four use cases are constructor-injected (no new Hilt module); reuse the existing `now: () -> Instant` provider.
- `./gradlew test` passes for the new package.

## Self-Review Notes

- Bottle-feed Firestore sync (a new `SyncToFirestoreUseCase.SyncType`) is intentionally deferred to plan 07 so this plan has no forward dependency. The only sync here is the INVENTORY sync already inside `MarkBagUsedUseCase`.
- Edit does not re-link/re-consume bags; delete does not restore bags — both documented limitations matching the one-way stash model.
- `LogBottleFeedUseCase` takes a `MilkBag?` (not an id) so it can hand the full object to `MarkBagUsedUseCase` without an extra repository lookup.
