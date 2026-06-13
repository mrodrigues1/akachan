# Home Tile Drag-and-Drop Reorder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user long-press any Home-screen tile (the 6 square cards *and* the full-width Sleep-prediction / Tip / Partner cards) and drag it to a new position, persisting the custom order across launches.

**Architecture:** Replace the hardcoded `Column`/`Row` layout in `HomeScreen` with a data-driven `LazyVerticalStaggeredGrid` whose item order comes from a persisted `List<HomeTile>`. A pure-Kotlin `HomeTile` enum identifies every movable card; order is stored in DataStore (one `stringPreferencesKey`) via `SettingsRepository`, reconciled on read so added/removed tiles never corrupt a saved order. Drag-and-drop uses the `sh.calvin.reorderable` library's staggered-grid support (built for mixed item sizes); reorder operates on tile **keys** against the full list so hidden (conditional) tiles keep their slots.

**Tech Stack:** Kotlin 2.3.20, Jetpack Compose (BOM 2026.03.00), `androidx.compose.foundation` LazyVerticalStaggeredGrid, `sh.calvin.reorderable:reorderable:3.1.0`, DataStore Preferences, Hilt, JUnit 5 + MockK + Turbine (unit), Compose UI Test (instrumented).

**Delivery:** 4 PRs, each its own Linear issue. They are sequential — PR2 depends on PR1, etc. Every PR builds green and is independently mergeable.

- **PR 1** — `HomeTile` model + order persistence (data layer only, no UI change).
- **PR 2** — Data-driven HomeScreen via static `LazyVerticalStaggeredGrid` (no drag yet).
- **PR 3** — Add reorderable lib + long-press drag + persist reorder.
- **PR 4** — Accessibility move actions + reset-to-default + haptics.

---

## File Structure

| File | PR | Responsibility |
|------|----|----|
| `app/src/main/java/com/babytracker/domain/model/HomeTile.kt` | 1 | Pure enum of movable tiles; default order; serialize / deserialize / reconcile |
| `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt` | 1 | Add `getHomeTileOrder` / `setHomeTileOrder` |
| `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt` | 1 | DataStore key + read/write |
| `app/src/test/java/com/babytracker/domain/model/HomeTileTest.kt` | 1 | Serialize/reconcile logic |
| `app/src/test/java/com/babytracker/data/repository/SettingsRepositoryHomeTileTest.kt` | 1 | Repo round-trip |
| `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt` | 2 | Add `tileOrder` to state + `onTilesReordered` handler |
| `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt` | 2,3,4 | Data-driven grid; tile dispatch; drag; a11y |
| `app/src/main/java/com/babytracker/ui/home/HomeTileContent.kt` | 2 | `@Composable HomeTileContent(...)` dispatcher + extracted Breastfeeding/Sleep cards |
| `gradle/libs.versions.toml` | 3 | `reorderable` version + library coords |
| `app/build.gradle.kts` | 3 | `implementation(libs.reorderable)` |
| `CLAUDE.md` | 3 | Tech-stack table row for reorderable |
| `app/src/test/java/com/babytracker/ui/home/HomeViewModelTileOrderTest.kt` | 2,3 | Order in state; reorder-by-key persistence |
| `app/src/androidTest/java/com/babytracker/ui/home/HomeReorderTest.kt` | 2,3 | Render order + conditional hide; drag smoke |

---

## Task 1 — PR 1: HomeTile model + order persistence

