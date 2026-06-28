# Partner Dashboard — Live Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the partner dashboard's one-shot Firestore `get()` read path with a realtime `addSnapshotListener` flow, so the primary's changes appear within ~1s and the partner's own actions appear instantly — while retaining optimistic op overlays until the live snapshot catches up.

**Architecture:** A new `ObservePartnerDataUseCase` streams the share document (plus a cheap partner-doc listener for disconnect detection). A pure `reconcilePendingOps` helper retains an op overlay until the snapshot reflects it or a TTL fires, defeating the "op deleted before snapshot update" flicker. The history use cases switch from a fixed snapshot list to the live snapshot **stream** + reconciliation; the view models and screen drop all manual refresh affordances.

**Tech Stack:** Kotlin Coroutines + Flow (`callbackFlow`, `combine`, `scan`), Hilt (constructor injection — no module edits), Firestore `addSnapshotListener`, JUnit 5 + MockK + Turbine for unit tests, Compose UI Test for instrumentation.

## Global Constraints

- **No new dependencies, no Firestore rule changes, no Room migration.** Pure client refactor of the partner read path.
- **Service subcollection ops are extension functions**, not member functions — the class is at detekt's `TooManyFunctions` ceiling. Add `observeSnapshot`/`observePartnerConnected` as extensions, mirroring `observeSleepOps`.
- **Domain models are pure Kotlin** — zero framework imports in `sharing/domain/model/`.
- **No Mapper classes, no BaseViewModel, no `sealed Result<T>` wrappers.** Let exceptions propagate or use nullable types.
- **`() -> Instant` is Hilt-provided** (`DatabaseModule.provideNowProvider()`); use cases take `now: () -> Instant = Instant::now` and tests pass a fixed clock.
- **Detekt fixes are never `@Suppress`** — fix the code. The pre-commit hook runs ktlint + detekt automatically; do not run them manually unless diagnosing a failure.
- **`FetchPartnerDataUseCase` and `WidgetRefreshWorker` stay unchanged** — the widget worker cannot hold a long-lived listener and keeps the one-shot fetch.
- **Cache-origin caveat is the central correctness rule:** absence/disconnect is acted on **only when server-confirmed** (`isFromCache == false`). A cache-origin missing snapshot must NOT clear local partner state.

---

## File Structure

**New files**
- `app/src/main/java/com/babytracker/sharing/domain/model/ReconcilePendingOps.kt` — `PendingOp` interface, `PENDING_OP_TTL_MS`, `Reconciled<O>`, pure `reconcilePendingOps`.
- `app/src/main/java/com/babytracker/sharing/usecase/ObservePartnerDataUseCase.kt` — live snapshot use case.
- `app/src/test/java/com/babytracker/sharing/domain/model/ReconcilePendingOpsTest.kt`
- `app/src/test/java/com/babytracker/sharing/usecase/ObservePartnerDataUseCaseTest.kt`

**Modified production files**
- `sharing/domain/model/FeedOp.kt`, `SleepOp.kt` — implement `PendingOp`.
- `sharing/domain/model/MergeFeedHistory.kt` — add `feedOpReflected`.
- `sharing/domain/model/MergePartnerSleep.kt` — rename `PENDING_SLEEP_OP_TTL_MS` → `PENDING_OP_TTL_MS`; add `sleepEditReflected`, `sleepActiveReflected`.
- `sharing/data/firebase/FirestoreSharingService.kt` — `PARTNERS` → internal; add `observeSnapshot`, `observePartnerConnected`, `SnapshotEmission`, `ConnectionEmission`.
- `sharing/usecase/ObservePartnerFeedHistoryUseCase.kt`, `ObservePartnerSleepHistoryUseCase.kt` — take live snapshot **stream**; reconcile before merge.
- `ui/partner/PartnerHistoryRefresh.kt` — replace `refreshPartnerHistory`/`hasConsumedPendingOps` with a slim `collectPartnerHistory`.
- `ui/partner/PartnerFeedHistoryViewModel.kt`, `PartnerSleepHistoryViewModel.kt` — collect the live reconciling flow; drop the refetch dance.
- `ui/partner/PartnerSleepViewModel.kt` — replace `snapshotRefreshTick` with reconciliation.
- `ui/partner/PartnerDashboardViewModel.kt` — collect the live flow; drop `refresh`/`lastRefreshAt`.
- `ui/partner/PartnerDashboardScreen.kt` — remove refresh affordances; thread `sleepState.active` into the sleep tile.

**Modified test files** — see each task.

---

## Task 1: Pure reconciliation helper + reflection predicates

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/domain/model/ReconcilePendingOps.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/FeedOp.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/SleepOp.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/MergeFeedHistory.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/MergePartnerSleep.kt`
- Test: `app/src/test/java/com/babytracker/sharing/domain/model/ReconcilePendingOpsTest.kt`

**Interfaces:**
- Produces: `interface PendingOp { val opId: String; val createdAtMs: Long }`; `const val PENDING_OP_TTL_MS = 60_000L`; `data class Reconciled<O>(val effectiveOps: List<O>, val nextTracked: List<O>)`; `fun <O : PendingOp> reconcilePendingOps(isReflected: (O) -> Boolean, liveOps: List<O>, tracked: List<O>, nowMs: Long, ttlMs: Long = PENDING_OP_TTL_MS): Reconciled<O>`.
- Produces: `fun feedOpReflected(op: FeedOp, snapshotFeeds: List<BottleFeedSnapshot>): Boolean`; `fun sleepEditReflected(op: SleepOp, snapshotRecords: List<SleepSnapshot>): Boolean`; `fun sleepActiveReflected(op: SleepOp, snapshotRecords: List<SleepSnapshot>): Boolean`.
- Consumes: `FeedOp`, `SleepOp`, `BottleFeedSnapshot`, `SleepSnapshot`, `FeedOpAction`, `SleepOpAction`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/babytracker/sharing/domain/model/ReconcilePendingOpsTest.kt`:

```kotlin
package com.babytracker.sharing.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReconcilePendingOpsTest {

    private val t0 = 1_000_000L

    private fun feedCreate(clientId: String, createdAtMs: Long = t0) = FeedOp(
        opId = "op-$clientId", action = FeedOpAction.CREATE, entryClientId = clientId,
        authorUid = "uid", createdAtMs = createdAtMs, timestampMs = 5_000L, volumeMl = 90, type = "FORMULA",
    )

    private fun feedSnapshot(clientId: String) = BottleFeedSnapshot(
        timestamp = 5_000L, volumeMl = 90, type = "FORMULA", clientId = clientId, author = "PARTNER",
    )

    // --- A.1 regression: op deleted BEFORE its snapshot update keeps overlaying. ---

    @Test
    fun `feed create op-delete before snapshot update retains overlay then converges`() {
        val op = feedCreate("c1")
        val stale = emptyList<BottleFeedSnapshot>()
        val fresh = listOf(feedSnapshot("c1"))

        // Round 1: op live, snapshot stale.
        val r1 = reconcilePendingOps({ feedOpReflected(it, stale) }, listOf(op), emptyList(), nowMs = t0)
        assertEquals(listOf(op), r1.effectiveOps)
        assertEquals(listOf(op), r1.nextTracked)

        // Round 2: op DELETED from listener, snapshot STILL stale -> overlay retained.
        val r2 = reconcilePendingOps({ feedOpReflected(it, stale) }, emptyList(), r1.nextTracked, nowMs = t0 + 100)
        assertEquals(listOf(op), r2.effectiveOps)

        // Round 3: snapshot now reflects the op -> overlay dropped, snapshot wins.
        val r3 = reconcilePendingOps({ feedOpReflected(it, fresh) }, emptyList(), r2.nextTracked, nowMs = t0 + 200)
        assertTrue(r3.effectiveOps.isEmpty())
        assertTrue(r3.nextTracked.isEmpty())
    }

    @Test
    fun `feed delete op is reflected once the entry disappears from the snapshot`() {
        val op = FeedOp("op-d", FeedOpAction.DELETE, "c1", "uid", t0)
        assertFalse(feedOpReflected(op, listOf(feedSnapshot("c1")))) // still present -> not reflected
        assertTrue(feedOpReflected(op, emptyList())) // gone -> reflected
    }

    @Test
    fun `rejected op is dropped once the TTL elapses`() {
        val op = feedCreate("c1", createdAtMs = t0)
        // Op vanished, snapshot never reflects it, now past TTL.
        val r = reconcilePendingOps(
            { feedOpReflected(it, emptyList()) }, emptyList(), listOf(op), nowMs = t0 + PENDING_OP_TTL_MS + 1,
        )
        assertTrue(r.effectiveOps.isEmpty())
        assertTrue(r.nextTracked.isEmpty())
    }

    @Test
    fun `already-reflected live op is included but never carried`() {
        val op = feedCreate("c1")
        val fresh = listOf(feedSnapshot("c1"))
        // Op still live AND snapshot already reflects it.
        val r = reconcilePendingOps({ feedOpReflected(it, fresh) }, listOf(op), emptyList(), nowMs = t0)
        assertEquals(listOf(op), r.effectiveOps) // live ops always included (idempotent overlay)
        assertTrue(r.nextTracked.isEmpty()) // but reflected -> not carried, so it won't reappear after deletion
    }

    // --- Sleep predicates ---

    private fun record(clientId: String, endTime: Long?, sleepType: String = "NAP", start: Long = 1_000L, notes: String? = null) =
        SleepSnapshot(0, start, endTime, sleepType, notes, clientId = clientId, startedBy = "PARTNER")

    @Test
    fun `sleep start is reflected once the snapshot has the session`() {
        val op = SleepOp("op-s", SleepOpAction.START, "c1", "uid", t0, startTimeMs = 1_000L, sleepType = "NAP")
        assertFalse(sleepActiveReflected(op, emptyList()))
        assertTrue(sleepActiveReflected(op, listOf(record("c1", endTime = null))))
    }

    @Test
    fun `sleep stop is reflected once the snapshot session is ended or gone`() {
        val op = SleepOp("op-x", SleepOpAction.STOP, "c1", "uid", t0, endTimeMs = 9_000L)
        assertFalse(sleepActiveReflected(op, listOf(record("c1", endTime = null)))) // still active -> not reflected
        assertTrue(sleepActiveReflected(op, listOf(record("c1", endTime = 9_000L)))) // ended -> reflected
        assertTrue(sleepActiveReflected(op, emptyList())) // gone -> reflected
    }

    @Test
    fun `sleep edit is reflected once the snapshot record matches the edited fields`() {
        val op = SleepOp("op-e", SleepOpAction.UPDATE, "c1", "uid", t0, startTimeMs = 2_000L, endTimeMs = 8_000L, sleepType = "NIGHT_SLEEP", notes = "x")
        assertFalse(sleepEditReflected(op, listOf(record("c1", endTime = 9_000L)))) // endTime mismatch
        assertTrue(sleepEditReflected(op, listOf(record("c1", endTime = 8_000L, sleepType = "NIGHT_SLEEP", start = 2_000L, notes = "x"))))
        // START/STOP are not history edits -> treated as reflected so they never pin the history overlay.
        assertTrue(sleepEditReflected(SleepOp("op-s", SleepOpAction.START, "c1", "uid", t0), emptyList()))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.sharing.domain.model.ReconcilePendingOpsTest"`
