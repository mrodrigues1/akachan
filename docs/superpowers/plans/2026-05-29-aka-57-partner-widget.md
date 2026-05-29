# AKA-57 Partner-Mode Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In `AppMode.PARTNER` the home-screen widget renders the primary user's last feed and sleep status from a share-code-scoped cache of the Firestore snapshot; `NONE` / `PRIMARY` behaviour is unchanged.

**Architecture:** Two paths branched on `AppMode`. The Glance **render path** (`loadWidgetData`) reads a projected `WidgetData` from a dedicated DataStore-backed `PartnerWidgetCache`, never the network; on a partner cache-miss it enqueues a one-time refresh (no network on the render thread). The **refresh path** (`WidgetRefreshWorker`) fetches the snapshot, projects it to `WidgetData`, and writes the cache tagged with the active share code; it runs both periodically (15-min, existing) and once immediately when the partner connects or the widget renders with an empty cache. A typed `PartnerAccessRevokedException` lets the worker distinguish a confirmed revoke (clear cache, stop) from any other failure (keep stale cache, retry).

**Two race fixes baked in (from adversarial review):**
1. **Revoke must not erase a newer connection, and no cache may mix two primaries.** The revoke cleanup is *atomic and conditional*: `SettingsRepository.clearPartnerStateIfShareCodeMatches(code)` only clears share code + app mode when the stored code still equals the revoked one, so a stale background refresh for primary A cannot wipe a fresh reconnect to primary B. The worker fetches with an **explicit** share code (`FetchPartnerDataUseCase.invoke(code)`) and both saves and clears under that *same* code — the use case never re-reads settings to pick a different code mid-fetch, so a snapshot can never be cached under another primary's key. (The render path's code match would already hide a mis-tagged entry, but an airtight write path also prevents `read("A")` from ever surfacing B's data after a later reconnect to A.)
2. **Widget must populate promptly on connect.** Periodic-only refresh would leave a partner staring at `EMPTY`/stale data for up to 15 min after connecting. A one-time `WidgetRefreshScheduler.scheduleImmediateRefresh()` fires on connect and on render-path cache-miss.

**Tech Stack:** Kotlin, Jetpack Glance, Hilt `@HiltWorker` + EntryPoint, WorkManager (`OneTimeWorkRequest` + existing periodic), DataStore Preferences, Coroutines/Flow, JUnit 5 + MockK (mapper/loader/cache/use-case/VM), Robolectric + JUnit 4 (`TestListenableWorkerBuilder`, `WorkManagerTestInitHelper`, real DataStore).

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `sharing/usecase/PartnerAccessRevokedException.kt` | Typed marker for a *confirmed* partner revoke; subtype of `IllegalStateException` so `PartnerDashboardViewModel`'s `catch (IllegalStateException)` keeps working. | **Create** |
| `domain/repository/SettingsRepository.kt` | Add `clearPartnerStateIfShareCodeMatches(code): Boolean`. | Modify |
| `data/repository/SettingsRepositoryImpl.kt` | Atomic conditional clear (single `dataStore.edit`): removes share code + sets `APP_MODE=NONE` only if stored code matches. | Modify |
| `sharing/usecase/FetchPartnerDataUseCase.kt` | Revoke branch calls the conditional clear, then throws `PartnerAccessRevokedException`; "No share code" stays plain `IllegalStateException`. | Modify |
| `widget/data/WidgetData.kt` | Add pure `toWidgetData(snapshot: ShareSnapshot)` overload that picks the latest session/sleep, converts to domain models, and delegates to the existing overload. | Modify |
| `widget/PartnerWidgetCache.kt` | Interface: `read(shareCode)`, `save(shareCode, data)`, `clear(shareCode)` (code-scoped clear). | **Create** |
| `widget/PartnerWidgetCacheImpl.kt` | `@Singleton` DataStore-backed impl; 7 `partner_widget_*` keys; `clear(shareCode)` removes only those keys and only when the cached code matches. | **Create** |
| `widget/WidgetRefreshScheduler.kt` | Interface: `scheduleImmediateRefresh()`. | **Create** |
| `widget/WorkManagerWidgetRefreshScheduler.kt` | `@Singleton`; enqueues a unique one-time, network-constrained `WidgetRefreshWorker` (`ExistingWorkPolicy.KEEP`). | **Create** |
| `widget/WidgetEntryPoint.kt` | Add `settingsRepository()`, `partnerWidgetCache()`, `widgetRefreshScheduler()` accessors. | Modify |
| `widget/WidgetDataLoader.kt` | Branch on `AppMode`; partner reads `cache.read(shareCode)`; cache-miss → `scheduleImmediateRefresh()` + `EMPTY`; null code → `EMPTY`. | Modify |
| `widget/WidgetRefreshWorker.kt` | Inject `SettingsRepository`, `FetchPartnerDataUseCase`, `PartnerWidgetCache`; partner refresh + code-scoped revoke handling. | Modify |
| `ui/sharing/ConnectPartnerViewModel.kt` | Inject `WidgetRefreshScheduler`; call `scheduleImmediateRefresh()` after a successful connect. | Modify |
| `di/WidgetModule.kt` | `@Binds @Singleton` for `PartnerWidgetCache` and `WidgetRefreshScheduler`. | Modify |

**Explicitly unchanged:** `widget/WidgetSyncManager.kt` — its 60s elapsed-time timer already re-renders the partner widget from cache, and its local-flow triggers simply never emit for a partner. Do **not** touch it.

**Architecture-rule check (Konsist):** `ui/sharing/ConnectPartnerViewModel` importing `com.babytracker.widget.WidgetRefreshScheduler` is allowed — `LayerIsolationTest` only forbids `ui → com.babytracker.data`. `WorkManagerWidgetRefreshScheduler` resides in `widget` (not `..repository..`) and its parent ends in `Scheduler`, so the `*RepositoryImpl` naming rule does not apply (mirrors the existing `GlanceWidgetUpdater : WidgetUpdater`). No new `com.google.firebase` imports outside `sharing`.

**Test files:**

| Test | Framework | Change |
|---|---|---|
| `sharing/usecase/FetchPartnerDataUseCaseTest.kt` | JUnit 5 + MockK | Modify (conditional-clear mock + revoke type / missing-code negative) |
| `data/repository/SettingsPartnerStateTest.kt` | JUnit 4 + Robolectric + real DataStore | **Create** |
| `widget/data/WidgetDataMapperTest.kt` | JUnit 5 | Modify (add snapshot-overload cases) |
| `widget/PartnerWidgetCacheTest.kt` | JUnit 5 + real DataStore (`@TempDir`) | **Create** |
| `widget/WorkManagerWidgetRefreshSchedulerTest.kt` | JUnit 4 + Robolectric + `WorkManagerTestInitHelper` | **Create** |
| `widget/WidgetDataLoaderTest.kt` | JUnit 5 + MockK | Modify (new signature + partner + priming cases) |
| `widget/WidgetRefreshWorkerTest.kt` | JUnit 4 + Robolectric + MockK | Modify (new ctor + partner + code-scoped clear cases) |
| `ui/sharing/ConnectPartnerViewModelTest.kt` | JUnit 5 + MockK | Modify (new ctor + immediate-refresh cases) |

**Run commands** (per `CLAUDE.md`, WSL gradle setup is in `~/.gradle/gradle.properties`):
- Targeted: `./gradlew :app:testDebugUnitTest --tests "<FQCN>" -PfastTests`
- Fast loop (skips architecture tests): `./gradlew :app:testDebugUnitTest -PfastTests`
- Full suite before PR: `./gradlew :app:testDebugUnitTest`

---

