# Task 3 — Repository layer

> Part of the [Pumping & Inventory implementation plan](../2026-05-16-pumping-and-inventory-overview.md). Implement first, then write tests, then commit.

**Goal:** Provide `PumpingRepository` and `InventoryRepository` as the only abstractions use cases depend on.

**Depends on:** Task 1 (domain models), Task 2 (DAOs).

## Files

- Create: `app/src/main/java/com/babytracker/domain/repository/PumpingRepository.kt`
- Create: `app/src/main/java/com/babytracker/domain/repository/InventoryRepository.kt`
- Create: `app/src/main/java/com/babytracker/data/repository/PumpingRepositoryImpl.kt`
- Create: `app/src/main/java/com/babytracker/data/repository/InventoryRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/PumpingRepositoryImplTest.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/InventoryRepositoryImplTest.kt`

## Implementation

### Step 1: `PumpingRepository.kt`

```kotlin
package com.babytracker.domain.repository

import com.babytracker.domain.model.PumpingSession
import kotlinx.coroutines.flow.Flow

interface PumpingRepository {
    fun getAllSessions(): Flow<List<PumpingSession>>
    fun getActiveSession(): Flow<PumpingSession?>
    suspend fun getById(id: Long): PumpingSession?
    suspend fun insert(session: PumpingSession): Long
    suspend fun update(session: PumpingSession)
    suspend fun delete(session: PumpingSession)
}
```

### Step 2: `InventoryRepository.kt`

```kotlin
package com.babytracker.domain.repository

import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun getActiveBags(): Flow<List<MilkBag>>
    fun getAllBags(): Flow<List<MilkBag>>
    fun getSummary(): Flow<InventorySummary>
    suspend fun currentSummary(): InventorySummary
    suspend fun insert(bag: MilkBag): Long
    suspend fun update(bag: MilkBag)
    suspend fun delete(bag: MilkBag)
}
```

### Step 3: `PumpingRepositoryImpl.kt`

```kotlin
package com.babytracker.data.repository

import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PumpingRepositoryImpl @Inject constructor(
    private val dao: PumpingDao,
) : PumpingRepository {

    override fun getAllSessions(): Flow<List<PumpingSession>> =
        dao.getAllSessions().map { rows -> rows.map { it.toDomain() } }

    override fun getActiveSession(): Flow<PumpingSession?> =
        dao.getActiveSession().map { it?.toDomain() }

    override suspend fun getById(id: Long): PumpingSession? = dao.getById(id)?.toDomain()

    override suspend fun insert(session: PumpingSession): Long = dao.insert(session.toEntity())

    override suspend fun update(session: PumpingSession) = dao.update(session.toEntity())

    override suspend fun delete(session: PumpingSession) = dao.delete(session.toEntity())
}
```

### Step 4: `InventoryRepositoryImpl.kt`

```kotlin
package com.babytracker.data.repository

import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val dao: MilkBagDao,
) : InventoryRepository {

    override fun getActiveBags(): Flow<List<MilkBag>> =
        dao.getActiveBags().map { rows -> rows.map { it.toDomain() } }

    override fun getAllBags(): Flow<List<MilkBag>> =
        dao.getAllBags().map { rows -> rows.map { it.toDomain() } }

    override fun getSummary(): Flow<InventorySummary> =
        dao.getActiveSummary().map { row ->
            InventorySummary(
                totalMl = row.totalMl,
                bagCount = row.bagCount,
                oldestBagDate = row.oldestBagDateMs?.let { Instant.ofEpochMilli(it) },
            )
        }

    override suspend fun currentSummary(): InventorySummary = getSummary().first()

    override suspend fun insert(bag: MilkBag): Long = dao.insert(bag.toEntity())

    override suspend fun update(bag: MilkBag) = dao.update(bag.toEntity())

    override suspend fun delete(bag: MilkBag) = dao.delete(bag.toEntity())
}
```

### Step 5: Update `RepositoryModule.kt`

Add bindings for the two new repositories:

```kotlin
@Binds
@Singleton
abstract fun bindPumpingRepository(impl: PumpingRepositoryImpl): PumpingRepository

@Binds
@Singleton
abstract fun bindInventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository
```