Expected: FAIL — `reconcilePendingOps`, `feedOpReflected`, `sleepEditReflected`, `sleepActiveReflected`, `PendingOp`, `PENDING_OP_TTL_MS` unresolved.

- [ ] **Step 3: Create the reconciliation helper**

Create `app/src/main/java/com/babytracker/sharing/domain/model/ReconcilePendingOps.kt`:

```kotlin
package com.babytracker.sharing.domain.model

/** Ops carried across snapshot/op emissions need identity + age. Both [FeedOp] and [SleepOp] satisfy it. */
interface PendingOp {
    val opId: String
    val createdAtMs: Long
}

// Generalized from the old PENDING_SLEEP_OP_TTL_MS: a never-applied op (rejected by the primary, or
// the primary offline) stops pinning the optimistic overlay after this window.
const val PENDING_OP_TTL_MS = 60_000L

data class Reconciled<O>(
    val effectiveOps: List<O>,
    val nextTracked: List<O>,
)

/**
 * Retains an optimistic op overlay until the snapshot reflects it OR the TTL elapses — so an op the
 * primary deletes BEFORE its snapshot update arrives keeps overlaying instead of flickering out.
 *
 * Pure: call once per combined (snapshot, ops) emission, threading [Reconciled.nextTracked] back in as
 * [tracked]. Live ops are always included (an idempotent overlay if the snapshot already shows them);
 * a vanished op is carried only while still fresh and not yet reflected.
 */
fun <O : PendingOp> reconcilePendingOps(
    isReflected: (op: O) -> Boolean,
    liveOps: List<O>,
    tracked: List<O>,
    nowMs: Long,
    ttlMs: Long = PENDING_OP_TTL_MS,
): Reconciled<O> {
    val liveIds = liveOps.mapTo(mutableSetOf()) { it.opId }
    val carried = tracked.filter { op ->
        op.opId !in liveIds &&
            nowMs - op.createdAtMs <= ttlMs &&
            !isReflected(op)
    }
    val effectiveOps = liveOps + carried
    // Carry forward anything still fresh and unreflected; drop converged/expired ops so a snapshot-
    // confirmed entry can't reappear from a stale overlay on the next emission.
    val nextTracked = effectiveOps.filter { op ->
        nowMs - op.createdAtMs <= ttlMs && !isReflected(op)
    }
    return Reconciled(effectiveOps, nextTracked)
}
```

- [ ] **Step 4: Make `FeedOp` and `SleepOp` implement `PendingOp`**

Edit `FeedOp.kt` — add `override` to `opId` and `createdAtMs`, and `: PendingOp`:

```kotlin
data class FeedOp(
    override val opId: String,
    val action: FeedOpAction,
    val entryClientId: String,
    val authorUid: String,
    override val createdAtMs: Long,
    val timestampMs: Long? = null,
    val volumeMl: Int? = null,
    val type: String? = null,
    val notes: String? = null,
    val consumedBagId: Long? = null,
) : PendingOp
```

Edit `SleepOp.kt` likewise:

```kotlin
data class SleepOp(
    override val opId: String,
    val action: SleepOpAction,
    val entryClientId: String,
    val authorUid: String,
    override val createdAtMs: Long,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val sleepType: String? = null,
    val notes: String? = null,
) : PendingOp
```

- [ ] **Step 5: Add `feedOpReflected` to `MergeFeedHistory.kt`**

Append to `MergeFeedHistory.kt` (after `mergeFeedHistory`):

```kotlin
/** True once the snapshot already shows the op's effect, so the optimistic overlay can be dropped. */
fun feedOpReflected(op: FeedOp, snapshotFeeds: List<BottleFeedSnapshot>): Boolean {
    val entry = snapshotFeeds.firstOrNull { it.clientId.isNotEmpty() && it.clientId == op.entryClientId }
    return when (op.action) {
        FeedOpAction.DELETE -> entry == null
        FeedOpAction.CREATE, FeedOpAction.UPDATE ->
            entry != null &&
                entry.timestamp == op.timestampMs &&
                entry.volumeMl == op.volumeMl &&
                entry.type == op.type &&
                entry.notes == op.notes
    }
}
```

- [ ] **Step 6: Rename the TTL constant and add sleep predicates in `MergePartnerSleep.kt`**

In `MergePartnerSleep.kt`: delete the `const val PENDING_SLEEP_OP_TTL_MS = 60_000L` line (and its comment) and replace the three `ttlMs: Long = PENDING_SLEEP_OP_TTL_MS` defaults with `ttlMs: Long = PENDING_OP_TTL_MS` (same package — no import needed). Then append:

```kotlin
/**
 * Edit (UPDATE) op is reflected once the snapshot record matches the edited fields. START/STOP do not
 * affect the history overlay, so they are treated as reflected (never pinned by the history merge).
 */
fun sleepEditReflected(op: SleepOp, snapshotRecords: List<SleepSnapshot>): Boolean {
    if (op.action != SleepOpAction.UPDATE) return true
    val entry = snapshotRecords.firstOrNull { it.clientId.isNotEmpty() && it.clientId == op.entryClientId }
        ?: return false
    return (op.startTimeMs == null || entry.startTime == op.startTimeMs) &&
        entry.endTime == op.endTimeMs &&
        (op.sleepType == null || entry.sleepType == op.sleepType) &&
        entry.notes == op.notes
}

/**
 * For the active-session overlay: a START is reflected once the snapshot has that session; a STOP once
 * the snapshot session is ended (or gone); an UPDATE per [sleepEditReflected].
 */
fun sleepActiveReflected(op: SleepOp, snapshotRecords: List<SleepSnapshot>): Boolean {
    val entry = snapshotRecords.firstOrNull { it.clientId.isNotEmpty() && it.clientId == op.entryClientId }
    return when (op.action) {
        SleepOpAction.START -> entry != null
        SleepOpAction.STOP -> entry == null || entry.endTime != null
        SleepOpAction.UPDATE -> sleepEditReflected(op, snapshotRecords)
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.babytracker.sharing.domain.model.ReconcilePendingOpsTest"`
Expected: PASS.

Also run the existing sleep-merge tests, since the constant was renamed:
Run: `./gradlew test --tests "com.babytracker.sharing.domain.model.MergePartnerSleepTest" --tests "com.babytracker.sharing.domain.model.MergeFeedHistoryTest"`
Expected: PASS. If `MergePartnerSleepTest` referenced `PENDING_SLEEP_OP_TTL_MS` by name, update it to `PENDING_OP_TTL_MS`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/domain/model app/src/test/java/com/babytracker/sharing/domain/model/ReconcilePendingOpsTest.kt
git commit -m "feat(sharing): add reconcilePendingOps convergence helper and op-reflection predicates"
```

---

## Task 2: Live snapshot + partner-connection service listeners

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt`

**Interfaces:**
- Produces: `data class SnapshotEmission(val data: ShareSnapshot?, val fromCache: Boolean)`; `data class ConnectionEmission(val connected: Boolean, val fromCache: Boolean)`; `fun FirestoreSharingService.observeSnapshot(code: String): Flow<SnapshotEmission>`; `fun FirestoreSharingService.observePartnerConnected(code: String, partnerUid: String): Flow<ConnectionEmission>`.
- Consumes: `mapToSnapshot` (same package, in `FirestoreSnapshotMapping.kt`); `firestore` (internal property); `SHARES` (internal const); `PARTNERS` (made internal here).

> These wrap `addSnapshotListener`, which cannot be exercised without the Firestore emulator, so they have **no isolated unit test** — their contract is validated by `ObservePartnerDataUseCaseTest` (Task 3), which mocks them. The listener/`awaitClose` shape is a verbatim mirror of the existing `observeFeedOps`.

- [ ] **Step 1: Make `PARTNERS` internal**

In `FirestoreSharingService.kt` companion object, change:

```kotlin
        private const val PARTNERS = "partners"
```

to:

```kotlin
        internal const val PARTNERS = "partners"
```

- [ ] **Step 2: Add the emission types and listeners as extension functions**

Append to the bottom of `FirestoreSharingService.kt` (after `deleteSleepOps`):

```kotlin
/** A share-document emission with its cache origin, so callers can distinguish a server-confirmed
 *  absence (document gone) from a cache-origin one (cold offline start). */
data class SnapshotEmission(val data: ShareSnapshot?, val fromCache: Boolean)

/** A partner-connection emission with its cache origin. [connected] = the partner doc exists. */
data class ConnectionEmission(val connected: Boolean, val fromCache: Boolean)

/**
 * Streams the share document. [SnapshotEmission.data] is the mapped snapshot when the `data` field is
 * present, else null (document missing / no data). Mirrors [FirestoreSharingService.observeFeedOps]:
 * close on listener error, remove the registration on cancellation.
 */
fun FirestoreSharingService.observeSnapshot(code: String): Flow<SnapshotEmission> = callbackFlow {
    val registration = firestore.collection(FirestoreSharingService.SHARES).document(code)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val data = snapshot.get("data") as? Map<*, *>
                trySend(
                    SnapshotEmission(
                        data = data?.let { mapToSnapshot(it) },
                        fromCache = snapshot.metadata.isFromCache,
                    ),
                )
            }
        }
    awaitClose { registration.remove() }
}

/** The live equivalent of [FirestoreSharingService.isPartnerConnected]: streams whether the partner's
 *  own `partners/{partnerUid}` document exists (readable per rules: request.auth.uid == partnerUid). */
fun FirestoreSharingService.observePartnerConnected(
    code: String,
    partnerUid: String,
): Flow<ConnectionEmission> = callbackFlow {
    val registration = firestore.collection(FirestoreSharingService.SHARES).document(code)
        .collection(FirestoreSharingService.PARTNERS).document(partnerUid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(ConnectionEmission(connected = snapshot.exists(), fromCache = snapshot.metadata.isFromCache))
            }
        }
    awaitClose { registration.remove() }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt
git commit -m "feat(sharing): add observeSnapshot and observePartnerConnected live listeners"
```