## Task 1: Revoke exception + atomic conditional clear + use case

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/usecase/PartnerAccessRevokedException.kt`
- Modify: `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/FetchPartnerDataUseCase.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/SettingsPartnerStateTest.kt` (create)
- Test: `app/src/test/java/com/babytracker/sharing/usecase/FetchPartnerDataUseCaseTest.kt` (modify)

- [ ] **Step 1: Write the failing conditional-clear repository test**

Create `app/src/test/java/com/babytracker/data/repository/SettingsPartnerStateTest.kt`:

```kotlin
package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.babytracker.sharing.domain.model.AppMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SettingsPartnerStateTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepositoryImpl

    @Before
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(context.cacheDir, "partner_state_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        repo = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() { scope.cancel(); file.delete() }

    @Test
    fun `clears and returns true when stored code matches`() = runTest {
        repo.setShareCode("CODE_A")
        repo.setAppMode(AppMode.PARTNER)

        val cleared = repo.clearPartnerStateIfShareCodeMatches("CODE_A")

        assertTrue(cleared)
        assertNull(repo.getShareCode().first())
        assertEquals(AppMode.NONE, repo.getAppMode().first())
    }

    @Test
    fun `leaves state and returns false when stored code differs`() = runTest {
        repo.setShareCode("CODE_B")
        repo.setAppMode(AppMode.PARTNER)

        val cleared = repo.clearPartnerStateIfShareCodeMatches("CODE_A")

        assertFalse(cleared)
        assertEquals("CODE_B", repo.getShareCode().first())
        assertEquals(AppMode.PARTNER, repo.getAppMode().first())
    }
}
```

- [ ] **Step 2: Update the use-case test (revoke type + conditional-clear mock + missing-code negative)**

In `app/src/test/java/com/babytracker/sharing/usecase/FetchPartnerDataUseCaseTest.kt`, add these imports next to the existing `org.junit.jupiter.api.Assertions.*` imports:

```kotlin
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
```

Replace the existing `throwsAndClearsStateWhenPartnerIsDisconnected` and `throwsWhenNoShareCodeStored` tests with:

```kotlin
    @Test
    fun throwsPartnerAccessRevokedWhenPartnerIsDisconnected() = runTest {
        coEvery { sharingRepository.isPartnerConnected(shareCode, "uid123") } returns false
        coEvery { settingsRepository.clearPartnerStateIfShareCodeMatches(shareCode.value) } returns true

        var caught: IllegalStateException? = null
        try {
            useCase()
        } catch (e: IllegalStateException) {
            caught = e
        }

        assertNotNull(caught)
        assertTrue(caught is PartnerAccessRevokedException)
        assertTrue(caught is IllegalStateException)
        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches(shareCode.value) }
    }

    @Test
    fun throwsPlainIllegalStateExceptionWhenNoShareCodeStored() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(null)

        var caught: IllegalStateException? = null
        try {
            useCase()
        } catch (e: IllegalStateException) {
            caught = e
        }

        assertNotNull(caught)
        assertFalse(caught is PartnerAccessRevokedException)
    }

    @Test
    fun invokeWithExplicitCodeFetchesThatCodeNotSettings() = runTest {
        // The explicit overload must use the passed code, never re-reading settings — this is what
        // lets the worker cache a snapshot under exactly the code it requested.
        val explicit = ShareCode("ZZ999999")
        coEvery { sharingRepository.isPartnerConnected(explicit, "uid123") } returns true
        coEvery { sharingRepository.fetchSnapshot(explicit) } returns snapshot

        val result = useCase(explicit)

        assertEquals(snapshot, result)
        coVerify { sharingRepository.fetchSnapshot(explicit) }
    }
```

(The `just Runs` / `Runs` imports stay; they are still used by other tests in this file. `assertEquals`, `coVerify`, and `ShareCode` are already imported by this test.)

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.SettingsPartnerStateTest" --tests "com.babytracker.sharing.usecase.FetchPartnerDataUseCaseTest" -PfastTests`
Expected: FAIL — `Unresolved reference: clearPartnerStateIfShareCodeMatches` and `Unresolved reference: PartnerAccessRevokedException`.

- [ ] **Step 4: Create the exception**

Create `app/src/main/java/com/babytracker/sharing/usecase/PartnerAccessRevokedException.kt`:

```kotlin
package com.babytracker.sharing.usecase

/**
 * Thrown by [FetchPartnerDataUseCase] only when a partner's access has been *confirmed* revoked
 * (the share document no longer lists this partner's UID). Subtypes [IllegalStateException] so that
 * existing `catch (IllegalStateException)` handlers (e.g. PartnerDashboardViewModel) keep working,
 * while callers that must act only on a confirmed revoke can match this exact type.
 */
class PartnerAccessRevokedException(message: String) : IllegalStateException(message)
```

- [ ] **Step 5: Add the conditional-clear method to the SettingsRepository interface**

In `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`, add after `clearShareCode()`:

```kotlin
    /**
     * Atomically clears partner state (removes the share code and sets [AppMode.NONE]) only if the
     * currently stored share code still equals [code]. Returns true if it cleared, false if the
     * stored code had already changed (e.g. the partner reconnected to a different primary).
     * This prevents a stale background revoke from erasing a newer connection.
     */
    suspend fun clearPartnerStateIfShareCodeMatches(code: String): Boolean
```

- [ ] **Step 6: Implement it atomically in SettingsRepositoryImpl**

In `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt`, add after `clearShareCode()` (the `AppMode` import is already present):

```kotlin
    override suspend fun clearPartnerStateIfShareCodeMatches(code: String): Boolean {
        var cleared = false
        dataStore.edit { prefs ->
            // Single edit block runs under DataStore's lock, so the compare-and-clear is atomic
            // with respect to ConnectAsPartnerUseCase's setShareCode/setAppMode writes.
            if (prefs[SHARE_CODE] == code) {
                prefs.remove(SHARE_CODE)
                prefs[APP_MODE] = AppMode.NONE.name
                cleared = true
            }
        }
        return cleared
    }
```

- [ ] **Step 7: Split the use case into a settings-driven and an explicit-code overload**

Replace the entire contents of `app/src/main/java/com/babytracker/sharing/usecase/FetchPartnerDataUseCase.kt` with:

```kotlin
package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class FetchPartnerDataUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
) {
    /** Fetches the snapshot for the currently stored share code (used by the dashboard). */
    suspend operator fun invoke(): ShareSnapshot {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        return invoke(code)
    }

    /**
     * Fetches the snapshot for an explicit [code]. Callers that already hold the code (e.g. the
     * widget refresh worker) use this so the returned snapshot provably belongs to [code] — the
     * use case never re-reads settings, so a reconnect mid-fetch can't make the fetched data and
     * the caller's code diverge and end up cached under another primary's key.
     */
    suspend operator fun invoke(code: ShareCode): ShareSnapshot {
        val uid = sharingRepository.signInAnonymously()
        if (!sharingRepository.isPartnerConnected(code, uid)) {
            // Conditional clear: if the user already reconnected to a different primary, the stored
            // code no longer matches and this is a no-op — the newer connection is preserved.
            settingsRepository.clearPartnerStateIfShareCodeMatches(code.value)
            throw PartnerAccessRevokedException("Partner access revoked")
        }
        return sharingRepository.fetchSnapshot(code)
    }
}
```

Note the `import com.babytracker.sharing.domain.model.AppMode` from the original file is intentionally gone — `setAppMode` is no longer called here (the conditional clear owns the `AppMode.NONE` write). `NamingConventionTest` still passes: the class retains `invoke` functions.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.SettingsPartnerStateTest" --tests "com.babytracker.sharing.usecase.FetchPartnerDataUseCaseTest" -PfastTests`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/usecase/PartnerAccessRevokedException.kt \
        app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt \
        app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt \
        app/src/main/java/com/babytracker/sharing/usecase/FetchPartnerDataUseCase.kt \
        app/src/test/java/com/babytracker/data/repository/SettingsPartnerStateTest.kt \
        app/src/test/java/com/babytracker/sharing/usecase/FetchPartnerDataUseCaseTest.kt