**Branch:** `feat/home-tile-order-model`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/HomeTile.kt`
- Modify: `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt`
- Test: `app/src/test/java/com/babytracker/domain/model/HomeTileTest.kt`
- Test: `app/src/test/java/com/babytracker/data/repository/SettingsRepositoryHomeTileTest.kt`

- [ ] **Step 1: Write the failing test for `HomeTile` serialize/reconcile**

Create `app/src/test/java/com/babytracker/domain/model/HomeTileTest.kt`:

```kotlin
package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeTileTest {

    @Test
    fun `deserialize null returns default order`() {
        assertEquals(HomeTile.DEFAULT_ORDER, HomeTile.deserialize(null))
    }

    @Test
    fun `deserialize blank returns default order`() {
        assertEquals(HomeTile.DEFAULT_ORDER, HomeTile.deserialize("   "))
    }

    @Test
    fun `serialize then deserialize round-trips a custom order`() {
        val custom = listOf(HomeTile.SLEEP, HomeTile.BREASTFEEDING) +
            HomeTile.DEFAULT_ORDER.filter { it != HomeTile.SLEEP && it != HomeTile.BREASTFEEDING }
        assertEquals(custom, HomeTile.deserialize(HomeTile.serialize(custom)))
    }

    @Test
    fun `reconcile drops unknown stored names`() {
        val raw = "SLEEP,GHOST_TILE,BREASTFEEDING"
        val result = HomeTile.deserialize(raw)
        // GHOST_TILE dropped; SLEEP, BREASTFEEDING kept first; remaining defaults appended
        assertEquals(HomeTile.SLEEP, result[0])
        assertEquals(HomeTile.BREASTFEEDING, result[1])
        assertEquals(HomeTile.DEFAULT_ORDER.size, result.size)
        assertEquals(HomeTile.entries.toSet(), result.toSet())
    }

    @Test
    fun `reconcile appends tiles missing from a stale stored order`() {
        // Only two tiles were saved (older app version); the rest must be appended in default order.
        val result = HomeTile.deserialize("PARTNER,SLEEP")
        assertEquals(HomeTile.PARTNER, result[0])
        assertEquals(HomeTile.SLEEP, result[1])
        assertEquals(HomeTile.entries.toSet(), result.toSet())
        // appended tiles keep their relative DEFAULT_ORDER sequence
        val appended = result.drop(2)
        val expectedAppended = HomeTile.DEFAULT_ORDER.filter { it != HomeTile.PARTNER && it != HomeTile.SLEEP }
        assertEquals(expectedAppended, appended)
    }
}
```

- [ ] **Step 2: Run it and confirm it fails to compile (`HomeTile` not defined)**

Run: `./gradlew test --tests "com.babytracker.domain.model.HomeTileTest" -PfastTests`
Expected: FAIL — unresolved reference `HomeTile`.

- [ ] **Step 3: Create `HomeTile`**

Create `app/src/main/java/com/babytracker/domain/model/HomeTile.kt`:

```kotlin
package com.babytracker.domain.model

/**
 * Identifies every Home-screen card the user can reorder. Pure Kotlin (no framework imports) so it
 * can flow through the repository layer. Stored as a comma-joined list of [name]s in DataStore.
 */
enum class HomeTile {
    BREASTFEEDING,
    SLEEP,
    PUMPING,
    INVENTORY,
    BOTTLE_FEED,
    FEEDING_HISTORY,
    SLEEP_PREDICTION,
    TIP,
    PARTNER,
    ;