---

## Task 3: ObservePartnerDataUseCase

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/usecase/ObservePartnerDataUseCase.kt`
- Test: `app/src/test/java/com/babytracker/sharing/usecase/ObservePartnerDataUseCaseTest.kt`

**Interfaces:**
- Consumes: `FirestoreSharingService.observeSnapshot`, `.observePartnerConnected`, `.signInAnonymously`, `SnapshotEmission`, `ConnectionEmission`, `SettingsRepository.getShareCode`/`.clearPartnerStateIfShareCodeMatches`, `DebugPartnerSnapshotBuilder`, `PartnerAccessRevokedException`, `PartnerDataFetchException`.
- Produces: `operator fun invoke(): Flow<ShareSnapshot>` and `operator fun invoke(code: ShareCode): Flow<ShareSnapshot>` (both non-suspend, cold).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/babytracker/sharing/usecase/ObservePartnerDataUseCaseTest.kt`:

```kotlin
package com.babytracker.sharing.usecase

import app.cash.turbine.test
import com.babytracker.debug.DebugPartnerSnapshotBuilder
import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.ConnectionEmission
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.SnapshotEmission
import com.babytracker.sharing.data.firebase.observePartnerConnected
import com.babytracker.sharing.data.firebase.observeSnapshot
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ObservePartnerDataUseCaseTest {
    private val service: FirestoreSharingService = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val debugBuilder: DebugPartnerSnapshotBuilder = mockk()
    private lateinit var useCase: ObservePartnerDataUseCase

    private val snapshot = ShareSnapshot(
        lastSyncAt = java.time.Instant.EPOCH,
        baby = BabySnapshot("Baby", 0L, emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
    )

    @BeforeEach
    fun setUp() {
        mockkStatic("com.babytracker.sharing.data.firebase.FirestoreSharingServiceKt")
        useCase = ObservePartnerDataUseCase(service, settingsRepository, Lazy { debugBuilder })
        every { settingsRepository.getShareCode() } returns flowOf("CODE1234")
        coEvery { service.signInAnonymously() } returns "uid"
        coEvery { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") } returns true
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `emits the snapshot when present and connected from cache`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns flowOf(SnapshotEmission(snapshot, fromCache = true))
        every { service.observePartnerConnected("CODE1234", "uid") } returns flowOf(ConnectionEmission(true, fromCache = true))

        assertEquals(snapshot, useCase().first()) // cached data must display offline-first
    }

    @Test
    fun `server-confirmed disconnect clears state and throws revoked`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns flowOf(SnapshotEmission(snapshot, fromCache = false))
        every { service.observePartnerConnected("CODE1234", "uid") } returns flowOf(ConnectionEmission(false, fromCache = false))

        assertThrows<PartnerAccessRevokedException> { useCase().first() }
        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") }
    }

    @Test
    fun `server-confirmed missing snapshot clears state and throws revoked`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns flowOf(SnapshotEmission(null, fromCache = false))
        every { service.observePartnerConnected("CODE1234", "uid") } returns flowOf(ConnectionEmission(true, fromCache = false))

        assertThrows<PartnerAccessRevokedException> { useCase().first() }
        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") }
    }

    @Test
    fun `cache-origin missing snapshot does NOT clear or throw and later server snapshot is emitted`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns
            flowOf(SnapshotEmission(null, fromCache = true), SnapshotEmission(snapshot, fromCache = false))
        every { service.observePartnerConnected("CODE1234", "uid") } returns flowOf(ConnectionEmission(true, fromCache = false))

        useCase().test {
            assertEquals(snapshot, awaitItem())
            awaitComplete()
        }
        coVerify(exactly = 0) { settingsRepository.clearPartnerStateIfShareCodeMatches(any()) }
    }

    @Test
    fun `cache-origin disconnect is ignored until the server confirms connection`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns flowOf(SnapshotEmission(snapshot, fromCache = false))
        every { service.observePartnerConnected("CODE1234", "uid") } returns
            flowOf(ConnectionEmission(false, fromCache = true), ConnectionEmission(true, fromCache = false))

        useCase().test {
            assertEquals(snapshot, awaitItem())
            awaitComplete()
        }
        coVerify(exactly = 0) { settingsRepository.clearPartnerStateIfShareCodeMatches(any()) }
    }

    @Test
    fun `debug placeholder code serves the seeded snapshot without signing in`() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(DebugSeedConfig.PARTNER_SHARE_CODE)
        every { debugBuilder.build() } returns snapshot

        assertEquals(snapshot, useCase().first())
        coVerify(exactly = 0) { service.signInAnonymously() }
    }

    @Test
    fun `firebase listener error is wrapped as PartnerDataFetchException`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns flow {
            throw FirebaseFirestoreException("boom", FirebaseFirestoreException.Code.UNAVAILABLE)
        }
        every { service.observePartnerConnected("CODE1234", "uid") } returns flowOf(ConnectionEmission(true, fromCache = false))

        assertThrows<PartnerDataFetchException> { useCase().first() }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.sharing.usecase.ObservePartnerDataUseCaseTest"`
Expected: FAIL — `ObservePartnerDataUseCase` unresolved.

- [ ] **Step 3: Create the use case**

Create `app/src/main/java/com/babytracker/sharing/usecase/ObservePartnerDataUseCase.kt`:

```kotlin
package com.babytracker.sharing.usecase

import com.babytracker.BuildConfig
import com.babytracker.debug.DebugPartnerSnapshotBuilder
import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.ConnectionEmission
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.SnapshotEmission
import com.babytracker.sharing.data.firebase.observePartnerConnected
import com.babytracker.sharing.data.firebase.observeSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

/**
 * The live equivalent of [FetchPartnerDataUseCase]: streams the share snapshot via Firestore listeners
 * so the primary's changes appear within ~1s. A second cheap listener on the partner's own doc detects
 * a server-confirmed disconnect. Local partner state is cleared ONLY on a server-confirmed loss of
 * access — a cache-origin absence (cold offline start) is held as [Access.Pending] until the server
 * corrects it, so a missing cached snapshot never erases a valid partner's data.
 */
class ObservePartnerDataUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
    // Lazy: only resolved on the debug placeholder-code path, never in release observation.
    private val debugSnapshotBuilder: Lazy<DebugPartnerSnapshotBuilder>,
) {
    /** Observes the snapshot for the currently stored share code (used by the dashboard). */
    operator fun invoke(): Flow<ShareSnapshot> = flow {
        val codeValue = settingsRepository.getShareCode().first() ?: error("No share code")
        emitAll(invoke(ShareCode(codeValue)))
    }

    operator fun invoke(code: ShareCode): Flow<ShareSnapshot> {
        // Debug offline partner mode: serve a snapshot of the locally-seeded data instead of Firebase.
        if (BuildConfig.DEBUG && code.value == DebugSeedConfig.PARTNER_SHARE_CODE) {
            return flowOf(debugSnapshotBuilder.get().build())
        }
        return flow {
            // signInAnonymously() lives inside the flow so an auth/network failure routes through
            // .catch instead of crashing the collector.
            val uid = service.signInAnonymously()
            emitAll(
                combine(
                    service.observeSnapshot(code.value),
                    service.observePartnerConnected(code.value, uid),
                ) { snap, conn -> resolveAccess(snap, conn) }
                    .transform { access ->
                        when (access) {
                            is Access.Data -> emit(access.snapshot)
                            Access.Pending -> Unit // cache-origin absence: emit nothing, await the server
                            Access.Disconnected -> {
                                settingsRepository.clearPartnerStateIfShareCodeMatches(code.value)
                                throw PartnerAccessRevokedException("Partner disconnected")
                            }
                        }
                    },
            )
        }.catch { error ->
            // Transient drops do not error the flow (the SDK serves cache + auto-reconnects), so a thrown
            // error is genuinely terminal. Re-throw revoke; wrap everything else for the VM's error branch.
            if (error is PartnerAccessRevokedException) throw error
            throw PartnerDataFetchException("Could not load partner data", error)
        }
    }

    private sealed interface Access {
        data class Data(val snapshot: ShareSnapshot) : Access
        data object Disconnected : Access
        data object Pending : Access
    }

    private fun resolveAccess(snap: SnapshotEmission, conn: ConnectionEmission): Access = when {
        // Present data + connected — show it (cached data is fine, offline-first).
        snap.data != null && conn.connected -> Access.Data(snap.data)
        // Server-CONFIRMED absence or disconnect — the only state that clears DataStore.
        (snap.data == null && !snap.fromCache) || (!conn.connected && !conn.fromCache) -> Access.Disconnected
        // Cache-origin absence/disconnect (offline start, cold cache): wait for the server emission.
        else -> Access.Pending
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.sharing.usecase.ObservePartnerDataUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/usecase/ObservePartnerDataUseCase.kt app/src/test/java/com/babytracker/sharing/usecase/ObservePartnerDataUseCaseTest.kt
git commit -m "feat(sharing): add ObservePartnerDataUseCase live snapshot read path"
```

---