git commit -m "feat(sharing): atomically scope partner revoke to the active share code"
```

---

## Task 2: Snapshot → WidgetData mapper overload

**Files:**
- Modify: `app/src/main/java/com/babytracker/widget/data/WidgetData.kt`
- Test: `app/src/test/java/com/babytracker/widget/data/WidgetDataMapperTest.kt`

- [ ] **Step 1: Add failing snapshot-overload tests**

Append these tests inside `WidgetDataMapperTest` (before the closing brace). Add the snapshot-model imports listed below at the top of the file.

Imports to add:

```kotlin
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
```

Tests to append:

```kotlin
    private fun snapshot(
        babyName: String = "Akira",
        sessions: List<SessionSnapshot> = emptyList(),
        sleeps: List<SleepSnapshot> = emptyList(),
    ) = ShareSnapshot(
        lastSyncAt = Instant.parse("2026-05-24T12:00:00Z"),
        baby = BabySnapshot(name = babyName, birthDateMs = 0L, allergies = emptyList()),
        sessions = sessions,
        sleepRecords = sleeps,
    )

    private fun sessionSnapshot(
        id: Long = 1,
        startMs: Long = Instant.parse("2026-05-24T10:00:00Z").toEpochMilli(),
        endMs: Long? = Instant.parse("2026-05-24T10:15:00Z").toEpochMilli(),
        side: String = "LEFT",
        switchMs: Long? = null,
    ) = SessionSnapshot(
        id = id,
        startTime = startMs,
        endTime = endMs,
        startingSide = side,
        switchTime = switchMs,
        pausedDurationMs = 0L,
        notes = null,
    )

    private fun sleepSnapshot(
        id: Long = 1,
        startMs: Long = Instant.parse("2026-05-24T11:00:00Z").toEpochMilli(),
        endMs: Long? = null,
        type: String = "NAP",
    ) = SleepSnapshot(
        id = id,
        startTime = startMs,
        endTime = endMs,
        sleepType = type,
        notes = null,
    )

    @Test
    fun `snapshot picks the latest session by startTime`() {
        val older = sessionSnapshot(id = 1, startMs = Instant.parse("2026-05-24T08:00:00Z").toEpochMilli(), side = "LEFT")
        val newer = sessionSnapshot(id = 2, startMs = Instant.parse("2026-05-24T10:00:00Z").toEpochMilli(), side = "RIGHT")

        val result = toWidgetData(snapshot(sessions = listOf(older, newer)))

        assertEquals(BreastSide.RIGHT, result.lastFeedSide)
        assertEquals(Instant.parse("2026-05-24T10:00:00Z"), result.lastFeedStart)
    }

    @Test
    fun `snapshot open session maps to ACTIVE`() {
        val result = toWidgetData(snapshot(sessions = listOf(sessionSnapshot(endMs = null))))

        assertEquals(FeedState.ACTIVE, result.feedState)
    }

    @Test
    fun `snapshot closed session maps to RECENT`() {
        val result = toWidgetData(snapshot(sessions = listOf(sessionSnapshot())))

        assertEquals(FeedState.RECENT, result.feedState)
    }

    @Test
    fun `snapshot with no sessions maps to feed NONE`() {
        val result = toWidgetData(snapshot(sessions = emptyList()))

        assertEquals(FeedState.NONE, result.feedState)
        assertNull(result.lastFeedSide)
        assertNull(result.lastFeedStart)
    }

    @Test
    fun `snapshot switched session reports opposite side`() {
        val switched = sessionSnapshot(
            side = "LEFT",
            switchMs = Instant.parse("2026-05-24T10:10:00Z").toEpochMilli(),
        )

        val result = toWidgetData(snapshot(sessions = listOf(switched)))

        assertEquals(BreastSide.RIGHT, result.lastFeedSide)
    }

    @Test
    fun `snapshot picks the latest sleep and maps SLEEPING`() {
        val older = sleepSnapshot(id = 1, startMs = Instant.parse("2026-05-24T09:00:00Z").toEpochMilli(), endMs = Instant.parse("2026-05-24T09:30:00Z").toEpochMilli())
        val newer = sleepSnapshot(id = 2, startMs = Instant.parse("2026-05-24T11:00:00Z").toEpochMilli(), endMs = null)

        val result = toWidgetData(snapshot(sleeps = listOf(older, newer)))

        assertEquals(SleepState.SLEEPING, result.sleepState)
        assertEquals(Instant.parse("2026-05-24T11:00:00Z"), result.sleepSince)
    }

    @Test
    fun `snapshot with no sleeps maps to sleep NONE`() {
        val result = toWidgetData(snapshot(sleeps = emptyList()))

        assertEquals(SleepState.NONE, result.sleepState)
        assertNull(result.sleepSince)
    }

    @Test
    fun `snapshot blank baby name falls back to "Baby"`() {
        val result = toWidgetData(snapshot(babyName = "   "))

        assertEquals("Baby", result.babyName)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.data.WidgetDataMapperTest" -PfastTests`
Expected: FAIL — `None of the following functions can be called` / unresolved `toWidgetData(ShareSnapshot)`.

- [ ] **Step 3: Add the snapshot overload + converters**

In `app/src/main/java/com/babytracker/widget/data/WidgetData.kt`, add these imports below the existing imports:

```kotlin
import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
```

Add the new overload immediately after the existing `toWidgetData(babyName, lastFeed, latestSleep)` function (after its closing brace, around line 58):

```kotlin
fun toWidgetData(snapshot: ShareSnapshot): WidgetData {
    val lastFeed = snapshot.sessions
        .maxByOrNull { it.startTime }
        ?.toDomainSession()
    val latestSleep = snapshot.sleepRecords
        .maxByOrNull { it.startTime }
        ?.toDomainSleep()

    return toWidgetData(
        babyName = snapshot.baby.name,
        lastFeed = lastFeed,
        latestSleep = latestSleep,
    )
}

// Snapshot rows carry no pausedAt, so partner feeds never derive PAUSED (accepted limitation:
// the shared snapshot model has no pause state — the dashboard has the same blind spot).
private fun SessionSnapshot.toDomainSession(): BreastfeedingSession =
    BreastfeedingSession(
        id = id,
        startTime = Instant.ofEpochMilli(startTime),
        endTime = endTime?.let(Instant::ofEpochMilli),
        startingSide = BreastSide.valueOf(startingSide),
        switchTime = switchTime?.let(Instant::ofEpochMilli),
        pausedAt = null,
    )

private fun SleepSnapshot.toDomainSleep(): SleepRecord =
    SleepRecord(
        id = id,
        startTime = Instant.ofEpochMilli(startTime),
        endTime = endTime?.let(Instant::ofEpochMilli),
        sleepType = SleepType.valueOf(sleepType),
    )
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.data.WidgetDataMapperTest" -PfastTests`
Expected: PASS (all existing + new cases).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/widget/data/WidgetData.kt \
        app/src/test/java/com/babytracker/widget/data/WidgetDataMapperTest.kt
git commit -m "feat(widget): map ShareSnapshot to WidgetData for partner mode"
```

---

## Task 3: PartnerWidgetCache (interface + DataStore impl + DI binding)

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/PartnerWidgetCache.kt`
- Create: `app/src/main/java/com/babytracker/widget/PartnerWidgetCacheImpl.kt`
- Modify: `app/src/main/java/com/babytracker/di/WidgetModule.kt`
- Test: `app/src/test/java/com/babytracker/widget/PartnerWidgetCacheTest.kt`

- [ ] **Step 1: Write the failing cache test**

Create `app/src/test/java/com/babytracker/widget/PartnerWidgetCacheTest.kt`:

```kotlin
package com.babytracker.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.babytracker.domain.model.BreastSide
import com.babytracker.widget.data.FeedState
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

class PartnerWidgetCacheTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var cache: PartnerWidgetCacheImpl

    private val sample = WidgetData(
        babyName = "Akira",
        lastFeedSide = BreastSide.RIGHT,
        lastFeedStart = Instant.parse("2026-05-24T10:00:00Z"),
        feedState = FeedState.RECENT,
        sleepState = SleepState.SLEEPING,
        sleepSince = Instant.parse("2026-05-24T11:00:00Z"),
    )

    @BeforeEach
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir, "partner_widget_${System.nanoTime()}.preferences_pb")
        }
        cache = PartnerWidgetCacheImpl(dataStore)
    }

    @AfterEach
    fun tearDown() = scope.cancel()

    @Test
    fun `save then read round-trips all fields for the same share code`() = runTest {
        cache.save("CODE1", sample)

        assertEquals(sample, cache.read("CODE1"))
    }

    @Test
    fun `read returns null on an empty store`() = runTest {
        assertNull(cache.read("CODE1"))
    }

    @Test
    fun `read returns null when the cached share code mismatches`() = runTest {
        cache.save("CODE1", sample)

        assertNull(cache.read("OTHER"))
    }

    @Test
    fun `save then read round-trips null feed and sleep fields`() = runTest {
        cache.save("CODE1", WidgetData.EMPTY)

        assertEquals(WidgetData.EMPTY, cache.read("CODE1"))
    }

    @Test
    fun `clear with the matching code makes read return null`() = runTest {
        cache.save("CODE1", sample)

        cache.clear("CODE1")

        assertNull(cache.read("CODE1"))
    }

    @Test
    fun `clear with a different code leaves the cache intact`() = runTest {
        cache.save("CODE1", sample)

        cache.clear("OTHER")

        assertEquals(sample, cache.read("CODE1"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.PartnerWidgetCacheTest" -PfastTests`
Expected: FAIL — `Unresolved reference: PartnerWidgetCacheImpl`.

- [ ] **Step 3: Create the interface**

Create `app/src/main/java/com/babytracker/widget/PartnerWidgetCache.kt`:

```kotlin
package com.babytracker.widget

import com.babytracker.widget.data.WidgetData

/**
 * Caches the projected [WidgetData] for a partner-mode widget, tagged with the share code it was
 * fetched under. [read] returns data only when the requested code matches the cached one, and
 * [clear] removes the cache only when the supplied code matches — so a partner who reconnects to a
 * different primary never sees, nor has wiped, the other primary's data.
 */
interface PartnerWidgetCache {
    suspend fun read(shareCode: String): WidgetData?
    suspend fun save(shareCode: String, data: WidgetData)
    suspend fun clear(shareCode: String)
}
```

- [ ] **Step 4: Create the DataStore impl**

Create `app/src/main/java/com/babytracker/widget/PartnerWidgetCacheImpl.kt`:

```kotlin
package com.babytracker.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.BreastSide
import com.babytracker.widget.data.FeedState
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartnerWidgetCacheImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PartnerWidgetCache {

    override suspend fun read(shareCode: String): WidgetData? {
        val prefs = dataStore.data.first()
        val feedStateName = prefs[FEED_STATE] ?: return null
        if (prefs[SHARE_CODE] != shareCode) return null

        return WidgetData(
            babyName = prefs[BABY_NAME] ?: "Baby",
            lastFeedSide = prefs[FEED_SIDE]?.let(BreastSide::valueOf),
            lastFeedStart = prefs[FEED_START_MS]?.let(Instant::ofEpochMilli),
            feedState = FeedState.valueOf(feedStateName),
            sleepState = SleepState.valueOf(prefs[SLEEP_STATE] ?: SleepState.NONE.name),
            sleepSince = prefs[SLEEP_SINCE_MS]?.let(Instant::ofEpochMilli),
        )
    }

    override suspend fun save(shareCode: String, data: WidgetData) {
        dataStore.edit { prefs ->
            prefs[SHARE_CODE] = shareCode
            prefs[BABY_NAME] = data.babyName
            data.lastFeedSide?.let { prefs[FEED_SIDE] = it.name } ?: prefs.remove(FEED_SIDE)
            data.lastFeedStart?.let { prefs[FEED_START_MS] = it.toEpochMilli() } ?: prefs.remove(FEED_START_MS)
            prefs[FEED_STATE] = data.feedState.name
            prefs[SLEEP_STATE] = data.sleepState.name
            data.sleepSince?.let { prefs[SLEEP_SINCE_MS] = it.toEpochMilli() } ?: prefs.remove(SLEEP_SINCE_MS)
        }
    }

    override suspend fun clear(shareCode: String) {
        dataStore.edit { prefs ->
            // Code-scoped clear: a concurrent reconnect that already cached a different primary's
            // data keeps it. And because this is the shared "baby_tracker_prefs" store, we remove
            // only the partner_widget_* keys — NEVER prefs.clear(), which would wipe app settings.
            if (prefs[SHARE_CODE] != shareCode) return@edit
            prefs.remove(SHARE_CODE)
            prefs.remove(BABY_NAME)
            prefs.remove(FEED_SIDE)
            prefs.remove(FEED_START_MS)
            prefs.remove(FEED_STATE)
            prefs.remove(SLEEP_STATE)
            prefs.remove(SLEEP_SINCE_MS)
        }
    }

    private companion object {
        val BABY_NAME = stringPreferencesKey("partner_widget_baby_name")
        val FEED_SIDE = stringPreferencesKey("partner_widget_feed_side")
        val FEED_START_MS = longPreferencesKey("partner_widget_feed_start_ms")
        val FEED_STATE = stringPreferencesKey("partner_widget_feed_state")
        val SLEEP_STATE = stringPreferencesKey("partner_widget_sleep_state")
        val SLEEP_SINCE_MS = longPreferencesKey("partner_widget_sleep_since_ms")
        val SHARE_CODE = stringPreferencesKey("partner_widget_share_code")
    }
}
```

- [ ] **Step 5: Bind the cache in WidgetModule**

In `app/src/main/java/com/babytracker/di/WidgetModule.kt`, add imports:

```kotlin
import com.babytracker.widget.PartnerWidgetCache
import com.babytracker.widget.PartnerWidgetCacheImpl
```

Add a binding inside the `WidgetModule` class (after `bindWidgetUpdater`):

```kotlin
    @Binds
    @Singleton
    abstract fun bindPartnerWidgetCache(impl: PartnerWidgetCacheImpl): PartnerWidgetCache
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.PartnerWidgetCacheTest" -PfastTests`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/widget/PartnerWidgetCache.kt \
        app/src/main/java/com/babytracker/widget/PartnerWidgetCacheImpl.kt \
        app/src/main/java/com/babytracker/di/WidgetModule.kt \
        app/src/test/java/com/babytracker/widget/PartnerWidgetCacheTest.kt
git commit -m "feat(widget): add share-code-scoped PartnerWidgetCache"
```

---

## Task 4: WidgetRefreshScheduler (immediate one-time refresh)

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/WidgetRefreshScheduler.kt`
- Create: `app/src/main/java/com/babytracker/widget/WorkManagerWidgetRefreshScheduler.kt`
- Modify: `app/src/main/java/com/babytracker/di/WidgetModule.kt`
- Test: `app/src/test/java/com/babytracker/widget/WorkManagerWidgetRefreshSchedulerTest.kt`

- [ ] **Step 1: Write the failing scheduler test**

Create `app/src/test/java/com/babytracker/widget/WorkManagerWidgetRefreshSchedulerTest.kt`:

```kotlin
package com.babytracker.widget

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerWidgetRefreshSchedulerTest {

    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `scheduleImmediateRefresh enqueues a single unique one-time work`() {
        WorkManagerWidgetRefreshScheduler(context).scheduleImmediateRefresh()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerWidgetRefreshScheduler.IMMEDIATE_UNIQUE_NAME)
            .get()
        assertEquals(1, infos.size)
    }

    @Test
    fun `scheduleImmediateRefresh dedupes while one is already enqueued`() {
        val scheduler = WorkManagerWidgetRefreshScheduler(context)

        scheduler.scheduleImmediateRefresh()
        scheduler.scheduleImmediateRefresh()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerWidgetRefreshScheduler.IMMEDIATE_UNIQUE_NAME)
            .get()
        assertEquals(1, infos.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.WorkManagerWidgetRefreshSchedulerTest" -PfastTests`
Expected: FAIL — `Unresolved reference: WorkManagerWidgetRefreshScheduler`.

- [ ] **Step 3: Create the interface**

Create `app/src/main/java/com/babytracker/widget/WidgetRefreshScheduler.kt`:

```kotlin
package com.babytracker.widget

/**
 * Enqueues an out-of-band widget refresh so the partner widget populates promptly (on connect, or
 * when the render path sees an empty cache) rather than waiting for the 15-min periodic worker.
 */
interface WidgetRefreshScheduler {
    fun scheduleImmediateRefresh()
}
```

- [ ] **Step 4: Create the WorkManager-backed impl**

Create `app/src/main/java/com/babytracker/widget/WorkManagerWidgetRefreshScheduler.kt`:

```kotlin
package com.babytracker.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerWidgetRefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefreshScheduler {

    override fun scheduleImmediateRefresh() {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        // Unique name distinct from the periodic UNIQUE_NAME; KEEP dedupes bursts (e.g. the 60s
        // sync timer repeatedly hitting an empty cache) into a single pending refresh.
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val IMMEDIATE_UNIQUE_NAME = "baby_widget_refresh_now"
    }
}
```

- [ ] **Step 5: Bind the scheduler in WidgetModule**

In `app/src/main/java/com/babytracker/di/WidgetModule.kt`, add imports:

```kotlin
import com.babytracker.widget.WidgetRefreshScheduler
import com.babytracker.widget.WorkManagerWidgetRefreshScheduler
```

Add a binding inside the `WidgetModule` class (after `bindPartnerWidgetCache`):

```kotlin
    @Binds
    @Singleton
    abstract fun bindWidgetRefreshScheduler(impl: WorkManagerWidgetRefreshScheduler): WidgetRefreshScheduler
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.WorkManagerWidgetRefreshSchedulerTest" -PfastTests`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/widget/WidgetRefreshScheduler.kt \
        app/src/main/java/com/babytracker/widget/WorkManagerWidgetRefreshScheduler.kt \
        app/src/main/java/com/babytracker/di/WidgetModule.kt \
        app/src/test/java/com/babytracker/widget/WorkManagerWidgetRefreshSchedulerTest.kt
git commit -m "feat(widget): add WidgetRefreshScheduler for immediate one-time refresh"
```

---

## Task 5: WidgetDataLoader render-path branch + cache-miss priming + EntryPoint

**Files:**
- Modify: `app/src/main/java/com/babytracker/widget/WidgetEntryPoint.kt`
- Modify: `app/src/main/java/com/babytracker/widget/WidgetDataLoader.kt`
- Test: `app/src/test/java/com/babytracker/widget/WidgetDataLoaderTest.kt`

- [ ] **Step 1: Rewrite the loader test (new signature + partner + priming cases)**

Replace the entire contents of `app/src/test/java/com/babytracker/widget/WidgetDataLoaderTest.kt` with:

```kotlin
package com.babytracker.widget

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.widget.data.FeedState
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class WidgetDataLoaderTest {

    private val happyBaby = Baby(name = "Akira", birthDate = LocalDate.of(2025, 12, 1))
    private val happyFeed = BreastfeedingSession(
        id = 1,
        startTime = Instant.parse("2026-05-27T10:00:00Z"),
        endTime = Instant.parse("2026-05-27T10:15:00Z"),
        startingSide = BreastSide.LEFT,
    )

    private fun noneSettings(): SettingsRepository = mockk {
        every { getAppMode() } returns flowOf(AppMode.NONE)
    }

    @Test
    fun `none mode maps local repositories to WidgetData`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk { coEvery { getLastSession() } returns happyFeed }
        val sleep: SleepRepository = mockk { coEvery { getLatestRecord() } returns null }
        val scheduler: WidgetRefreshScheduler = mockk(relaxUnitFun = true)

        val result = loadWidgetData(
            settings = noneSettings(),
            partnerCache = mockk(),
            scheduler = scheduler,
            baby = baby,
            feed = feed,
            sleep = sleep,
        )

        assertEquals("Akira", result.babyName)
        assertEquals(BreastSide.LEFT, result.lastFeedSide)
        assertEquals(SleepState.NONE, result.sleepState)
        verify(exactly = 0) { scheduler.scheduleImmediateRefresh() }
    }

    @Test
    fun `baby repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk {
            every { getBabyProfile() } returns flow { throw IllegalStateException("datastore busted") }
        }
        val feed: BreastfeedingRepository = mockk()
        val sleep: SleepRepository = mockk()

        val result = loadWidgetData(
            settings = noneSettings(),
            partnerCache = mockk(),
            scheduler = mockk(relaxUnitFun = true),
            baby = baby,
            feed = feed,
            sleep = sleep,
        )

        assertEquals(WidgetData.EMPTY, result)
    }

    @Test
    fun `feed repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk {
            coEvery { getLastSession() } throws IllegalStateException("room busted")
        }
        val sleep: SleepRepository = mockk()

        val result = loadWidgetData(
            settings = noneSettings(),
            partnerCache = mockk(),
            scheduler = mockk(relaxUnitFun = true),
            baby = baby,
            feed = feed,
            sleep = sleep,
        )

        assertEquals(WidgetData.EMPTY, result)
    }

    @Test
    fun `sleep repository failure falls back to EMPTY`() = runTest {
        val baby: BabyRepository = mockk { every { getBabyProfile() } returns flowOf(happyBaby) }
        val feed: BreastfeedingRepository = mockk { coEvery { getLastSession() } returns happyFeed }
        val sleep: SleepRepository = mockk {
            coEvery { getLatestRecord() } throws IllegalStateException("room busted")
        }

        val result = loadWidgetData(
            settings = noneSettings(),
            partnerCache = mockk(),
            scheduler = mockk(relaxUnitFun = true),
            baby = baby,
            feed = feed,
            sleep = sleep,
        )

        assertEquals(WidgetData.EMPTY, result)
    }

    @Test
    fun `partner mode returns cached data and does not schedule a refresh`() = runTest {
        val cached = WidgetData(
            babyName = "Akira",
            lastFeedSide = BreastSide.RIGHT,
            lastFeedStart = Instant.parse("2026-05-27T10:00:00Z"),
            feedState = FeedState.RECENT,
            sleepState = SleepState.SLEEPING,
            sleepSince = Instant.parse("2026-05-27T11:00:00Z"),
        )
        val settings: SettingsRepository = mockk {
            every { getAppMode() } returns flowOf(AppMode.PARTNER)
            every { getShareCode() } returns flowOf("CODE")
        }
        val cache: PartnerWidgetCache = mockk { coEvery { read("CODE") } returns cached }
        val scheduler: WidgetRefreshScheduler = mockk(relaxUnitFun = true)

        val result = loadWidgetData(
            settings = settings,
            partnerCache = cache,
            scheduler = scheduler,
            baby = mockk(),
            feed = mockk(),
            sleep = mockk(),
        )

        assertEquals(cached, result)
        verify(exactly = 0) { scheduler.scheduleImmediateRefresh() }
    }

    @Test
    fun `partner mode cache-miss returns EMPTY and schedules an immediate refresh`() = runTest {
        // read() returns null both when nothing is cached and when the cached share code mismatches
        // (mismatch is exercised directly in PartnerWidgetCacheTest).
        val settings: SettingsRepository = mockk {
            every { getAppMode() } returns flowOf(AppMode.PARTNER)
            every { getShareCode() } returns flowOf("CODE")
        }
        val cache: PartnerWidgetCache = mockk { coEvery { read("CODE") } returns null }
        val scheduler: WidgetRefreshScheduler = mockk(relaxUnitFun = true)

        val result = loadWidgetData(
            settings = settings,
            partnerCache = cache,
            scheduler = scheduler,
            baby = mockk(),
            feed = mockk(),
            sleep = mockk(),
        )

        assertEquals(WidgetData.EMPTY, result)
        verify(exactly = 1) { scheduler.scheduleImmediateRefresh() }
    }

    @Test
    fun `partner mode with null share code returns EMPTY without reading cache or scheduling`() = runTest {
        val settings: SettingsRepository = mockk {
            every { getAppMode() } returns flowOf(AppMode.PARTNER)
            every { getShareCode() } returns flowOf(null)
        }
        val cache: PartnerWidgetCache = mockk()
        val scheduler: WidgetRefreshScheduler = mockk(relaxUnitFun = true)

        val result = loadWidgetData(
            settings = settings,
            partnerCache = cache,
            scheduler = scheduler,
            baby = mockk(),
            feed = mockk(),
            sleep = mockk(),
        )

        assertEquals(WidgetData.EMPTY, result)
        coVerifyCacheNeverRead(cache)
        verify(exactly = 0) { scheduler.scheduleImmediateRefresh() }
    }

    private fun coVerifyCacheNeverRead(cache: PartnerWidgetCache) {
        io.mockk.coVerify(exactly = 0) { cache.read(any()) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.WidgetDataLoaderTest" -PfastTests`
Expected: FAIL — `loadWidgetData(...)` does not accept the `settings` / `partnerCache` / `scheduler` arguments.

- [ ] **Step 3: Add EntryPoint accessors**

Replace the entire contents of `app/src/main/java/com/babytracker/widget/WidgetEntryPoint.kt` with:

```kotlin
package com.babytracker.widget

import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun babyRepository(): BabyRepository
    fun breastfeedingRepository(): BreastfeedingRepository
    fun sleepRepository(): SleepRepository
    fun settingsRepository(): SettingsRepository
    fun partnerWidgetCache(): PartnerWidgetCache
    fun widgetRefreshScheduler(): WidgetRefreshScheduler
}
```

- [ ] **Step 4: Branch the loader on AppMode + prime on cache-miss**

Replace the entire contents of `app/src/main/java/com/babytracker/widget/WidgetDataLoader.kt` with:

```kotlin
package com.babytracker.widget

import android.content.Context
import android.util.Log
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.widget.data.WidgetData
import com.babytracker.widget.data.toWidgetData
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val TAG = "BabyWidget"

suspend fun loadWidgetData(context: Context): WidgetData {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetEntryPoint::class.java,
    )
    return loadWidgetData(
        settings = entryPoint.settingsRepository(),
        partnerCache = entryPoint.partnerWidgetCache(),
        scheduler = entryPoint.widgetRefreshScheduler(),
        baby = entryPoint.babyRepository(),
        feed = entryPoint.breastfeedingRepository(),
        sleep = entryPoint.sleepRepository(),
    )
}

internal suspend fun loadWidgetData(
    settings: SettingsRepository,
    partnerCache: PartnerWidgetCache,
    scheduler: WidgetRefreshScheduler,
    baby: BabyRepository,
    feed: BreastfeedingRepository,
    sleep: SleepRepository,
): WidgetData = runCatching {
    if (settings.getAppMode().first() == AppMode.PARTNER) {
        // PARTNER: never touch local repos or the network. Read the worker-populated cache; on a
        // miss, kick off a one-time refresh (no network on this thread) and render EMPTY for now.
        val shareCode = settings.getShareCode().first() ?: return@runCatching WidgetData.EMPTY
        partnerCache.read(shareCode) ?: run {
            scheduler.scheduleImmediateRefresh()
            WidgetData.EMPTY
        }
    } else {
        val babyProfile = baby.getBabyProfile().first()
        val lastFeed = feed.getLastSession()
        val latestSleep = sleep.getLatestRecord()
        toWidgetData(
            babyName = babyProfile?.name,
            lastFeed = lastFeed,
            latestSleep = latestSleep,
        )
    }
}.getOrElse { error ->
    if (error is CancellationException) throw error
    Log.w(TAG, "Widget data load failed; rendering EMPTY", error)
    WidgetData.EMPTY
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.WidgetDataLoaderTest" -PfastTests`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/widget/WidgetEntryPoint.kt \
        app/src/main/java/com/babytracker/widget/WidgetDataLoader.kt \
        app/src/test/java/com/babytracker/widget/WidgetDataLoaderTest.kt
git commit -m "feat(widget): render partner cache and prime it on cache-miss"
```

---

## Task 6: WidgetRefreshWorker partner refresh

**Files:**
- Modify: `app/src/main/java/com/babytracker/widget/WidgetRefreshWorker.kt`
- Test: `app/src/test/java/com/babytracker/widget/WidgetRefreshWorkerTest.kt`

- [ ] **Step 1: Rewrite the worker test (new ctor + partner + code-scoped clear cases)**

Replace the entire contents of `app/src/test/java/com/babytracker/widget/WidgetRefreshWorkerTest.kt` with:

```kotlin
package com.babytracker.widget

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class WidgetRefreshWorkerTest {

    private val snapshot = ShareSnapshot(
        lastSyncAt = Instant.now(),
        baby = BabySnapshot("Akira", 0L, emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
    )

    private fun buildWorker(
        updater: WidgetUpdater,
        settings: SettingsRepository,
        fetchPartnerData: FetchPartnerDataUseCase,
        partnerCache: PartnerWidgetCache,
    ): WidgetRefreshWorker {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return TestListenableWorkerBuilder<WidgetRefreshWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = WidgetRefreshWorker(
                        appContext,
                        workerParameters,
                        updater,
                        settings,
                        fetchPartnerData,
                        partnerCache,
                    )
                },
            )
            .build()
    }

    @Test
    fun `non-partner mode updates widgets without fetching and returns success`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk { coEvery { getAppMode() } returns flowOf(AppMode.NONE) }
        val fetch: FetchPartnerDataUseCase = mockk()
        val cache: PartnerWidgetCache = mockk()

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        coVerify(exactly = 1) { updater.updateAll() }
        coVerify(exactly = 0) { fetch(any<ShareCode>()) }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `non-partner mode returns retry when updater throws`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } throws IllegalStateException("transient") }
        val settings: SettingsRepository = mockk { coEvery { getAppMode() } returns flowOf(AppMode.NONE) }
        val fetch: FetchPartnerDataUseCase = mockk()
        val cache: PartnerWidgetCache = mockk()

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `partner success fetches and saves under the same share code`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk {
            coEvery { getAppMode() } returns flowOf(AppMode.PARTNER)
            coEvery { getShareCode() } returns flowOf("CODE")
        }
        val fetch: FetchPartnerDataUseCase = mockk()
        coEvery { fetch(ShareCode("CODE")) } returns snapshot
        val cache: PartnerWidgetCache = mockk { coEvery { save(any(), any()) } just Runs }

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        // The worker fetched the exact code it read and cached the result under that same code —
        // no path by which one primary's snapshot lands under another's key.
        coVerify(exactly = 1) { fetch(ShareCode("CODE")) }
        coVerify(exactly = 1) { cache.save("CODE", any()) }
        coVerify(exactly = 1) { updater.updateAll() }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `partner mode with null share code updates widgets and returns success without fetching`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk {
            coEvery { getAppMode() } returns flowOf(AppMode.PARTNER)
            coEvery { getShareCode() } returns flowOf(null)
        }
        val fetch: FetchPartnerDataUseCase = mockk()
        val cache: PartnerWidgetCache = mockk()

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        coVerify(exactly = 1) { updater.updateAll() }
        coVerify(exactly = 0) { fetch(any<ShareCode>()) }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `partner revoke clears the cache for the tried code and returns success without retry`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk {
            coEvery { getAppMode() } returns flowOf(AppMode.PARTNER)
            coEvery { getShareCode() } returns flowOf("CODE")
        }
        val fetch: FetchPartnerDataUseCase = mockk()
        coEvery { fetch(ShareCode("CODE")) } throws PartnerAccessRevokedException("Partner access revoked")
        val cache: PartnerWidgetCache = mockk {
            coEvery { clear(any()) } just Runs
            coEvery { save(any(), any()) } just Runs
        }

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        coVerify(exactly = 1) { cache.clear("CODE") }
        coVerify(exactly = 0) { cache.save(any(), any()) }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `partner generic IllegalStateException retries and leaves cache untouched`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk {
            coEvery { getAppMode() } returns flowOf(AppMode.PARTNER)
            coEvery { getShareCode() } returns flowOf("CODE")
        }
        val fetch: FetchPartnerDataUseCase = mockk()
        coEvery { fetch(ShareCode("CODE")) } throws IllegalStateException("boom")
        val cache: PartnerWidgetCache = mockk()

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        coVerify(exactly = 0) { cache.save(any(), any()) }
        coVerify(exactly = 0) { cache.clear(any()) }
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `partner transient failure retries and leaves cache untouched`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk {
            coEvery { getAppMode() } returns flowOf(AppMode.PARTNER)
            coEvery { getShareCode() } returns flowOf("CODE")
        }
        val fetch: FetchPartnerDataUseCase = mockk()
        coEvery { fetch(ShareCode("CODE")) } throws RuntimeException("network down")
        val cache: PartnerWidgetCache = mockk()

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        coVerify(exactly = 0) { cache.save(any(), any()) }
        coVerify(exactly = 0) { cache.clear(any()) }
        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.WidgetRefreshWorkerTest" -PfastTests`