    companion object {
        /** Canonical order used when the user has never reordered. Mirrors the pre-feature layout. */
        val DEFAULT_ORDER: List<HomeTile> = listOf(
            BREASTFEEDING,
            SLEEP,
            PUMPING,
            INVENTORY,
            BOTTLE_FEED,
            FEEDING_HISTORY,
            SLEEP_PREDICTION,
            TIP,
            PARTNER,
        )

        /**
         * Reconcile a persisted order with the current tile set:
         * - keep stored tiles in their saved position,
         * - drop names that no longer map to a tile (removed in a later version),
         * - append tiles added since the order was saved, in their [DEFAULT_ORDER] sequence.
         */
        fun reconcile(stored: List<String>): List<HomeTile> {
            val known = stored.mapNotNull { name -> entries.firstOrNull { it.name == name } }
            val seen = known.toSet()
            val appended = DEFAULT_ORDER.filter { it !in seen }
            return known + appended
        }

        fun serialize(order: List<HomeTile>): String = order.joinToString(",") { it.name }

        fun deserialize(raw: String?): List<HomeTile> =
            if (raw.isNullOrBlank()) {
                DEFAULT_ORDER
            } else {
                reconcile(raw.split(",").map(String::trim).filter { it.isNotEmpty() })
            }
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew test --tests "com.babytracker.domain.model.HomeTileTest" -PfastTests`
Expected: PASS (5 tests).

- [ ] **Step 5: Add repository interface methods**

In `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`, add the import and two methods (place after `setVolumeUnit`):

```kotlin
import com.babytracker.domain.model.HomeTile
```

```kotlin
    fun getHomeTileOrder(): Flow<List<HomeTile>>
    suspend fun setHomeTileOrder(order: List<HomeTile>)
```

- [ ] **Step 6: Write the failing repo round-trip test**

Create `app/src/test/java/com/babytracker/data/repository/SettingsRepositoryHomeTileTest.kt`:

```kotlin
package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.babytracker.domain.model.HomeTile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlinx.coroutines.flow.first

// Mirrors the Robolectric-backed DataStore pattern already used in this module's repository tests.
@ExtendWith(org.robolectric.junit.jupiter.RobolectricExtension::class)
@Config(manifest = Config.NONE)
class SettingsRepositoryHomeTileTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl
    private lateinit var file: File

    @BeforeEach
    fun setup() {
        file = File.createTempFile("settings_home_tile", ".preferences_pb")
        file.delete()
        dataStore = PreferenceDataStoreFactory.create { file }
        repository = SettingsRepositoryImpl(dataStore)
    }

    @AfterEach
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `default order returned when nothing stored`() = runTest {
        assertEquals(HomeTile.DEFAULT_ORDER, repository.getHomeTileOrder().first())
    }

    @Test
    fun `set then get round-trips custom order`() = runTest {
        val custom = listOf(HomeTile.PARTNER, HomeTile.SLEEP) +
            HomeTile.DEFAULT_ORDER.filter { it != HomeTile.PARTNER && it != HomeTile.SLEEP }
        repository.setHomeTileOrder(custom)
        assertEquals(custom, repository.getHomeTileOrder().first())
    }
}
```

> **Note:** If `RobolectricExtension` / `org.robolectric.junit.jupiter` is not on the classpath in this module, copy the exact DataStore-test harness from an existing repository test under `app/src/test/java/com/babytracker/data/repository/` and match its runner annotations. Do not invent a new harness.

- [ ] **Step 7: Run it and confirm it fails (`getHomeTileOrder` unresolved)**

Run: `./gradlew test --tests "com.babytracker.data.repository.SettingsRepositoryHomeTileTest" -PfastTests`
Expected: FAIL — unresolved reference.

- [ ] **Step 8: Implement in `SettingsRepositoryImpl`**

Add import:

```kotlin
import com.babytracker.domain.model.HomeTile
```

Add the key inside the `companion object` (after `VOLUME_UNIT`):

```kotlin
        val HOME_TILE_ORDER = stringPreferencesKey("home_tile_order")
```

Add the overrides (after `setVolumeUnit`):

```kotlin
    override fun getHomeTileOrder(): Flow<List<HomeTile>> =
        dataStore.data.map { HomeTile.deserialize(it[HOME_TILE_ORDER]) }