## Task 4: Feed history — live stream + reconciliation, and the shared collector

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/ObservePartnerFeedHistoryUseCase.kt`
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerHistoryRefresh.kt`
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerFeedHistoryViewModel.kt`
- Test: `app/src/test/java/com/babytracker/sharing/usecase/ObservePartnerFeedHistoryUseCaseTest.kt`
- Test: `app/src/test/java/com/babytracker/ui/partner/PartnerFeedHistoryViewModelTest.kt`

**Interfaces:**
- Consumes: `ObservePartnerDataUseCase` (Task 3), `reconcilePendingOps`/`feedOpReflected` (Task 1).
- Produces: `suspend operator fun ObservePartnerFeedHistoryUseCase.invoke(snapshotFeeds: Flow<List<BottleFeedSnapshot>>): Flow<MergedFeedHistory>`; `suspend fun <T> Flow<T>.collectPartnerHistory(widgetUpdater, onItem, onAccessRevoked, onLoadFailed)`.

> The old `refreshPartnerHistory` and `hasConsumedPendingOps` stay until Task 5 (the sleep VM still uses them). This task adds the new `collectPartnerHistory` alongside them.

- [ ] **Step 1: Rewrite the feed-history use case test (failing)**

Replace `app/src/test/java/com/babytracker/sharing/usecase/ObservePartnerFeedHistoryUseCaseTest.kt`:

```kotlin
package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.time.Instant

class ObservePartnerFeedHistoryUseCaseTest {
    private val service: FirestoreSharingService = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val now = Instant.ofEpochMilli(100_000)
    private lateinit var useCase: ObservePartnerFeedHistoryUseCase

    private fun feed(clientId: String) = BottleFeedSnapshot(
        timestamp = 5_000L, volumeMl = 90, type = "FORMULA", clientId = clientId, author = "PARTNER",
    )

    private fun createOp(clientId: String) = FeedOp(
        opId = "op-$clientId", action = FeedOpAction.CREATE, entryClientId = clientId, authorUid = "uid",
        createdAtMs = now.toEpochMilli(), timestampMs = 5_000L, volumeMl = 90, type = "FORMULA",
    )

    @BeforeEach
    fun setUp() {
        useCase = ObservePartnerFeedHistoryUseCase(service, settingsRepository) { now }
        every { settingsRepository.getShareCode() } returns flowOf("CODE1234")
        coEvery { service.signInAnonymously() } returns "uid"
    }

    @Test
    fun `overlays the partner's own pending feed op on the snapshot`() = runTest {
        every { service.observeFeedOps("CODE1234", "uid") } returns flowOf(listOf(createOp("c1")))

        val merged = useCase(flowOf(emptyList())).first()

        assertEquals(listOf("c1"), merged.entries.map { it.clientId })
    }

    @Test
    fun `op-delete delivered before the snapshot update keeps the entry, then converges`() = runTest {
        // Op present, then deleted (empty), while the snapshot is stale until the last emission.
        every { service.observeFeedOps("CODE1234", "uid") } returns flowOf(listOf(createOp("c1")), emptyList(), emptyList())
        val snapshotFeeds = flowOf(emptyList(), emptyList(), listOf(feed("c1")))

        var lastEntries: List<String> = emptyList()
        useCase(snapshotFeeds).collectIndexed { _, merged -> lastEntries = merged.entries.map { it.clientId } }

        // After op-delete + stale snapshot the entry is retained; the final fresh snapshot converges to it.
        assertEquals(listOf("c1"), lastEntries)
    }