Expected: FAIL — `WidgetRefreshWorker(...)` constructor does not accept the 4th–6th arguments.

- [ ] **Step 3: Implement the partner refresh path in the worker**

Replace the entire contents of `app/src/main/java/com/babytracker/widget/WidgetRefreshWorker.kt` with:

```kotlin
package com.babytracker.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.widget.data.toWidgetData
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val TAG = "WidgetRefreshWorker"

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updater: WidgetUpdater,
    private val settings: SettingsRepository,
    private val fetchPartnerData: FetchPartnerDataUseCase,
    private val partnerCache: PartnerWidgetCache,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        runCatching {
            if (settings.getAppMode().first() == AppMode.PARTNER) {
                refreshPartner()
            } else {
                updater.updateAll()
                Result.success()
            }
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            Log.w(TAG, "Periodic widget refresh failed; will retry", t)
            Result.retry()
        }

    private suspend fun refreshPartner(): Result {
        val shareCode = settings.getShareCode().first()
        if (shareCode == null) {
            // Partner mode but no code yet — nothing to fetch; the render path already shows EMPTY.
            updater.updateAll()
            return Result.success()
        }
        val code = ShareCode(shareCode)
        return runCatching { fetchPartnerData(code) }.fold(
            onSuccess = { snapshot ->
                // Snapshot provably belongs to `code` (explicit fetch), so tagging the cache with
                // the same `shareCode` can never mix two primaries.
                partnerCache.save(shareCode, toWidgetData(snapshot))
                updater.updateAll()
                Result.success()
            },
            onFailure = { t ->
                when {
                    t is CancellationException -> throw t
                    // Confirmed revoke (use case already cleared the matching share state):
                    // drop only the cache tagged with the code we tried. No retry. A concurrent
                    // reconnect to a different primary keeps its own freshly-saved cache.
                    t is PartnerAccessRevokedException -> {
                        partnerCache.clear(shareCode)
                        updater.updateAll()
                        Result.success()
                    }
                    // Any other failure (network / sign-in / bug): keep the stale cache, re-render
                    // it, and retry. A misclassified error never empties it.
                    else -> {
                        Log.w(TAG, "Partner snapshot refresh failed; will retry", t)
                        updater.updateAll()
                        Result.retry()
                    }
                }
            },
        )
    }

    companion object {
        const val UNIQUE_NAME = "baby_widget_refresh"
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.WidgetRefreshWorkerTest" -PfastTests`
Expected: PASS (all 6 cases).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/widget/WidgetRefreshWorker.kt \
        app/src/test/java/com/babytracker/widget/WidgetRefreshWorkerTest.kt