    override suspend fun setHomeTileOrder(order: List<HomeTile>) {
        dataStore.edit { it[HOME_TILE_ORDER] = HomeTile.serialize(order) }
    }
```

- [ ] **Step 9: Run both test classes and confirm green**

Run: `./gradlew test --tests "com.babytracker.domain.model.HomeTileTest" --tests "com.babytracker.data.repository.SettingsRepositoryHomeTileTest" -PfastTests`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/HomeTile.kt \
        app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt \
        app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt \
        app/src/test/java/com/babytracker/domain/model/HomeTileTest.kt \
        app/src/test/java/com/babytracker/data/repository/SettingsRepositoryHomeTileTest.kt
git commit -m "feat(home): add HomeTile model and tile-order persistence"
```

---

## Task 2 — PR 2: Data-driven HomeScreen (static staggered grid, no drag)

**Branch:** `feat/home-tile-data-driven-grid`

**Goal:** Render the existing tiles from `uiState.tileOrder` inside a `LazyVerticalStaggeredGrid`, honoring conditional visibility. No drag yet. Home looks the same (default order == old layout).

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/babytracker/ui/home/HomeTileContent.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- Test: `app/src/test/java/com/babytracker/ui/home/HomeViewModelTileOrderTest.kt`
- Test: `app/src/androidTest/java/com/babytracker/ui/home/HomeReorderTest.kt`

- [ ] **Step 1: Write the failing VM test — order surfaces in state**

Create `app/src/test/java/com/babytracker/ui/home/HomeViewModelTileOrderTest.kt`. Construct `HomeViewModel` the same way `HomeViewModelTest` does (copy its `@BeforeEach` mock wiring verbatim, then add the two lines below). Assert the default order is exposed:

```kotlin
    @Test
    fun `uiState exposes persisted tile order`() = runTest {
        every { settingsRepository.getHomeTileOrder() } returns flowOf(HomeTile.DEFAULT_ORDER)
        val vm = buildViewModel() // same construction as HomeViewModelTest
        vm.uiState.test {
            assertEquals(HomeTile.DEFAULT_ORDER, awaitItem().tileOrder)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

> Add `every { settingsRepository.getHomeTileOrder() } returns flowOf(HomeTile.DEFAULT_ORDER)` to the shared `@BeforeEach` of any existing `HomeViewModel` test setup you reuse, so previously-passing tests keep compiling.

- [ ] **Step 2: Run it — fails (`tileOrder` not a member of `HomeUiState`)**

Run: `./gradlew test --tests "com.babytracker.ui.home.HomeViewModelTileOrderTest" -PfastTests`
Expected: FAIL — unresolved `tileOrder` / `getHomeTileOrder`.

- [ ] **Step 3: Wire order into the ViewModel**

In `HomeViewModel.kt`:

1. Add import: `import com.babytracker.domain.model.HomeTile`.
2. Change the constructor param `settingsRepository: SettingsRepository` to `private val settingsRepository: SettingsRepository`.
3. Add to `HomeUiState`:

```kotlin
    val tileOrder: List<HomeTile> = HomeTile.DEFAULT_ORDER,
```

4. Replace the final `uiState` combine with a 3-flow combine:

```kotlin
    val uiState: StateFlow<HomeUiState> = combine(
        baseState,
        observeTodayFeedingSummary(),
        settingsRepository.getHomeTileOrder(),
    ) { base, todayFeedingSummary, tileOrder ->
        base.copy(todayFeedingSummary = todayFeedingSummary, tileOrder = tileOrder)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(),
    )
```

5. Add the reorder handler (used in PR 3; harmless now):

```kotlin
    fun onTilesReordered(newOrder: List<HomeTile>) {
        viewModelScope.launch { runCatching { settingsRepository.setHomeTileOrder(newOrder) } }
    }
```

- [ ] **Step 4: Run the VM test — passes**

Run: `./gradlew test --tests "com.babytracker.ui.home.HomeViewModelTileOrderTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Extract Breastfeeding & Sleep cards + create the dispatcher**

Create `app/src/main/java/com/babytracker/ui/home/HomeTileContent.kt`.

This file owns: (a) the `BreastfeedingHomeCard` and `SleepHomeCard` composables extracted from `HomeScreen.kt`, and (b) the `HomeTileContent` `when`-dispatcher plus the `isFullWidth` / `isVisible` helpers.

For (a), **move** the inline Breastfeeding card body (currently `HomeScreen.kt:172-263`) into:

```kotlin
@Composable
internal fun BreastfeedingHomeCard(
    uiState: HomeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) { /* paste the existing inline block: activeSession, animateColorAsState x3, Card { ... } */ }
```

and the inline Sleep card body (`HomeScreen.kt:266-359`) into:

```kotlin
@Composable
internal fun SleepHomeCard(
    uiState: HomeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) { /* paste the existing inline block: activeSleepRecord, animateColorAsState x3, Card { ... } */ }
```

Both already reference only `uiState` fields and helper composables (`ActiveStatusBadge`, `ActiveFeedingTimer`, `LastFeedingAgoText`, `FeedingPredictionSubtitle`, `ActiveSleepTimer`, `LastSleepAgoText`) that stay in `HomeScreen.kt` and are `internal`/`private`. Change those six helpers and `EaseOutQuart` from `private` to `internal` so `HomeTileContent.kt` (same package) can call them. Keep `weight`/`fillMaxHeight` OUT of the extracted cards — the grid sizes items, so the card root `Modifier` must come from the caller. Replace any `.weight(1f).fillMaxHeight()` on the moved Card with the passed `modifier`.

For (b), add:

```kotlin
internal fun HomeTile.isFullWidth(): Boolean = when (this) {
    HomeTile.SLEEP_PREDICTION, HomeTile.TIP, HomeTile.PARTNER -> true
    else -> false
}

internal fun HomeTile.isVisible(uiState: HomeUiState): Boolean = when (this) {
    HomeTile.TIP -> uiState.nextRecommendedSide != null
    HomeTile.PARTNER -> uiState.appMode == AppMode.NONE
    HomeTile.SLEEP_PREDICTION -> uiState.sleepPrediction !is SleepPredictionState.Unavailable
    else -> true
}

@Composable
internal fun HomeTileContent(
    tile: HomeTile,
    uiState: HomeUiState,
    callbacks: HomeTileCallbacks,
    modifier: Modifier = Modifier,
) {
    when (tile) {
        HomeTile.BREASTFEEDING -> BreastfeedingHomeCard(uiState, callbacks.onBreastfeeding, modifier)
        HomeTile.SLEEP -> SleepHomeCard(uiState, callbacks.onSleep, modifier)
        HomeTile.PUMPING -> PumpingHomeCard(uiState.pumpingActive, callbacks.onPumping, modifier)
        HomeTile.INVENTORY ->
            InventoryHomeCard(uiState.inventorySummary, uiState.volumeUnit, callbacks.onInventory, modifier)
        HomeTile.BOTTLE_FEED -> BottleFeedHomeCard(callbacks.onBottleFeed, modifier)
        HomeTile.FEEDING_HISTORY ->
            FeedingHistoryHomeCard(uiState.todayFeedingSummary, uiState.volumeUnit, callbacks.onFeedingHistory, modifier)
        HomeTile.SLEEP_PREDICTION ->
            SleepPredictionCard(state = uiState.sleepPrediction, onClick = callbacks.onSleep, modifier = modifier)
        HomeTile.TIP -> HomeTipCard(uiState.nextRecommendedSide, modifier)
        HomeTile.PARTNER -> PartnerViewCard(callbacks.onConnectPartner, modifier)
    }
}

internal data class HomeTileCallbacks(
    val onBreastfeeding: () -> Unit,
    val onSleep: () -> Unit,
    val onPumping: () -> Unit,
    val onInventory: () -> Unit,
    val onBottleFeed: () -> Unit,
    val onFeedingHistory: () -> Unit,
    val onConnectPartner: () -> Unit,
)
```

> `SleepPredictionCard` currently takes `(state, onClick)`. Add a `modifier: Modifier = Modifier` parameter to its signature in `ui/sleep/SleepPredictionCard.kt` and apply it to its root, so the grid can size it. It keeps self-hiding on `Unavailable` (the grid simply won't show that tile because `isVisible` already filtered it).

- [ ] **Step 6: Extract Tip and Partner cards**

Move the Tip card body (`HomeScreen.kt:406-444`, the inner `Card { Row { ... } }`, dropping the outer `AnimatedVisibility`) into `HomeTileContent.kt`:

```kotlin
@Composable
internal fun HomeTipCard(nextRecommendedSide: BreastSide?, modifier: Modifier = Modifier) {
    val nextSideName = nextRecommendedSide?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
    Card(/* existing Card args + modifier.fillMaxWidth() */) { /* existing Row content */ }
}
```

Move the Partner card body (`HomeScreen.kt:446-490`, inner `Card`, dropping the outer `AnimatedVisibility`) into:

```kotlin
@Composable
internal fun PartnerViewCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, /* existing args + modifier.fillMaxWidth() */) { /* existing Row content */ }
}
```

Conditional visibility now lives in `isVisible` (the grid filters), not in `AnimatedVisibility`.

- [ ] **Step 7: Replace HomeScreen body with a static staggered grid**

In `HomeScreen.kt`, replace the `Box { Column(verticalScroll) { ... } }` content (`HomeScreen.kt:146-494`) with a `LazyVerticalStaggeredGrid`. Keep the `Scaffold` + `TopAppBar` exactly as-is. New body:

```kotlin
    ) { padding ->
        val callbacks = remember(
            onNavigateToBreastfeeding, onNavigateToSleep, onNavigateToPumping,
            onNavigateToInventory, onNavigateToBottleFeed, onNavigateToFeedingHistory,
            onNavigateToConnectPartner,
        ) {
            HomeTileCallbacks(
                onBreastfeeding = onNavigateToBreastfeeding,
                onSleep = onNavigateToSleep,
                onPumping = onNavigateToPumping,
                onInventory = onNavigateToInventory,
                onBottleFeed = onNavigateToBottleFeed,
                onFeedingHistory = onNavigateToFeedingHistory,
                onConnectPartner = onNavigateToConnectPartner,
            )
        }
        val visibleTiles = remember(uiState.tileOrder, uiState) {
            uiState.tileOrder.filter { it.isVisible(uiState) }
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = visibleTiles,
                key = { it.name },
                span = { tile -> if (tile.isFullWidth()) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane },
            ) { tile ->
                HomeTileContent(
                    tile = tile,
                    uiState = uiState,
                    callbacks = callbacks,
                    modifier = Modifier.heightIn(min = if (tile.isFullWidth()) 0.dp else 140.dp),
                )
            }
        }
    }
```

Add imports: `androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid`, `...StaggeredGridCells`, `...StaggeredGridItemSpan`, `...items`, `androidx.compose.foundation.layout.PaddingValues`. Remove now-unused imports (`verticalScroll`, `rememberScrollState`, the moved-out card imports). Delete the extracted card bodies and the two `AnimatedVisibility` blocks from `HomeScreen.kt`.

> The old `widthIn(max = 600.dp)` centering is dropped here for simplicity; if tablet centering matters, wrap the grid in a `Box(contentAlignment = TopCenter)` with the width cap on the grid `Modifier`. Out of scope unless a reviewer flags it.

- [ ] **Step 8: Write the instrumented render test**

Create `app/src/androidTest/java/com/babytracker/ui/home/HomeReorderTest.kt`. Render `HomeScreen` (or the grid extracted as a testable composable taking `uiState`) with a fake state and assert tiles appear and conditional ones hide:

```kotlin
    @Test
    fun tipTileHiddenWhenNoRecommendedSide() {
        composeTestRule.setContent {
            // render grid with uiState(nextRecommendedSide = null)
        }
        composeTestRule.onNodeWithText("TIP").assertDoesNotExist()
        composeTestRule.onNodeWithText("Breastfeeding").assertIsDisplayed()
    }
```

> Match the harness used by the existing `HomeScreenTest.kt` in the same folder (same rule, same fake-VM or state-injection approach). Reuse its setup verbatim.

- [ ] **Step 9: Run unit + build**

Run: `./gradlew test --tests "com.babytracker.ui.home.HomeViewModelTileOrderTest" -PfastTests`
Then: `./gradlew assembleDebug`
Expected: tests PASS; debug build succeeds. (Instrumented test runs on CI / device per project norms.)

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/home/ \
        app/src/main/java/com/babytracker/ui/sleep/SleepPredictionCard.kt \
        app/src/test/java/com/babytracker/ui/home/HomeViewModelTileOrderTest.kt \
        app/src/androidTest/java/com/babytracker/ui/home/HomeReorderTest.kt
git commit -m "refactor(home): render tiles from persisted order via staggered grid"
```

---

## Task 3 — PR 3: Add reorderable library + long-press drag + persist

**Branch:** `feat/home-tile-drag-drop`

**Goal:** Long-press any tile to drag-reorder; persist on drop. Active timers survive reorder (stable keys, already set in PR 2).

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `CLAUDE.md`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- Test: `app/src/test/java/com/babytracker/ui/home/HomeViewModelTileOrderTest.kt`

- [ ] **Step 1: Add the dependency to the version catalog**

In `gradle/libs.versions.toml`, add under `[versions]`:

```toml
reorderable = "3.1.0"
```

and under `[libraries]`:

```toml
reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }
```

- [ ] **Step 2: Wire it into the module**

In `app/build.gradle.kts`, add inside `dependencies { }` (near the Compose block):

```kotlin
    // Drag-and-drop reordering
    implementation(libs.reorderable)
```

- [ ] **Step 3: Confirm it resolves**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath` and grep for `sh.calvin.reorderable:reorderable:3.1.0`, OR run `./gradlew assembleDebug`.
Expected: resolves / builds.

- [ ] **Step 4: Write the failing reorder-by-key VM test**

Add to `HomeViewModelTileOrderTest.kt`:

```kotlin
    @Test
    fun `onTilesReordered persists the new order`() = runTest {
        val slot = slot<List<HomeTile>>()
        coEvery { settingsRepository.setHomeTileOrder(capture(slot)) } returns Unit
        val vm = buildViewModel()
        val moved = listOf(HomeTile.SLEEP) + HomeTile.DEFAULT_ORDER.filter { it != HomeTile.SLEEP }
        vm.onTilesReordered(moved)
        advanceUntilIdle()
        coVerify { settingsRepository.setHomeTileOrder(moved) }
        assertEquals(moved, slot.captured)
    }
```

- [ ] **Step 5: Run it — should already PASS**

`onTilesReordered` exists from PR 2. Run:
`./gradlew test --tests "com.babytracker.ui.home.HomeViewModelTileOrderTest" -PfastTests`
Expected: PASS. (This locks the persistence contract before wiring the gesture.)

- [ ] **Step 6: Add the key-based reorder helper to `HomeScreen.kt`**

```kotlin
/**
 * Move [fromKey] to [toKey]'s slot within the full [order]. Operating on keys (not visible indices)
 * means hidden/conditional tiles keep their absolute positions during a drag of visible tiles.
 */
internal fun reorderByKey(order: List<HomeTile>, fromKey: HomeTile, toKey: HomeTile): List<HomeTile> {
    if (fromKey == toKey) return order
    val mutable = order.toMutableList()
    val fromIdx = mutable.indexOf(fromKey)
    val toIdx = mutable.indexOf(toKey)
    if (fromIdx < 0 || toIdx < 0) return order
    mutable.add(toIdx, mutable.removeAt(fromIdx))
    return mutable
}
```

- [ ] **Step 7: Make the grid reorderable with optimistic local state**

Replace the PR 2 grid block with the reorderable version. Persisted order arrives via StateFlow (async), so mirror it in local `mutableStateOf` for lag-free drag animation, persisting on each move and re-syncing when the VM emits.

```kotlin
        var orderState by remember { mutableStateOf(uiState.tileOrder) }
        LaunchedEffect(uiState.tileOrder) { orderState = uiState.tileOrder }

        val visibleTiles = remember(orderState, uiState) {
            orderState.filter { it.isVisible(uiState) }
        }
        val gridState = rememberLazyStaggeredGridState()
        val reorderableState = rememberReorderableLazyStaggeredGridState(gridState) { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyStaggeredGridState
            val toKey = to.key as? String ?: return@rememberReorderableLazyStaggeredGridState
            val fromTile = HomeTile.entries.firstOrNull { it.name == fromKey } ?: return@rememberReorderableLazyStaggeredGridState
            val toTile = HomeTile.entries.firstOrNull { it.name == toKey } ?: return@rememberReorderableLazyStaggeredGridState
            val newOrder = reorderByKey(orderState, fromTile, toTile)
            orderState = newOrder
            viewModel.onTilesReordered(newOrder)
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = visibleTiles,
                key = { it.name },
                span = { tile -> if (tile.isFullWidth()) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane },
            ) { tile ->
                ReorderableItem(reorderableState, key = tile.name) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "tileDragElevation")
                    HomeTileContent(
                        tile = tile,
                        uiState = uiState,
                        callbacks = callbacks,
                        modifier = Modifier
                            .heightIn(min = if (tile.isFullWidth()) 0.dp else 140.dp)
                            .shadow(elevation, MaterialTheme.shapes.large)
                            .longPressDraggableHandle(),
                    )
                }
            }
        }
```

Imports to add (verify exact names against the reorderable 3.1.0 README/sources before finalizing):
`sh.calvin.reorderable.ReorderableItem`, `sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState`, plus `androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState`, `androidx.compose.runtime.mutableStateOf`, `setValue`, `getValue`, `LaunchedEffect`, `androidx.compose.ui.draw.shadow`. The `viewModel` reference: change `HomeScreen` to keep the `viewModel` instance accessible in this scope (it already is — `viewModel.uiState` is collected at the top).

> **API caveat:** reorderable 3.x exposes `longPressDraggableHandle()` within the `ReorderableItem` content scope (`ReorderableCollectionItemScope`). If 3.1.0 renamed/relocated any symbol, consult the bundled README (the Context7 docs for `/calvin-ll/reorderable` show the canonical `rememberReorderableLazyStaggeredGridState` / `ReorderableItem` / `draggableHandle` usage). Do not guess — read the actual artifact.

- [ ] **Step 8: Update the CLAUDE.md tech-stack table**

Add a row under the relevant section of the table in `CLAUDE.md`:

```markdown
| Reorder | sh.calvin.reorderable 3.1.0 (drag-and-drop) |
```

(The project rule requires updating this table whenever `libs.versions.toml` changes.)

- [ ] **Step 9: Build + unit tests**

Run: `./gradlew assembleDebug` then `./gradlew test --tests "com.babytracker.ui.home.*" -PfastTests`
Expected: build succeeds; tests PASS.

- [ ] **Step 10: Manual smoke (device/emulator)** — long-press a tile, drag it, release; relaunch app; order persists. Verify an active feeding/sleep timer keeps counting through a drag (stable key).

- [ ] **Step 11: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts CLAUDE.md \
        app/src/main/java/com/babytracker/ui/home/HomeScreen.kt \
        app/src/test/java/com/babytracker/ui/home/HomeViewModelTileOrderTest.kt
git commit -m "feat(home): long-press drag-and-drop tile reordering"
```

---

## Task 4 — PR 4: Accessibility, reset-to-default, haptics

**Branch:** `feat/home-tile-reorder-a11y`

**Goal:** Drag is invisible to TalkBack — add semantic "Move up/down" actions; add a reset-to-default-order affordance; add haptic feedback on drag.

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt` (+Impl) — optional `clearHomeTileOrder`
- Test: `app/src/test/java/com/babytracker/ui/home/HomeViewModelTileOrderTest.kt`

- [ ] **Step 1: Add reset to the repository (failing test first)**

Add to `SettingsRepositoryHomeTileTest.kt`:

```kotlin
    @Test
    fun `clear resets to default order`() = runTest {
        repository.setHomeTileOrder(listOf(HomeTile.PARTNER) + HomeTile.DEFAULT_ORDER.filter { it != HomeTile.PARTNER })
        repository.clearHomeTileOrder()
        assertEquals(HomeTile.DEFAULT_ORDER, repository.getHomeTileOrder().first())
    }
```

Run: `./gradlew test --tests "com.babytracker.data.repository.SettingsRepositoryHomeTileTest" -PfastTests` → FAIL (`clearHomeTileOrder` unresolved).

- [ ] **Step 2: Implement `clearHomeTileOrder`**

Interface:

```kotlin
    suspend fun clearHomeTileOrder()
```

Impl:

```kotlin
    override suspend fun clearHomeTileOrder() {
        dataStore.edit { it.remove(HOME_TILE_ORDER) }
    }
```

VM handler:

```kotlin
    fun onResetTileOrder() {
        viewModelScope.launch { runCatching { settingsRepository.clearHomeTileOrder() } }
    }
```

Run the repo test again → PASS.

- [ ] **Step 3: Add semantic move actions to each tile**

Wrap `HomeTileContent`'s `modifier` in the grid with `customActions` so TalkBack users can reorder. Inside the `items` block compute neighbor keys from `visibleTiles`:

```kotlin
                    val index = visibleTiles.indexOf(tile)
                    val moveActions = buildList {
                        if (index > 0) add(
                            CustomAccessibilityAction("Move up") {
                                viewModel.onTilesReordered(reorderByKey(orderState, tile, visibleTiles[index - 1]))
                                true
                            },
                        )
                        if (index < visibleTiles.lastIndex) add(
                            CustomAccessibilityAction("Move down") {
                                viewModel.onTilesReordered(reorderByKey(orderState, tile, visibleTiles[index + 1]))
                                true
                            },
                        )
                    }
```

Apply `.semantics { customActions = moveActions }` to the tile modifier (merge with existing). Add imports `androidx.compose.ui.semantics.CustomAccessibilityAction`, `androidx.compose.ui.semantics.customActions`, `androidx.compose.ui.semantics.semantics`.

- [ ] **Step 4: Add haptics on drag start/stop**

Capture `val haptics = LocalHapticFeedback.current`. In `longPressDraggableHandle(onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }, onDragStopped = { ... })`. Match the reorderable README's haptic example for the exact callback signatures in 3.1.0.

- [ ] **Step 5: Add a reset affordance**

Add an overflow action to the `TopAppBar` (a `MoreVert` `IconButton` → `DropdownMenu` with "Reset layout" → `viewModel.onResetTileOrder()`), OR a `TextButton` shown only when `orderState != HomeTile.DEFAULT_ORDER`. Keep it minimal and one-handed-reachable per the app's UX rules.

- [ ] **Step 6: Build + full unit suite**

Run: `./gradlew test -PfastTests`
Then before commit: `./gradlew build`
Expected: green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/home/ \
        app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt \
        app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt \
        app/src/test/java/com/babytracker/
git commit -m "feat(home): accessible tile reorder, reset-to-default, haptics"
```

---

## Self-Review Notes

- **Spec coverage:** "move ANY tile" → all 9 tiles modelled incl. conditional Tip/Partner/SleepPrediction (Task 1 enum, Task 2 `isVisible`). "lib OK" → reorderable 3.1.0 (Task 3). "staggered grid" + "long-press" decisions honored (Task 2/3). Persistence (Task 1). Each issue = own PR/branch (4 branches).
- **Conditional tiles:** visibility moved from `AnimatedVisibility` into `isVisible`; hidden tiles retain their slot because reorder is key-based on the full `orderState`, not the visible sublist.
- **Timers:** stable `key = it.name` on grid items preserves `produceState` elapsed counters across reorder.
- **Open verifications for the implementer:** (1) exact reorderable 3.1.0 symbol names/signatures — read the artifact, don't guess; (2) the module's Robolectric+JUnit5 DataStore test harness — copy an existing repository test's setup; (3) tablet `widthIn(max=600.dp)` centering dropped — restore via a wrapping `Box` only if a reviewer wants it.
- **Per-commit gate:** run adversarial review before each commit per project workflow (max 2 rounds/commit).