    @Test
    fun `permission denied clears partner state and throws revoked`() = runTest {
        val denied = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        every { service.observeFeedOps("CODE1234", "uid") } returns flow { throw denied }
        coEvery { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") } returns true

        assertThrows<PartnerAccessRevokedException> {
            runBlocking { useCase(flowOf(emptyList())).first() }
        }
        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") }
    }

    @Test
    fun `non-revoked failure wraps as PartnerDataFetchException`() = runTest {
        every { service.observeFeedOps("CODE1234", "uid") } returns flow { throw IOException("network down") }

        assertThrows<PartnerDataFetchException> {
            runBlocking { useCase(flowOf(emptyList())).first() }
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCaseTest"`
Expected: FAIL — `invoke` no longer accepts a `Flow`, `now` constructor param missing.

- [ ] **Step 3: Adapt the feed-history use case**

Replace the body of `ObservePartnerFeedHistoryUseCase.kt`:

```kotlin
package com.babytracker.sharing.usecase

import com.babytracker.BuildConfig
import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.MergedFeedHistory
import com.babytracker.sharing.domain.model.Reconciled
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.feedOpReflected
import com.babytracker.sharing.domain.model.mergeFeedHistory
import com.babytracker.sharing.domain.model.reconcilePendingOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import java.time.Instant
import javax.inject.Inject

/**
 * The partner's feed history over the LIVE snapshot stream: each (snapshot, own-ops) emission is run
 * through [reconcilePendingOps] (so an op deleted before its snapshot update keeps overlaying until the
 * snapshot converges or the TTL fires) and then the existing [mergeFeedHistory].
 */
class ObservePartnerFeedHistoryUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(snapshotFeeds: Flow<List<BottleFeedSnapshot>>): Flow<MergedFeedHistory> {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        // Debug offline partner mode: merge the seeded feeds with no pending ops.
        if (BuildConfig.DEBUG && code.value == DebugSeedConfig.PARTNER_SHARE_CODE) {
            return snapshotFeeds.map { feeds -> mergeFeedHistory(feeds, emptyList()) }
        }
        return flow {
            val uid = service.signInAnonymously()
            emitAll(
                combine(snapshotFeeds, service.observeFeedOps(code.value, authorUid = uid)) { feeds, ops ->
                    feeds to ops
                }.scan(ReconcileState()) { state, (feeds, liveOps) ->
                    val reconciled = reconcilePendingOps(
                        isReflected = { feedOpReflected(it, feeds) },
                        liveOps = liveOps,
                        tracked = state.reconciled.nextTracked,
                        nowMs = now().toEpochMilli(),
                    )
                    ReconcileState(feeds, reconciled)
                }.drop(1).map { state ->
                    mergeFeedHistory(state.feeds, state.reconciled.effectiveOps)
                },
            )
        }.catch { error ->
            val revoked = error.toPartnerAccessRevokedExceptionAfterClearing(settingsRepository, code)
            if (revoked != null) throw revoked
            throw PartnerDataFetchException("Could not load feed history", error)
        }
    }

    private data class ReconcileState(
        val feeds: List<BottleFeedSnapshot> = emptyList(),
        val reconciled: Reconciled<FeedOp> = Reconciled(emptyList(), emptyList()),
    )
}
```

- [ ] **Step 4: Add `collectPartnerHistory` to `PartnerHistoryRefresh.kt`**

Append to `PartnerHistoryRefresh.kt` (keep `refreshPartnerHistory` and `hasConsumedPendingOps` for now), and add `import kotlinx.coroutines.flow.catch`:

```kotlin
/**
 * Collects a partner-history flow with the shared 3-branch error handling: revoke -> onAccessRevoked +
 * widget poke; any other terminal error -> onLoadFailed. Cancellation propagates untouched. The flow
 * itself differs per view model (feed adds milk bags, sleep does not), so the per-item write is a lambda.
 */
internal suspend fun <T> Flow<T>.collectPartnerHistory(
    widgetUpdater: WidgetUpdater,
    onItem: (T) -> Unit,
    onAccessRevoked: () -> Unit,
    onLoadFailed: () -> Unit,
) {
    catch { error ->
        when (error) {
            is CancellationException -> throw error
            is PartnerAccessRevokedException -> {
                onAccessRevoked()
                widgetUpdater.updateAll()
            }
            else -> onLoadFailed() // PartnerDataFetchException, IllegalStateException, etc.
        }
    }.collect { onItem(it) }
}
```

- [ ] **Step 5: Rewrite the feed-history view model test (failing)**

Replace `PartnerFeedHistoryViewModelTest.kt`:

```kotlin
package com.babytracker.ui.partner

import android.content.Context
import com.babytracker.R
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.MergedFeedHistory
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.DeletePartnerFeedUseCase
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.PartnerDataFetchException
import com.babytracker.widget.WidgetUpdater
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerFeedHistoryViewModelTest {

    private val observePartnerData = mockk<ObservePartnerDataUseCase>()
    private val observePartnerFeedHistory = mockk<ObservePartnerFeedHistoryUseCase>()
    private val deletePartnerFeed = mockk<DeletePartnerFeedUseCase>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val widgetUpdater = mockk<WidgetUpdater>()
    private val appContext = mockk<Context>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
        every { observePartnerData() } returns flowOf(snapshot())
        coJustRun { deletePartnerFeed(any()) }
        coJustRun { widgetUpdater.updateAll() }
        every { appContext.getString(R.string.error_partner_load_failed) } returns "load-failed"
        every { appContext.getString(R.string.error_partner_delete_failed) } returns "delete-failed"
        every { appContext.getString(R.string.error_partner_access_revoked) } returns "access-revoked"
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `merged entries land in state`() = runTest {
        val entry = feed(clientId = "entry-1", author = FeedAuthor.PARTNER.name)
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(entries = listOf(entry)))

        val viewModel = viewModel()

        assertEquals(listOf(entry), viewModel.uiState.value.entries)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `milk bags are sourced from the live snapshot`() = runTest {
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(emptyList()))
        every { observePartnerData() } returns flowOf(snapshot().copy(milkBags = listOf(MilkBagSnapshot(1L, 0L, 120, null))))

        val viewModel = viewModel()

        assertEquals(1, viewModel.uiState.value.milkBags.size)
    }

    @Test
    fun `isEditable is true only for partner entries with clientId`() = runTest {
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(emptyList()))
        val viewModel = viewModel()

        assertTrue(viewModel.isEditable(feed(clientId = "entry-1", author = FeedAuthor.PARTNER.name)))
        assertFalse(viewModel.isEditable(feed(clientId = "entry-1", author = FeedAuthor.OWNER.name)))
        assertFalse(viewModel.isEditable(feed(clientId = "", author = FeedAuthor.PARTNER.name)))
    }

    @Test
    fun `listener access revoked exception sets accessRevoked`() = runTest {
        coEvery { observePartnerFeedHistory(any()) } returns flow { throw PartnerAccessRevokedException("revoked") }

        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.accessRevoked)
        coVerify { widgetUpdater.updateAll() }
    }

    @Test
    fun `listener feed history exception clears loading and shows error`() = runTest {
        coEvery { observePartnerFeedHistory(any()) } returns flow { throw PartnerDataFetchException("Could not load feed history") }

        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.accessRevoked)
        assertEquals("load-failed", viewModel.uiState.value.error)
    }

    @Test
    fun `delete delegates to DeletePartnerFeedUseCase`() = runTest {
        val entry = feed(clientId = "entry-1", author = FeedAuthor.PARTNER.name)
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(emptyList()))
        val viewModel = viewModel()

        viewModel.onDelete(entry)

        coVerify { deletePartnerFeed(entry) }
    }

    @Test
    fun `delete revoked failure sets accessRevoked and updates widgets`() = runTest {
        val entry = feed(clientId = "entry-1", author = FeedAuthor.PARTNER.name)
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(emptyList()))
        coEvery { deletePartnerFeed(entry) } throws PartnerAccessRevokedException("Partner access revoked")
        val viewModel = viewModel()

        viewModel.onDelete(entry)

        assertTrue(viewModel.uiState.value.accessRevoked)
        coVerify { widgetUpdater.updateAll() }
    }

    private fun viewModel() = PartnerFeedHistoryViewModel(
        observePartnerData = observePartnerData,
        observePartnerFeedHistory = observePartnerFeedHistory,
        deletePartnerFeed = deletePartnerFeed,
        widgetUpdater = widgetUpdater,
        settingsRepository = settingsRepository,
        appContext = appContext,
    )

    private fun snapshot() = ShareSnapshot(
        lastSyncAt = Instant.ofEpochMilli(1_000),
        baby = BabySnapshot(name = "Baby", birthDateMs = 0L, allergies = emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
    )

    private fun feed(clientId: String, author: String) = BottleFeedSnapshot(
        timestamp = 2_000L, volumeMl = 90, type = FeedType.FORMULA.name, clientId = clientId, author = author, notes = null,
    )
}
```

- [ ] **Step 6: Rewrite the feed-history view model**

Replace `PartnerFeedHistoryViewModel.kt`:

```kotlin
package com.babytracker.ui.partner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.usecase.DeletePartnerFeedUseCase
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.PartnerDataFetchException
import com.babytracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerFeedHistoryUiState(
    val entries: List<BottleFeedSnapshot> = emptyList(),
    val milkBags: List<MilkBagSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val accessRevoked: Boolean = false,
    val error: String? = null,
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
)

@HiltViewModel
class PartnerFeedHistoryViewModel @Inject constructor(
    private val observePartnerData: ObservePartnerDataUseCase,
    private val observePartnerFeedHistory: ObservePartnerFeedHistoryUseCase,
    private val deletePartnerFeed: DeletePartnerFeedUseCase,
    private val widgetUpdater: WidgetUpdater,
    settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerFeedHistoryUiState())
    val uiState: StateFlow<PartnerFeedHistoryUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            settingsRepository.getVolumeUnit().collect { unit ->
                _uiState.update { it.copy(volumeUnit = unit) }
            }
        }
    }

    /** Restarts the live collection (used by the screen's retry button). */
    fun refresh() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, accessRevoked = false, error = null) }
            // ponytail: `snapshots` is subscribed twice (here + inside the feed-history use case), so the
            // dashboard read costs two listeners on one doc. Fine for a single screen; share if it matters.
            val snapshots = observePartnerData()
            combine(
                snapshots,
                observePartnerFeedHistory(snapshots.map { it.bottleFeeds }),
            ) { snapshot, history -> snapshot.milkBags to history.entries }
                .collectPartnerHistory(
                    widgetUpdater = widgetUpdater,
                    onItem = { (milkBags, entries) ->
                        _uiState.update { it.copy(milkBags = milkBags, entries = entries, isLoading = false) }
                    },
                    onAccessRevoked = {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                accessRevoked = true,
                                error = appContext.getString(R.string.error_partner_access_revoked),
                            )
                        }
                    },
                    onLoadFailed = {
                        _uiState.update {
                            it.copy(isLoading = false, error = appContext.getString(R.string.error_partner_load_failed))
                        }
                    },
                )
        }
    }

    fun onDelete(entry: BottleFeedSnapshot) {
        if (!isEditable(entry)) return
        viewModelScope.launch {
            try {
                deletePartnerFeed(entry)
            } catch (_: PartnerAccessRevokedException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        accessRevoked = true,
                        error = appContext.getString(R.string.error_partner_access_revoked),
                    )
                }
                widgetUpdater.updateAll()
            } catch (_: PartnerDataFetchException) {
                _uiState.update { it.copy(error = appContext.getString(R.string.error_partner_delete_failed)) }
            } catch (_: IllegalStateException) {
                _uiState.update { it.copy(error = appContext.getString(R.string.error_partner_delete_failed)) }
            }
        }
    }

    fun isEditable(entry: BottleFeedSnapshot): Boolean =
        entry.author == FeedAuthor.PARTNER.name && entry.clientId.isNotEmpty()
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCaseTest" --tests "com.babytracker.ui.partner.PartnerFeedHistoryViewModelTest"`
Expected: PASS. (`PartnerFeedHistoryScreen` still calls `viewModel.refresh()` / `viewModel::refresh`, which both still exist — no screen change needed.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/usecase/ObservePartnerFeedHistoryUseCase.kt app/src/main/java/com/babytracker/ui/partner/PartnerHistoryRefresh.kt app/src/main/java/com/babytracker/ui/partner/PartnerFeedHistoryViewModel.kt app/src/test/java/com/babytracker/sharing/usecase/ObservePartnerFeedHistoryUseCaseTest.kt app/src/test/java/com/babytracker/ui/partner/PartnerFeedHistoryViewModelTest.kt
git commit -m "refactor(partner): feed history reconciles over the live snapshot stream"
```

---

## Task 5: Sleep history — live stream + reconciliation; delete the old refetch helper

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/ObservePartnerSleepHistoryUseCase.kt`
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerSleepHistoryViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerHistoryRefresh.kt` (delete `refreshPartnerHistory` + `hasConsumedPendingOps`)
- Test: `app/src/test/java/com/babytracker/sharing/usecase/ObservePartnerSleepHistoryUseCaseTest.kt`
- Test: `app/src/test/java/com/babytracker/ui/partner/PartnerSleepHistoryViewModelTest.kt`

**Interfaces:**
- Produces: `suspend operator fun ObservePartnerSleepHistoryUseCase.invoke(snapshotRecords: Flow<List<SleepSnapshot>>): Flow<MergedSleepHistory>`.

- [ ] **Step 1: Update the sleep-history use case test (failing)**

In `ObservePartnerSleepHistoryUseCaseTest.kt`, change the two `useCase(listOf(record))` calls to `useCase(flowOf(listOf(record)))`. The assertions are unchanged.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.babytracker.sharing.usecase.ObservePartnerSleepHistoryUseCaseTest"`
Expected: FAIL to compile — the test now passes a `Flow` to an `invoke(List)`.

- [ ] **Step 3: Adapt the sleep-history use case**

Replace `ObservePartnerSleepHistoryUseCase.kt`:

```kotlin
package com.babytracker.sharing.usecase

import com.babytracker.BuildConfig
import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.observeSleepOps
import com.babytracker.sharing.domain.model.MergedSleepHistory
import com.babytracker.sharing.domain.model.Reconciled
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.domain.model.mergeSleepHistory
import com.babytracker.sharing.domain.model.reconcilePendingOps
import com.babytracker.sharing.domain.model.sleepEditReflected
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import java.time.Instant
import javax.inject.Inject

/**
 * Mirrors [ObservePartnerFeedHistoryUseCase] for sleep over the LIVE snapshot stream: reconciles the
 * partner's own pending edit ops (retaining an op deleted before its snapshot update) before the
 * existing [mergeSleepHistory], which overlays edit (UPDATE) ops only.
 */
class ObservePartnerSleepHistoryUseCase @Inject constructor(
    private val service: FirestoreSharingService,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(snapshotRecords: Flow<List<SleepSnapshot>>): Flow<MergedSleepHistory> {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        if (BuildConfig.DEBUG && code.value == DebugSeedConfig.PARTNER_SHARE_CODE) {
            return snapshotRecords.map { records -> mergeSleepHistory(records, emptyList(), now().toEpochMilli()) }
        }
        return flow {
            val uid = service.signInAnonymously()
            emitAll(
                combine(snapshotRecords, service.observeSleepOps(code.value, authorUid = uid)) { records, ops ->
                    records to ops
                }.scan(ReconcileState()) { state, (records, liveOps) ->
                    val reconciled = reconcilePendingOps(
                        isReflected = { sleepEditReflected(it, records) },
                        liveOps = liveOps,
                        tracked = state.reconciled.nextTracked,
                        nowMs = now().toEpochMilli(),
                    )
                    ReconcileState(records, reconciled)
                }.drop(1).map { state ->
                    mergeSleepHistory(state.records, state.reconciled.effectiveOps, now().toEpochMilli())
                },
            )
        }.catch { error ->
            val revoked = error.toPartnerAccessRevokedExceptionAfterClearing(settingsRepository, code)
            if (revoked != null) throw revoked
            throw PartnerDataFetchException("Could not load sleep history", error)
        }
    }

    private data class ReconcileState(
        val records: List<SleepSnapshot> = emptyList(),
        val reconciled: Reconciled<SleepOp> = Reconciled(emptyList(), emptyList()),
    )
}
```

- [ ] **Step 4: Update the sleep-history view model test (failing)**

In `PartnerSleepHistoryViewModelTest.kt`: replace `FetchPartnerDataUseCase` with `ObservePartnerDataUseCase` (field + import). In `setUp`, replace the `coEvery { fetchPartnerData() } returns ShareSnapshot(...)` block with:

```kotlin
        every { observePartnerData() } returns flowOf(
            ShareSnapshot(
                lastSyncAt = Instant.EPOCH,
                baby = BabySnapshot("Mia", 0L, emptyList()),
                sessions = emptyList(),
                sleepRecords = emptyList(),
            ),
        )
```

and update both constructor calls to `PartnerSleepHistoryViewModel(observePartnerData, observeHistory, widgetUpdater, appContext)`.

- [ ] **Step 5: Rewrite the sleep-history view model**

Replace `PartnerSleepHistoryViewModel.kt`:

```kotlin
package com.babytracker.ui.partner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerSleepHistoryUseCase
import com.babytracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerSleepHistoryUiState(
    val entries: List<SleepSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val accessRevoked: Boolean = false,
    val error: String? = null,
)

/** Mirrors [PartnerFeedHistoryViewModel] for sleep (read + edit-own; no delete in this domain). */
@HiltViewModel
class PartnerSleepHistoryViewModel @Inject constructor(
    private val observePartnerData: ObservePartnerDataUseCase,
    private val observePartnerSleepHistory: ObservePartnerSleepHistoryUseCase,
    private val widgetUpdater: WidgetUpdater,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerSleepHistoryUiState())
    val uiState: StateFlow<PartnerSleepHistoryUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    init {
        refresh()
    }

    /** Restarts the live collection (used by the screen's retry button). */
    fun refresh() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, accessRevoked = false, error = null) }
            observePartnerSleepHistory(observePartnerData().map { it.sleepRecords })
                .map { it.entries }
                .collectPartnerHistory(
                    widgetUpdater = widgetUpdater,
                    onItem = { entries -> _uiState.update { it.copy(entries = entries, isLoading = false) } },
                    onAccessRevoked = {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                accessRevoked = true,
                                error = appContext.getString(R.string.error_partner_access_revoked),
                            )
                        }
                    },
                    onLoadFailed = {
                        _uiState.update {
                            it.copy(isLoading = false, error = appContext.getString(R.string.error_partner_load_failed))
                        }
                    },
                )
        }
    }

    fun isEditable(entry: SleepSnapshot): Boolean =
        entry.startedBy == SleepAuthor.PARTNER.name && entry.clientId.isNotEmpty()
}
```

- [ ] **Step 6: Delete the dead refetch helper**

In `PartnerHistoryRefresh.kt`, delete `hasConsumedPendingOps` and `refreshPartnerHistory` and their now-unused imports (`ShareSnapshot`, `FetchPartnerDataUseCase`, `PartnerDataFetchException`, `Flow`). Keep only `collectPartnerHistory` and its imports.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.babytracker.sharing.usecase.ObservePartnerSleepHistoryUseCaseTest" --tests "com.babytracker.ui.partner.PartnerSleepHistoryViewModelTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/usecase/ObservePartnerSleepHistoryUseCase.kt app/src/main/java/com/babytracker/ui/partner/PartnerSleepHistoryViewModel.kt app/src/main/java/com/babytracker/ui/partner/PartnerHistoryRefresh.kt app/src/test/java/com/babytracker/sharing/usecase/ObservePartnerSleepHistoryUseCaseTest.kt app/src/test/java/com/babytracker/ui/partner/PartnerSleepHistoryViewModelTest.kt
git commit -m "refactor(partner): sleep history reconciles over the live snapshot stream; drop refetch helper"
```

---

## Task 6: PartnerSleepViewModel — replace `snapshotRefreshTick` with A.1 reconciliation

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerSleepViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt` (remove the one `snapshotRefreshTick` effect)
- Test: `app/src/test/java/com/babytracker/ui/partner/PartnerSleepViewModelTest.kt`

**Interfaces:**
- Consumes: `reconcilePendingOps`/`sleepActiveReflected` (Task 1).
- Produces: `PartnerSleepUiState` WITHOUT `snapshotRefreshTick`; `active`/`stopping`/`canEditActive` now reconcile a consumed START/STOP/edit overlay until the snapshot reflects it.

> The active overlay no longer drives a dashboard refetch (the dashboard is live). When an op disappears from the listener before the snapshot catches up, its overlay is RETAINED via `reconcilePendingOps` until the snapshot reflects it or the TTL fires — instead of the old "applied -> bump tick -> dashboard refetch" path.

- [ ] **Step 1: Update the sleep view model test (failing)**

In `PartnerSleepViewModelTest.kt`, **remove** the two tests that assert `snapshotRefreshTick` (`snapshotRefreshTick bumps when a submitted op is applied by the primary` and the `assertEquals(1, vm.uiState.value.snapshotRefreshTick)` line inside `op listener resubscribes after a transient error`). Replace the resubscribe test's tail assertion and add a retention test. Concretely:

Change `op listener resubscribes after a transient error` to assert on `active`/resubscription instead of the tick:

```kotlin
    @Test
    fun `op listener resubscribes after a transient error`() = runTest {
        val startOp = SleepOp(
            opId = "op-1", action = SleepOpAction.START, entryClientId = "active-cid",
            authorUid = "uid", createdAtMs = now.toEpochMilli(), startTimeMs = now.toEpochMilli(), sleepType = "NAP",
        )
        var attempts = 0
        every { settingsRepository.getShareCode() } returns flowOf("CODE")
        coEvery { service.signInAnonymously() } returns "uid"
        // First subscription blows up; retryWhen must re-subscribe and pick up the op.
        every { service.observeSleepOps("CODE", "uid") } returns flow {
            attempts++
            if (attempts == 1) throw IOException("listener boom")
            emit(listOf(startOp))
        }

        val vm = buildViewModel()
        vm.onSleepRecordsAvailable(emptyList())
        advanceUntilIdle()

        assertTrue(attempts >= 2)
        assertNotNull(vm.uiState.value.active) // the optimistic START surfaced after re-subscribe
    }

    @Test
    fun `a consumed START overlay is retained until the snapshot shows the session`() = runTest {
        val startOp = SleepOp(
            opId = "op-1", action = SleepOpAction.START, entryClientId = "p-cid",
            authorUid = "uid", createdAtMs = now.toEpochMilli(), startTimeMs = now.toEpochMilli(), sleepType = "NAP",
        )
        every { settingsRepository.getShareCode() } returns flowOf("CODE")
        coEvery { service.signInAnonymously() } returns "uid"
        // START op present, then consumed (empty) — BEFORE the snapshot has the session.
        every { service.observeSleepOps("CODE", "uid") } returns flowOf(listOf(startOp), emptyList())

        val vm = buildViewModel()
        vm.onSleepRecordsAvailable(emptyList()) // snapshot still has no active session
        advanceUntilIdle()

        // The just-started session is retained optimistically instead of vanishing on op-consume.
        assertNotNull(vm.uiState.value.active)
        assertEquals("p-cid", vm.uiState.value.active?.clientId)
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.babytracker.ui.partner.PartnerSleepViewModelTest"`
Expected: FAIL to compile — `snapshotRefreshTick` removed in Step 3 (right now the removed tests are gone but the new retention test fails because the overlay still vanishes on op-consume under the current `trackedOpIds` logic).

- [ ] **Step 3: Rewrite the observe loop and state**

In `PartnerSleepViewModel.kt`:

(a) Remove `snapshotRefreshTick` from `PartnerSleepUiState` (delete the field + its comment).

(b) Replace the three tracking fields and the `init` collect body. Replace:

```kotlin
    private var snapshotRecords: List<SleepSnapshot> = emptyList()
    private var pendingOps: List<SleepOp> = emptyList()
    private var trackedOpIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            val codeValue = settingsRepository.getShareCode().first() ?: return@launch
            runCatching {
                val uid = service.signInAnonymously()
                service.observeSleepOps(codeValue, authorUid = uid)
                    .retryWhen { cause, attempt ->
                        // A transient Firestore listener error must not kill the stream for good:
                        // op tracking (and the dashboard refetch it drives) would freeze. Re-subscribe
                        // with capped backoff; trackedOpIds survives so applied-op detection still works.
                        Log.w(TAG, "own sleep op listener error (attempt $attempt); retrying", cause)
                        delay(min(RETRY_BASE_MS * (attempt + 1), RETRY_MAX_MS))
                        true
                    }
                    .collect { ops ->
                    val ids = ops.mapTo(mutableSetOf()) { it.opId }
                    // A previously-pending op vanished -> the primary applied (or dropped) it and has
                    // already re-pushed the snapshot (it pushes before deleting), so the stale
                    // dashboard snapshot must be refetched to reflect the change.
                    val applied = trackedOpIds.any { it !in ids }
                    trackedOpIds = ids
                    pendingOps = ops
                    if (applied) _uiState.update { it.copy(snapshotRefreshTick = it.snapshotRefreshTick + 1) }
                    recomputeActive()
                }
            }
        }
    }
```

with:

```kotlin
    private var snapshotRecords: List<SleepSnapshot> = emptyList()
    private var liveOps: List<SleepOp> = emptyList()
    private var trackedOps: List<SleepOp> = emptyList()

    init {
        viewModelScope.launch {
            val codeValue = settingsRepository.getShareCode().first() ?: return@launch
            runCatching {
                val uid = service.signInAnonymously()
                service.observeSleepOps(codeValue, authorUid = uid)
                    .retryWhen { cause, attempt ->
                        // A transient Firestore listener error must not kill the stream for good: the
                        // optimistic active overlay would freeze. Re-subscribe with capped backoff;
                        // trackedOps survives so a consumed op keeps overlaying until the snapshot converges.
                        Log.w(TAG, "own sleep op listener error (attempt $attempt); retrying", cause)
                        delay(min(RETRY_BASE_MS * (attempt + 1), RETRY_MAX_MS))
                        true
                    }
                    .collect { ops ->
                        liveOps = ops
                        recomputeActive()
                    }
            }
        }
    }
```

(c) Replace `recomputeActive()` to reconcile before merging:

```kotlin
    private fun recomputeActive() {
        val reconciled = reconcilePendingOps(
            isReflected = { sleepActiveReflected(it, snapshotRecords) },
            liveOps = liveOps,
            tracked = trackedOps,
            nowMs = now().toEpochMilli(),
        )
        trackedOps = reconciled.nextTracked
        val snapshotActive = snapshotRecords.firstOrNull { it.endTime == null }
        val merged = mergeActiveSleep(snapshotActive, reconciled.effectiveOps, now().toEpochMilli())
        val session = merged.session
        _uiState.update {
            it.copy(
                active = session,
                stopping = merged.stopping,
                canEditActive = session != null &&
                    session.startedBy == SleepAuthor.PARTNER.name &&
                    session.clientId.isNotEmpty(),
            )
        }
    }
```

(d) Update imports: add `import com.babytracker.sharing.domain.model.reconcilePendingOps` and `import com.babytracker.sharing.domain.model.sleepActiveReflected`. (`mergeActiveSleep` import stays.)

- [ ] **Step 4: Remove the dashboard screen's `snapshotRefreshTick` effect**

In `PartnerDashboardScreen.kt`, delete this block (around line 172-176):

```kotlin
    // Pull-based dashboard: refetch once the primary applies a partner sleep op so the snapshot
    // reflects the change instead of the optimistic overlay reverting to stale data.
    LaunchedEffect(sleepState.snapshotRefreshTick) {
        if (sleepState.snapshotRefreshTick > 0) viewModel.refresh()
    }
```

(The remaining refresh affordances still reference `viewModel.refresh()`, which exists until Task 7 — the screen still compiles.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.babytracker.ui.partner.PartnerSleepViewModelTest"`
Expected: PASS.

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (the screen compiles without `snapshotRefreshTick`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/partner/PartnerSleepViewModel.kt app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt app/src/test/java/com/babytracker/ui/partner/PartnerSleepViewModelTest.kt
git commit -m "refactor(partner): sleep active overlay reconciles instead of bumping a refresh tick"
```

---

## Task 7: Dashboard view model + screen live-update rewrite

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt`
- Test: `app/src/test/java/com/babytracker/ui/partner/PartnerDashboardViewModelTest.kt`
- Test: `app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt`

**Interfaces:**
- Consumes: `ObservePartnerDataUseCase` (Task 3), `ObservePartnerFeedHistoryUseCase` (Task 4, `Flow` signature).
- Produces: `PartnerDashboardUiState` WITHOUT `lastRefreshAt`; `PartnerDashboardViewModel` collects the live flow, exposes `retry()` + `clearError()`, drops `refresh()`. The screen has no pull-to-refresh, no top-bar refresh button, no "check shared updates" button, and sources the sleep tile's active session from `sleepState.active`.

This is the coupled VM+screen unit — they change together because the screen consumes the VM's removed `refresh`/`lastRefreshAt`.

- [ ] **Step 1: Rewrite the dashboard view model test (failing)**

Replace `PartnerDashboardViewModelTest.kt`:

```kotlin
package com.babytracker.ui.partner

import android.content.Context
import com.babytracker.R
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.MergedFeedHistory
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.PartnerDataFetchException
import com.babytracker.widget.WidgetUpdater
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerDashboardViewModelTest {

    private lateinit var observePartnerData: ObservePartnerDataUseCase
    private lateinit var observePartnerFeedHistory: ObservePartnerFeedHistoryUseCase
    private lateinit var widgetUpdater: WidgetUpdater
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var appContext: Context

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        observePartnerData = mockk()
        observePartnerFeedHistory = mockk()
        widgetUpdater = mockk { coJustRun { updateAll() } }
        settingsRepository = mockk { every { getVolumeUnit() } returns flowOf(VolumeUnit.ML) }
        appContext = mockk {
            every { getString(R.string.error_partner_refresh_no_data) } returns
                "We couldn't check for shared updates. Pull down to try again."
            every { getString(R.string.error_partner_refresh_stale) } returns
                "We couldn't check just now. Showing the last shared update."
        }
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(emptyList()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeSnapshot() = ShareSnapshot(
        lastSyncAt = Instant.now(),
        baby = BabySnapshot("Baby", Instant.now().toEpochMilli(), emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
    )

    private fun build() = PartnerDashboardViewModel(
        observePartnerData, observePartnerFeedHistory, widgetUpdater, settingsRepository, appContext,
    )

    @Test
    fun `init streams the live snapshot`() = runTest {
        val snapshot = makeSnapshot()
        every { observePartnerData() } returns flowOf(snapshot)

        val viewModel = build()

        assertEquals(snapshot, viewModel.uiState.value.snapshot)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `revoke sets disconnected and updates widgets`() = runTest {
        every { observePartnerData() } returns flow { throw PartnerAccessRevokedException("revoked") }

        val viewModel = build()

        assertTrue(viewModel.uiState.value.isDisconnected)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 1) { widgetUpdater.updateAll() }
    }

    @Test
    fun `fetch failure shows retryable error without disconnecting`() = runTest {
        every { observePartnerData() } returns flow { throw PartnerDataFetchException("Could not load partner data") }

        val viewModel = build()

        assertEquals(
            "We couldn't check for shared updates. Pull down to try again.",
            viewModel.uiState.value.error,
        )
        assertFalse(viewModel.uiState.value.isDisconnected)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { widgetUpdater.updateAll() }
    }

    @Test
    fun `retry restarts the collection and clears the disconnected state`() = runTest {
        val snapshot = makeSnapshot()
        every { observePartnerData() } returnsMany listOf(
            flow { throw PartnerAccessRevokedException("revoked") },
            flowOf(snapshot),
        )

        val viewModel = build()
        assertTrue(viewModel.uiState.value.isDisconnected)

        viewModel.retry()

        assertEquals(snapshot, viewModel.uiState.value.snapshot)
        assertFalse(viewModel.uiState.value.isDisconnected)
    }

    @Test
    fun `bottle feed overlay is applied to the snapshot`() = runTest {
        val snapshot = makeSnapshot()
        val overlaid = com.babytracker.sharing.domain.model.BottleFeedSnapshot(
            timestamp = 5_000L, volumeMl = 120, type = "FORMULA", clientId = "c1", author = "PARTNER",
        )
        every { observePartnerData() } returns flowOf(snapshot)
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(listOf(overlaid)))

        val viewModel = build()

        assertEquals(listOf(overlaid), viewModel.uiState.value.snapshot?.bottleFeeds)
    }

    @Test
    fun `clearError removes the error`() = runTest {
        every { observePartnerData() } returns flow { throw PartnerDataFetchException("x") }
        val viewModel = build()

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.babytracker.ui.partner.PartnerDashboardViewModelTest"`
Expected: FAIL — constructor signature and `retry()` do not exist yet.

- [ ] **Step 3: Rewrite the dashboard view model**

Replace `PartnerDashboardViewModel.kt`:

```kotlin
package com.babytracker.ui.partner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerDashboardUiState(
    val snapshot: ShareSnapshot? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDisconnected: Boolean = false,
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
)

@HiltViewModel
class PartnerDashboardViewModel @Inject constructor(
    private val observePartnerData: ObservePartnerDataUseCase,
    private val observePartnerFeedHistory: ObservePartnerFeedHistoryUseCase,
    private val widgetUpdater: WidgetUpdater,
    settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerDashboardUiState(isLoading = true))
    val uiState: StateFlow<PartnerDashboardUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    init {
        start()
        viewModelScope.launch {
            settingsRepository.getVolumeUnit().collect { unit ->
                _uiState.update { it.copy(volumeUnit = unit) }
            }
        }
    }

    private fun start() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // ponytail: observePartnerData() is subscribed twice (here + inside the feed-history use
            // case) -> two listeners on one doc. Fine for one screen; share if it ever matters.
            val snapshots = observePartnerData()
            combine(
                snapshots,
                observePartnerFeedHistory(snapshots.map { it.bottleFeeds }),
            ) { snapshot, feedHistory -> snapshot.copy(bottleFeeds = feedHistory.entries) }
                .catch { error -> handleError(error) }
                .collect { snapshot ->
                    _uiState.update {
                        it.copy(snapshot = snapshot, isLoading = false, isDisconnected = false, error = null)
                    }
                }
        }
    }

    private suspend fun handleError(error: Throwable) {
        if (error is PartnerAccessRevokedException) {
            // AppMode is now NONE (the use case cleared it atomically on confirmed revoke).
            // Re-render the widget so it stops showing stale partner data.
            _uiState.update { it.copy(isLoading = false, isDisconnected = true) }
            widgetUpdater.updateAll()
        } else {
            _uiState.update {
                val message = if (it.snapshot == null) {
                    appContext.getString(R.string.error_partner_refresh_no_data)
                } else {
                    appContext.getString(R.string.error_partner_refresh_stale)
                }
                it.copy(isLoading = false, error = message)
            }
        }
    }

    /** Restarts the live collection (used by the Error/Empty state retry buttons). */
    fun retry() = start()

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
```

- [ ] **Step 4: Update the dashboard screen**

In `PartnerDashboardScreen.kt`, apply these edits:

(a) **Imports** — remove `import androidx.compose.material.icons.filled.Refresh` only if no longer referenced; it is still used by `EmptyState`/`ErrorState` buttons, so **keep it**. Remove `import androidx.compose.material3.pulltorefresh.PullToRefreshBox`.

(b) **`isRefreshingExistingDashboard`** — delete the line `val isRefreshingExistingDashboard = uiState.isLoading && snapshot != null`.

(c) **Top bar actions** — replace the whole `actions = { ... }` block with just the Settings button:

```kotlin
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings_content_description))
                    }
                },
```

(d) **`bottleFeedState.saved` effect** — keep only the sheet-close:

```kotlin
    LaunchedEffect(bottleFeedState.saved) {
        if (bottleFeedState.saved) {
            showBottleFeedSheet = false
        }
    }
```

(e) **`sleepState.accessRevoked` effect** — delete the entire `LaunchedEffect(sleepState.accessRevoked) { ... }` block. Revoke is now detected by the live partner-connection listener inside `ObservePartnerDataUseCase`.

(f) **Replace `PullToRefreshBox` with a plain `Box`.** Replace:

```kotlin
        val pullRefreshDescription = stringResource(R.string.partner_pull_refresh)
        val loadingDescription = stringResource(R.string.partner_loading)
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .semantics {
                    contentDescription = pullRefreshDescription
                },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
```

with:

```kotlin
        val loadingDescription = stringResource(R.string.partner_loading)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
```

(and delete the now-extra closing brace that previously matched the inner `Box`; the `when { ... }` block stays inside this single `Box`).

(g) **`DashboardContent` call** — drop `lastRefreshAt`/`isLoading`/`onRefresh`, add `activeSleep`:

```kotlin
                    snapshot != null -> {
                        DashboardContent(
                            snapshot = snapshot,
                            activeSleep = sleepState.active,
                            error = uiState.error,
                            onClearError = viewModel::clearError,
                            onLogBottle = {
                                bottleFeedViewModel.startLogging()
                                showBottleFeedSheet = true
                            },
                            onOpenSleepSheet = { showSleepSheet = true },
                            onNavigateToFeedHistory = onNavigateToFeedHistory,
                            nowProvider = nowProvider,
                            volumeUnit = uiState.volumeUnit,
                        )
                    }
```

Update the `ErrorState`/`EmptyState` `onRetry`/`onRefresh` references in the same `when` to `viewModel::retry`.

(h) **`DashboardContent` signature** — remove `lastRefreshAt`, `isLoading`, `onRefresh`; add `activeSleep`:

```kotlin
@Composable
private fun DashboardContent(
    snapshot: ShareSnapshot,
    activeSleep: SleepSnapshot?,
    error: String?,
    onClearError: () -> Unit,
    onLogBottle: () -> Unit,
    onOpenSleepSheet: () -> Unit,
    onNavigateToFeedHistory: () -> Unit,
    nowProvider: () -> Long,
    volumeUnit: VolumeUnit,
) {
```

Inside `DashboardContent`: delete the line `val activeSleep = remember(snapshot.sleepRecords) { snapshot.sleepRecords.firstOrNull { it.endTime == null } }` (now a parameter). Delete the `lastCheckedText` `remember` block. In the sleep-prediction call, change `hasActiveSleep = snapshot.sleepRecords.any { it.endTime == null }` to `hasActiveSleep = activeSleep != null`. Remove the trailing `Spacer(...)` + `RefreshSharedUpdatesButton(isLoading = isLoading, onRefresh = onRefresh)` at the end of the `Column`.

(i) **`PartnerSyncStrip` call** — drop `lastCheckedText`:

```kotlin
            PartnerSyncStrip(
                activeSession = activeSession,
                activeSleep = activeSleep,
                lastSharedText = lastSharedText,
                isShareStale = isShareStale,
                error = error,
                onClearError = onClearError,
            )
```

(j) **`PartnerSyncStrip` signature** — remove the `lastCheckedText: String?` parameter, the `partner_status_cd_checked` branch (always use `partner_status_cd`), and the `if (lastCheckedText != null) { Text(partner_checked_ago...) }` block:

```kotlin
@Composable
private fun PartnerSyncStrip(
    activeSession: SessionSnapshot?,
    activeSleep: SleepSnapshot?,
    lastSharedText: String,
    isShareStale: Boolean,
    error: String?,
    onClearError: () -> Unit,
) {
    val context = LocalContext.current
    val activeStateText = when {
        activeSession != null -> stringResource(R.string.partner_status_feeding)
        activeSleep != null -> stringResource(
            R.string.partner_status_in_progress,
            sleepTypeLabel(activeSleep.sleepType, context),
        )
        else -> stringResource(R.string.partner_status_quiet)
    }
    val stateText = if (isShareStale) {
        stringResource(R.string.partner_share_behind)
    } else {
        stringResource(R.string.partner_share_current)
    }
    val statusDescription = stringResource(
        R.string.partner_status_cd,
        activeStateText,
        stateText,
        lastSharedText.lowercaseFirstChar(),
    )
    // ... Card { Column { Row { Icon; Column { Text(partner_shared_ago, ...) } } ... } } unchanged
    // except the inner `if (lastCheckedText != null) { Text(...) }` block is deleted.
```

Keep the rest of the `Card`/`Column`/`Row` body exactly as-is, minus the deleted "checked ago" `Text`.

(k) **Delete `RefreshSharedUpdatesButton`** — remove the entire `@Composable private fun RefreshSharedUpdatesButton(...) { ... }` function.

(l) `context` in `PartnerSyncStrip` becomes unused once the `partner_status_cd_checked` branch is gone only if `sleepTypeLabel` no longer needs it — it still does (`sleepTypeLabel(activeSleep.sleepType, context)`), so **keep** `val context = LocalContext.current`.

- [ ] **Step 5: Update the instrumentation test**

In `PartnerDashboardScreenTest.kt`:

(a) Rework the VM builders and fake to the new constructor. Replace `buildViewModel`/`buildFetchUseCase` with:

```kotlin
    private fun buildViewModel(): PartnerDashboardViewModel = buildViewModel(snapshot = null)

    private fun buildViewModel(snapshot: ShareSnapshot?): PartnerDashboardViewModel {
        val service = fakeSharing(snapshot).service
        val settings = FakePartnerSettingsRepository()
        val observeData = ObservePartnerDataUseCase(service, settings, dagger.Lazy { error("debug builder unused") })
        val observeFeed = ObservePartnerFeedHistoryUseCase(service, settings) { fixedNow }
        return PartnerDashboardViewModel(observeData, observeFeed, noOpWidgetUpdater, settings, appContext)
    }
```

(b) In `fakeSharing`, stub the new live listeners (the static `FirestoreSharingServiceKt` mock is already set up in `@Before`). Add, alongside the existing `observeFeedOps`/`observeSleepOps` stubs:

```kotlin
        every { service.observeSnapshot(any()) } returns
            flowOf(com.babytracker.sharing.data.firebase.SnapshotEmission(snapshot, fromCache = false))
        every { service.observePartnerConnected(any(), any()) } returns
            flowOf(com.babytracker.sharing.data.firebase.ConnectionEmission(connected = true, fromCache = false))
```

Add the imports `com.babytracker.sharing.data.firebase.observeSnapshot` and `com.babytracker.sharing.data.firebase.observePartnerConnected`. The `fetchSnapshot` stub can stay (now unused by the dashboard) or be removed.

(c) **Delete the obsolete refresh tests**: `pullDownRefreshChecksForSharedUpdates`, `refreshingExistingDashboardShowsOneProgressSignal`, and `refreshButtonExposesCurrentStateForAccessibility`. Remove the now-unused `FetchPartnerDataUseCase` import and the `beforeFetchReturns`/`fetchCount` machinery from `FakeService`/`fakeSharing` if no longer referenced.

(d) In `narrowLargeFontDashboardKeepsLongTextUsable` and `darkThemeDashboardShowsPartnerStatusAndWarning`, remove the final `composeRule.onNodeWithText("Check shared updates")...` assertions (the button no longer exists).

(e) The spec asks the androidTest to "assert the sleep tile reflects an optimistic active session." The tile→`sleepState.active` wiring is already exercised by the retained `activeSleepIsPresentedInHeroStatusInsteadOfQuietState` and `activePartnerSleepShowsEditAffordance` tests (active sourced via `onSleepRecordsAvailable` → `mergeActiveSleep`). The purely-optimistic op-only path (no snapshot active) is deterministically unit-covered by Task 6's `a consumed START overlay is retained` test, so no fragile op-injecting instrumentation test is added here.

- [ ] **Step 6: Run the dashboard unit test**

Run: `./gradlew test --tests "com.babytracker.ui.partner.PartnerDashboardViewModelTest"`
Expected: PASS.

- [ ] **Step 7: Run the instrumentation test (requires an emulator/device)**

First confirm a device: `adb devices` (per project convention an emulator is usually running). Then:
Run: `./gradlew connectedAndroidTest --tests "com.babytracker.ui.partner.PartnerDashboardScreenTest"`
Expected: PASS. If no device is attached, note it and rely on Step 6 + the full `build` in Task 8; the androidTest is still updated for correctness.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/partner/PartnerDashboardViewModel.kt app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt app/src/test/java/com/babytracker/ui/partner/PartnerDashboardViewModelTest.kt app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt
git commit -m "refactor(partner): dashboard renders the live snapshot and drops manual refresh"
```

---

## Task 8: Full validation and unused-string cleanup

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-pt-rBR/strings.xml` (remove now-unused keys).

- [ ] **Step 1: Remove now-unused partner strings**

After Task 7, these string keys are no longer referenced from code (verify each with `rg` before deleting). Candidates introduced for the removed refresh affordances:
`partner_checked_ago`, `partner_status_cd_checked`, `partner_pull_refresh`, `partner_checking`, `partner_checking_updates`, `partner_check_shared`, `partner_check_state_loading`, `partner_check_state_ready`.

**Keep** these — they are still referenced after Task 7: `partner_check_again` (the `ErrorState` retry button), `partner_check_updates_cd` (the `EmptyState` retry button still uses it as its label/content description), and `partner_loading` (the initial-load spinner description). The `rg` check below is the safeguard: only delete a key whose grep returns zero `.kt` hits.

For each candidate, confirm zero references:

```bash
rg "partner_checked_ago|partner_status_cd_checked|partner_pull_refresh|partner_checking\b|partner_checking_updates|partner_check_shared|partner_check_state_loading|partner_check_state_ready" app/src/main -g "*.kt"
```

Delete the confirmed-unused keys from BOTH `values/strings.xml` and `values-pt-rBR/strings.xml` (keep the two locales in lockstep — a missing `pt-rBR` translation or an orphaned key is a release-build risk).

- [ ] **Step 2: Run the full unit suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. Fix any stragglers (e.g. a test still referencing `lastRefreshAt`, `snapshotRefreshTick`, `hasConsumedPendingOps`, or the old use-case signatures) and rerun until green.

- [ ] **Step 3: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. This runs ktlint + detekt + lint + release-bundle checks. If detekt flags a violation, fix the code (never `@Suppress`); if ktlint flags formatting, the pre-commit hook will auto-fix on commit, but you may run `./gradlew ktlintFormat` to diagnose.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-pt-rBR/strings.xml
git commit -m "chore(partner): remove unused refresh-affordance strings"
```

- [ ] **Step 5: Manual smoke (optional, requires two devices or the debug placeholder)**

With a primary device logging data and a partner device on the dashboard, verify: (1) a primary log appears on the partner within ~1s with no manual refresh; (2) a partner bottle log / sleep start appears instantly and does not flicker out when the primary consumes the op; (3) removing the partner from the primary drives the partner to the "sharing ended" dialog.

---

## Notes on out-of-scope items (from the spec)

- `FetchPartnerDataUseCase` and `WidgetRefreshWorker` are deliberately **untouched** — the widget worker stays one-shot (WorkManager cannot hold a listener).
- No Firestore rule change, no Room migration, no new dependency.
- True server-side revocation remains out of scope; `PartnerAccessRevokedException` means "disconnect the partner UI", preserving the existing capability model exactly.