git commit -m "feat(widget): refresh partner widget cache in WidgetRefreshWorker"
```

---

## Task 7: Prime the widget on partner connect

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sharing/ConnectPartnerViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/sharing/ConnectPartnerViewModelTest.kt`

- [ ] **Step 1: Update the ViewModel test (new ctor + immediate-refresh cases)**

Replace the entire contents of `app/src/test/java/com/babytracker/ui/sharing/ConnectPartnerViewModelTest.kt` with:

```kotlin
package com.babytracker.ui.sharing

import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.usecase.ConnectAsPartnerUseCase
import com.babytracker.widget.WidgetRefreshScheduler
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConnectPartnerViewModelTest {

    private lateinit var connectAsPartnerUseCase: ConnectAsPartnerUseCase
    private lateinit var widgetRefreshScheduler: WidgetRefreshScheduler
    private lateinit var viewModel: ConnectPartnerViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        connectAsPartnerUseCase = mockk()
        widgetRefreshScheduler = mockk(relaxUnitFun = true)
        viewModel = ConnectPartnerViewModel(connectAsPartnerUseCase, widgetRefreshScheduler)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onCodeChanged normalizes to uppercase and strips non-alphanumeric`() {
        viewModel.onCodeChanged("abc-12!3")

        assertEquals("ABC123", viewModel.uiState.value.code)
    }

    @Test
    fun `onCodeChanged truncates to 8 characters`() {
        viewModel.onCodeChanged("ABCDEFGH1234")

        assertEquals("ABCDEFGH", viewModel.uiState.value.code)
    }

    @Test
    fun `onConnect calls use case with current code`() = runTest {
        val code = "ABCD1234"
        coJustRun { connectAsPartnerUseCase(ShareCode(code)) }
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        coVerify { connectAsPartnerUseCase(ShareCode(code)) }
    }

    @Test
    fun `onConnect sets isConnected and schedules an immediate widget refresh on success`() = runTest {
        val code = "ABCD1234"
        coJustRun { connectAsPartnerUseCase(ShareCode(code)) }
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        assertTrue(viewModel.uiState.value.isConnected)
        assertNull(viewModel.uiState.value.error)
        verify(exactly = 1) { widgetRefreshScheduler.scheduleImmediateRefresh() }
    }

    @Test
    fun `onConnect shows not-found error and does not schedule on IllegalStateException`() = runTest {
        val code = "ABCD1234"
        coEvery { connectAsPartnerUseCase(ShareCode(code)) } throws IllegalStateException("not found")
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.contains("doesn't exist"))
        verify(exactly = 0) { widgetRefreshScheduler.scheduleImmediateRefresh() }
    }

    @Test
    fun `onConnect shows connection error and does not schedule on generic exception`() = runTest {
        val code = "ABCD1234"
        coEvery { connectAsPartnerUseCase(ShareCode(code)) } throws RuntimeException("Network error")
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.contains("connection"))
        verify(exactly = 0) { widgetRefreshScheduler.scheduleImmediateRefresh() }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.sharing.ConnectPartnerViewModelTest" -PfastTests`