Imports to add:

```kotlin
import com.babytracker.data.repository.InventoryRepositoryImpl
import com.babytracker.data.repository.PumpingRepositoryImpl
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.PumpingRepository
```

## Tests

### `PumpingRepositoryImplTest.kt`

```kotlin
package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class PumpingRepositoryImplTest {
    private lateinit var dao: PumpingDao
    private lateinit var repository: PumpingRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = PumpingRepositoryImpl(dao)
    }

    @Test
    fun `getActiveSession maps entity to domain`() = runTest {
        every { dao.getActiveSession() } returns flowOf(
            PumpingEntity(id = 7, startTime = 1_000L, breast = "LEFT")
        )

        repository.getActiveSession().test {
            val session = awaitItem()
            assertEquals(7L, session?.id)
            assertEquals(PumpingBreast.LEFT, session?.breast)
            awaitComplete()
        }
    }

    @Test
    fun `insert forwards toEntity to dao`() = runTest {
        val captured = slot<PumpingEntity>()
        coEvery { dao.insert(capture(captured)) } returns 42L

        val id = repository.insert(
            PumpingSession(
                startTime = Instant.ofEpochMilli(2_000L),
                breast = PumpingBreast.BOTH,
            )
        )

        assertEquals(42L, id)
        assertEquals(2_000L, captured.captured.startTime)
        assertEquals("BOTH", captured.captured.breast)
    }

    @Test
    fun `delete forwards toEntity to dao`() = runTest {
        val session = PumpingSession(
            id = 9,
            startTime = Instant.ofEpochMilli(1_000L),
            breast = PumpingBreast.RIGHT,
        )
        repository.delete(session)
        coVerify { dao.delete(match { it.id == 9L }) }
    }
}
```

### `InventoryRepositoryImplTest.kt`

```kotlin
package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.InventorySummaryRow
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.entity.MilkBagEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class InventoryRepositoryImplTest {
    private lateinit var dao: MilkBagDao
    private lateinit var repository: InventoryRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = InventoryRepositoryImpl(dao)
    }

    @Test
    fun `getActiveBags maps rows oldest first`() = runTest {
        every { dao.getActiveBags() } returns flowOf(
            listOf(
                MilkBagEntity(id = 1, collectionDate = 100L, volumeMl = 60, createdAt = 100L),
                MilkBagEntity(id = 2, collectionDate = 200L, volumeMl = 100, createdAt = 200L),
            )
        )
        repository.getActiveBags().test {
            val bags = awaitItem()
            assertEquals(2, bags.size)
            assertEquals(1L, bags[0].id)
            awaitComplete()
        }
    }

    @Test
    fun `getSummary converts row, mapping null oldestBagDateMs to null Instant`() = runTest {
        every { dao.getActiveSummary() } returns flowOf(
            InventorySummaryRow(totalMl = 0, bagCount = 0, oldestBagDateMs = null)
        )
        repository.getSummary().test {
            val summary = awaitItem()
            assertEquals(0, summary.totalMl)
            assertEquals(0, summary.bagCount)
            assertNull(summary.oldestBagDate)
            awaitComplete()
        }
    }

    @Test
    fun `getSummary converts non-null oldestBagDateMs to Instant`() = runTest {
        every { dao.getActiveSummary() } returns flowOf(
            InventorySummaryRow(totalMl = 240, bagCount = 3, oldestBagDateMs = 100L)
        )
        repository.getSummary().test {
            val summary = awaitItem()
            assertNotNull(summary.oldestBagDate)
            assertEquals(Instant.ofEpochMilli(100L), summary.oldestBagDate)
            awaitComplete()
        }
    }
}
```

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew test --tests "com.babytracker.data.repository.PumpingRepositoryImplTest"
./gradlew test --tests "com.babytracker.data.repository.InventoryRepositoryImplTest"
```

Expected: all green.

## Commit

```
feat(repository): add pumping and inventory repositories

Provide PumpingRepository and InventoryRepository abstractions over the
new DAOs, with Hilt bindings in RepositoryModule.
```