Expected: FAIL — `ConnectPartnerViewModel(...)` constructor does not accept the 2nd argument.

- [ ] **Step 3: Inject the scheduler and prime on success**

In `app/src/main/java/com/babytracker/ui/sharing/ConnectPartnerViewModel.kt`, add the import:

```kotlin
import com.babytracker.widget.WidgetRefreshScheduler
```

Change the constructor:

```kotlin
@HiltViewModel
class ConnectPartnerViewModel @Inject constructor(
    private val connectAsPartnerUseCase: ConnectAsPartnerUseCase,
    private val widgetRefreshScheduler: WidgetRefreshScheduler,
) : ViewModel() {
```

In `onConnect()`, add the scheduler call immediately after the successful use-case invocation:

```kotlin
            try {
                connectAsPartnerUseCase(ShareCode(code))
                // Partner just connected: prime the widget cache now instead of waiting for the
                // 15-min periodic worker.
                widgetRefreshScheduler.scheduleImmediateRefresh()
                _uiState.update { it.copy(isLoading = false, isConnected = true) }
            } catch (_: IllegalStateException) {
```

(Leave both `catch` blocks unchanged.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.sharing.ConnectPartnerViewModelTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sharing/ConnectPartnerViewModel.kt \
        app/src/test/java/com/babytracker/ui/sharing/ConnectPartnerViewModelTest.kt
git commit -m "feat(partner): prime the widget cache immediately on partner connect"
```

---

## Task 8: Full verification & wrap-up

**Files:** none (verification only).

- [ ] **Step 1: Run ktlint format + detekt**

Run: `./gradlew ktlintFormat detekt`
Expected: BUILD SUCCESSFUL. If ktlint reformats anything, re-stage and amend the relevant commit (or add a `style:` commit). If detekt reports a violation, **fix the code — never `@Suppress`** (per `CLAUDE.md`), as its own `fix(detekt): ...` commit. Watch for an unused `AppMode` import in `FetchPartnerDataUseCase` (removed in Task 1 Step 7 if flagged).

- [ ] **Step 2: Run the full unit-test suite (matches CI — includes architecture tests)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass. (No `-PfastTests` here — the Konsist architecture tests must run before pushing; first cold run is slow on WSL/NTFS, see `CLAUDE.md`.) Confirms `ConnectPartnerViewModel → widget` and the new scheduler class pass `LayerIsolationTest` / `NamingConventionTest`.

- [ ] **Step 3: Confirm acceptance criteria**

Verify each maps to shipped code:

- Partner widget shows last feed + sleep from snapshot → `toWidgetData(snapshot)` (Task 2) + loader partner branch (Task 5).
- `NONE` / `PRIMARY` unchanged → loader `else` branch + the local-path loader tests (Task 5).
- No blocking network on Glance render → loader reads `partnerCache.read` only; fetch lives in the worker; cache-miss only *enqueues* work (Task 5 / 6).
- Refreshes via periodic worker, no local-flow reliance → `WidgetRefreshWorker.refreshPartner` (Task 6); `WidgetSyncManager` untouched.
- Snapshot → `WidgetData` is pure + JVM-tested → `WidgetDataMapperTest` snapshot cases (Task 2).
- **Paused feeds — explicit acceptance narrowing:** a partner widget renders an in-progress feed as `ACTIVE` even when the primary has paused it, because `SessionSnapshot` carries no `pausedAt`. This is **in scope as a known limitation, not a bug**: it matches `PartnerDashboardScreen` (same blind spot), so the widget introduces no new discrepancy. Carrying pause state in the shared snapshot (`SessionSnapshot.pausedAt` → `BreastfeedingSession.pausedAt`, treating missing as unknown) is deferred to the follow-up tracked in the AKA-57 design spec's *Out of scope* section — it changes the Firestore sync contract, which `CLAUDE.md` gates behind an explicit design decision and AKA-57 scoped out.
- Revoke doesn't crash; falls back to `EMPTY` → `PartnerAccessRevokedException` (Task 1) → worker `cache.clear(code)` (Task 6) → loader `EMPTY` (Task 5).
- Cache scoped to active share → `PartnerWidgetCache` keyed by code (Task 3); loader passes current code (Task 5); mismatch test green.
- **Review fix 1 — stale revoke can't erase a newer connection; no cross-tenant cache** → atomic `clearPartnerStateIfShareCodeMatches` (Task 1) + explicit-code fetch and code-scoped `cache.save`/`cache.clear` (Task 1 / 3 / 6); covered by `SettingsPartnerStateTest`, the worker same-code fetch/save test, and the use-case explicit-overload test.
- **Review fix 2 — widget populates promptly on connect / cache-miss** → `WidgetRefreshScheduler` (Task 4) fired from connect (Task 7) and render cache-miss (Task 5); covered by scheduler, loader, and VM tests.

---

## Self-Review Notes

- **Shared DataStore hazard:** `PartnerWidgetCacheImpl` and `SettingsRepositoryImpl` both use the single `baby_tracker_prefs` store. `clear(shareCode)` removes only the 7 `partner_widget_*` keys (never `prefs.clear()`); `clearPartnerStateIfShareCodeMatches` touches only `SHARE_CODE` + `APP_MODE`. Both verified by their tests.
- **Race fix 1 (revoke vs. reconnect):** settings cleanup is atomic and code-conditional (single `dataStore.edit`); the worker's cache clear is likewise code-scoped. A stale refresh for primary A cannot wipe a fresh connection/cache for primary B. Tested in `SettingsPartnerStateTest` (mismatch → no-op) and the worker `clear("CODE")` case.
- **Race fix 2 (prompt population):** immediate refresh enqueued on connect (ConnectPartnerViewModel) and on render cache-miss (loader); `ExistingWorkPolicy.KEEP` dedupes bursts from the 60s sync timer. The worker still self-heals transient failures via `Result.retry()`.
- **Tenant isolation (airtight):** the worker fetches with an explicit `ShareCode` and saves/clears under that same code; the use case never re-reads settings to pick a code mid-fetch, so a reconnect during a fetch can never cache one primary's snapshot under another's key. Verified by `WidgetRefreshWorkerTest` (fetch + save use the same code) and `FetchPartnerDataUseCaseTest` (the explicit overload uses the passed code, not settings).
- **Breaking internal signatures:** `loadWidgetData(...)` (Task 5), the `WidgetRefreshWorker` constructor (Task 6), and the `ConnectPartnerViewModel` constructor (Task 7) all gain parameters; each task rewrites the matching test so the tree compiles after every task.
- **Paused feeds → ACTIVE:** accepted limitation; `toDomainSession` sets `pausedAt = null`, documented inline (Task 2) and in the design spec.
- **Architecture rules:** `ui/sharing → com.babytracker.widget` is allowed (`LayerIsolationTest` forbids only `ui → data`); scheduler impl naming is exempt from `*RepositoryImpl` (parent `Scheduler`, not in `..repository..`). No firebase imports added outside `sharing`.
- **Type consistency:** `read(shareCode)`, `save(shareCode, data)`, `clear(shareCode)`, `scheduleImmediateRefresh()`, `clearPartnerStateIfShareCodeMatches(code): Boolean`, and `PartnerAccessRevokedException(message)` are referenced identically across interfaces, impls, loader, worker, VM, and all tests.
```
