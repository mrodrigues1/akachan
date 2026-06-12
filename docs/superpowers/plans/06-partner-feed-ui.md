# Plan 06: Partner feed UI — log bottle, history, badge, gating

LINEAR_ISSUE: [AKA-122](https://linear.app/akachan/issue/AKA-122/spec-007-plan-06-partner-feed-ui-log-bottle-history-badge-gating)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. When building the screens, also invoke the `mobile-android-design` skill (project rule for screen work).

**Goal:** Partner dashboard gains "Log bottle" and "Feeding history"; the partner logs/edits/deletes bottle feeds through the same sheet flow the primary has (stash picker included); partner-authored entries show a "Partner" badge on **both** devices; edit/delete affordances appear only on `author == PARTNER` entries in partner mode (SPEC-007 "UI").

**Architecture:** Reuse over extraction: `BottleFeedSheet` is **already stateless** (it binds to `BottleFeedUiState` + callbacks, not to the ViewModel — the spec's "extract stateless content composables" is already satisfied; verify, don't re-extract). The partner side adds `PartnerBottleFeedViewModel` + `PartnerFeedHistoryViewModel` (no BaseViewModel — anti-goal) that feed the existing composables: snapshot `MilkBagSnapshot`s are adapted to the picker's `MilkBag` model, snapshot feed entries to `BottleFeed` for the shared history card.

**Tech Stack:** Jetpack Compose + Material 3, Hilt, Compose Navigation, JUnit 5 + MockK + Turbine.

**Spec:** `specs/SPEC-007-PARTNER-BOTTLE-FEED-LOGGING.md` — "UI", "Error handling", "Testing → ViewModel gating".

**Dependencies:** Plan 05 (partner use cases + merge), Plan 01 (`author` on `BottleFeed`), Plan 02 (`milkBags` in snapshot).

**Suggested branch:** `feat/partner-feed-ui`

---

## File Structure

- Create: `app/src/main/java/com/babytracker/ui/partner/PartnerBottleFeedViewModel.kt`
- Create: `app/src/main/java/com/babytracker/ui/partner/PartnerFeedHistoryViewModel.kt`
- Create: `app/src/main/java/com/babytracker/ui/partner/PartnerFeedHistoryScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedViewModel.kt` — add `isEditing` to `BottleFeedUiState`.
- Modify: `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedSheet.kt` — title keys off `state.isEditing` (not `editingId`).
- Modify: `app/src/main/java/com/babytracker/ui/feeding/UnifiedFeedingHistoryScreen.kt` — `BottleFeedHistoryCard` renders Partner badge + gating param.
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt` — two new actions.
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt` + `AppNavGraph.kt` — `PARTNER_FEED_HISTORY`.
- Modify: `app/src/main/res/values/strings.xml` — new strings.
- Test: `PartnerBottleFeedViewModelTest.kt`, `PartnerFeedHistoryViewModelTest.kt` (new, `app/src/test/java/com/babytracker/ui/partner/`); extend `FeedingHistoryViewModelTest` only if gating logic lands there.

---

### Task 1: Shared sheet groundwork (`isEditing`)

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/bottlefeed/BottleFeedSheet.kt`

The sheet's add/edit title currently derives from `state.editingId: Long?` — a Room id the partner doesn't have (its identities are `clientId` strings).

- [ ] **Step 1:** Add `val isEditing: Boolean = false` to `BottleFeedUiState`. In `BottleFeedViewModel`, set `isEditing = true` wherever `editingId` is set (search the file for `editingId =` assignments) and reset together.
- [ ] **Step 2:** In `BottleFeedSheet`, switch the title condition from `state.editingId == null` to `!state.isEditing`.
- [ ] **Step 3:** Run existing tests: `./gradlew test --tests "com.babytracker.ui.bottlefeed.*"` → PASS.

### Task 2: Shared history card — badge + gating

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/feeding/UnifiedFeedingHistoryScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1:** Extend `BottleFeedHistoryCard` (it already receives `feed: BottleFeed`, which carries `author` after Plan 01):

```kotlin
@Composable
internal fun BottleFeedHistoryCard(
    feed: BottleFeed,
    volumeUnit: VolumeUnit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    editable: Boolean = true,
) {
    HistoryCard(
        title = feed.type.historyLabel(),
        subtitle = buildString {
            append(feed.timestamp.formatTime12h())
            if (feed.linkedMilkBagId != null) append(" · from stash")
            if (feed.author == FeedAuthor.PARTNER) {
                append(" · ").append(stringResource(R.string.feed_author_partner_badge))
            }
        },
        trailing = formatVolume(feed.volumeMl, volumeUnit),
        badgeEmoji = "🍼",
        badgeColor = MaterialTheme.colorScheme.primaryContainer,
        onClick = if (editable) onEdit else ({}),
        trailingContent = {
            if (editable) BottleFeedOverflowMenu(onEdit = onEdit, onDelete = onDelete)
        },
    )
}
```

Check `HistoryCard`'s actual `onClick` nullability at implementation time — if it accepts `(() -> Unit)?`, pass `null` instead of `{}` for the non-editable case. If a chip-style badge fits `HistoryCard`'s layout better than the subtitle suffix, prefer the chip (use the `mobile-android-design` skill when judging) — the contract is only: PARTNER entries visibly labeled "Partner", OWNER entries unlabeled.

- [ ] **Step 2:** strings.xml additions:

```xml
<string name="feed_author_partner_badge">Partner</string>
<string name="partner_log_bottle">Log bottle</string>
<string name="partner_feeding_history">Feeding history</string>
```

- [ ] **Step 3:** Primary call sites pass nothing new (`editable` defaults true; badge comes from `feed.author`) — primary side is done. The primary may edit/delete partner entries (spec: "the primary can edit/delete everything"), so the primary never passes `editable = false`.

### Task 3: `PartnerBottleFeedViewModel`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/partner/PartnerBottleFeedViewModel.kt`

- [ ] **Step 1:** Reuses `BottleFeedUiState` so `BottleFeedSheet` renders unchanged; bags come from the snapshot:

```kotlin
package com.babytracker.ui.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.usecase.EditPartnerFeedUseCase
import com.babytracker.sharing.usecase.LogPartnerFeedUseCase
import com.babytracker.ui.bottlefeed.BottleFeedUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class PartnerBottleFeedViewModel @Inject constructor(
    private val logPartnerFeed: LogPartnerFeedUseCase,
    private val editPartnerFeed: EditPartnerFeedUseCase,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BottleFeedUiState(timestamp = now()))
    val uiState: StateFlow<BottleFeedUiState> = _uiState.asStateFlow()

    private var availableBags: List<MilkBagSnapshot> = emptyList()
    private var editingClientId: String? = null
    private var editingEntry: BottleFeedSnapshot? = null

    init {
        viewModelScope.launch {
            settingsRepository.getVolumeUnit().collect { unit -> _uiState.update { it.copy(volumeUnit = unit) } }
        }
    }

    /** Called by the screen with the latest fetched snapshot's active bags. */
    fun onBagsAvailable(bags: List<MilkBagSnapshot>) {
        availableBags = bags
        _uiState.update { it.copy(activeBags = bags.map { bag -> bag.toPickerBag() }) }
    }

    fun startLogging() {
        editingClientId = null
        editingEntry = null
        _uiState.update {
            BottleFeedUiState(
                timestamp = now(),
                activeBags = it.activeBags,
                volumeUnit = it.volumeUnit,
            )
        }
    }

    fun startEditing(entry: BottleFeedSnapshot) {
        check(entry.author == "PARTNER") { "Only partner entries are editable in partner mode" }
        editingClientId = entry.clientId
        editingEntry = entry
        _uiState.update {
            it.copy(
                feedType = FeedType.entries.firstOrNull { t -> t.name == entry.type } ?: FeedType.FORMULA,
                volumeText = entry.volumeMl.toString(),
                timestamp = Instant.ofEpochMilli(entry.timestamp),
                selectedBagId = null, // consumedBagId is create-only (spec): no bag editing
                notes = entry.notes.orEmpty(),
                isEditing = true,
                validationError = null,
                saved = false,
            )
        }
    }

    // onTypeChange/onVolumeChange/onTimeChange/onBagSelect/onNotesChange: same shape as
    // BottleFeedViewModel — copy those five handlers verbatim (they only touch _uiState).

    fun onConfirm() {
        val state = _uiState.value
        val volume = state.volumeText.toIntOrNull()
        if (volume == null || volume <= 0) {
            _uiState.update { it.copy(validationError = "Enter a volume greater than 0") }
            return
        }
        _uiState.update { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            runCatching {
                val editing = editingEntry
                if (editing == null) {
                    logPartnerFeed(
                        timestamp = state.timestamp,
                        volumeMl = volume,
                        type = state.feedType,
                        selectedBag = availableBags.firstOrNull { it.id == state.selectedBagId },
                        notes = state.notes.ifBlank { null },
                    )
                } else {
                    editPartnerFeed(
                        entry = editing,
                        timestamp = state.timestamp,
                        volumeMl = volume,
                        type = state.feedType,
                        notes = state.notes.ifBlank { null },
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, validationError = e.message) }
            }
        }
    }
}

private fun MilkBagSnapshot.toPickerBag(): MilkBag = MilkBag(
    id = id,
    collectionDate = Instant.ofEpochMilli(collectionDateMs),
    volumeMl = volumeMl,
    notes = notes,
    createdAt = Instant.EPOCH, // display-only adapter; createdAt unused by the picker
)
```

When editing, the stash picker is suppressed because `startEditing` clears `selectedBagId` and editing partner entries never sends `consumedBagId` (Plan 05 use case has no bag param). Hide the `BagPicker` while `isEditing` (one condition in the screen wrapper, or pass `activeBags = emptyList()` into the sheet state during edit — pick the simpler at implementation).

### Task 4: `PartnerFeedHistoryViewModel` + screen + navigation

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/partner/PartnerFeedHistoryViewModel.kt`
- Create: `app/src/main/java/com/babytracker/ui/partner/PartnerFeedHistoryScreen.kt`
- Modify: `Routes.kt`, `AppNavGraph.kt`, `PartnerDashboardScreen.kt`

- [ ] **Step 1: ViewModel** — merged history, snapshot re-fetch on pending-op shrink (Plan 05's documented obligation), revoke handling:

```kotlin
data class PartnerFeedHistoryUiState(
    val entries: List<BottleFeedSnapshot> = emptyList(),
    val milkBags: List<MilkBagSnapshot> = emptyList(),
    val isLoading: Boolean = true,
    val accessRevoked: Boolean = false,
)

@HiltViewModel
class PartnerFeedHistoryViewModel @Inject constructor(
    private val fetchPartnerData: FetchPartnerDataUseCase,
    private val observePartnerFeedHistory: ObservePartnerFeedHistoryUseCase,
    private val logPartnerFeedDelete: DeletePartnerFeedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerFeedHistoryUiState())
    val uiState: StateFlow<PartnerFeedHistoryUiState> = _uiState.asStateFlow()

    private var lastPendingOpCount = 0

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val snapshot = fetchPartnerData()
                _uiState.update { it.copy(milkBags = snapshot.milkBags, isLoading = false) }
                observePartnerFeedHistory(snapshot.bottleFeeds).collect { merged ->
                    // Plan 05 contract: when the pending set shrinks the primary consumed
                    // ops and already pushed the corrected snapshot — re-fetch to avoid
                    // the entry flickering back to its pre-op state.
                    val pendingNow = merged.pendingOpCount // see note below
                    _uiState.update { it.copy(entries = merged.entries, isLoading = false) }
                    if (pendingNow < lastPendingOpCount) refresh()
                    lastPendingOpCount = pendingNow
                }
            }.onFailure { e ->
                if (e is PartnerAccessRevokedException || e.isPermissionDenied()) {
                    _uiState.update { it.copy(accessRevoked = true) }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun onDelete(entry: BottleFeedSnapshot) {
        viewModelScope.launch { runCatching { logPartnerFeedDelete(entry) } }
    }

    /** Gating: only PARTNER entries expose edit/delete (spec). */
    fun isEditable(entry: BottleFeedSnapshot): Boolean =
        entry.author == FeedAuthor.PARTNER.name && entry.clientId.isNotEmpty()
}
```

Implementation notes (resolve while coding, keep the contract):
- `merged.pendingOpCount`: have `ObservePartnerFeedHistoryUseCase` emit a small result type `data class MergedFeedHistory(val entries: List<BottleFeedSnapshot>, val pendingOpCount: Int)` instead of a bare list — adjust Plan 05's use case signature accordingly (the merge function itself stays pure).
- `isPermissionDenied()`: small private extension checking `FirebaseFirestoreException.Code.PERMISSION_DENIED` anywhere in the cause chain (the own-ops listener errors with it after revocation).
- The recursive `refresh()` is bounded: it only recurses when ops were consumed, and each consume strictly shrinks the pending set.
- On `accessRevoked`, the screen navigates back to the dashboard, which already owns the established revoke flow (clear state + exit) — do not duplicate the clearing logic here; `FetchPartnerDataUseCase` already cleared settings.

- [ ] **Step 2: Screen** — list reusing the shared card via snapshot→domain adapter:

```kotlin
private fun BottleFeedSnapshot.toDisplayFeed(): BottleFeed = BottleFeed(
    clientId = clientId,
    timestamp = Instant.ofEpochMilli(timestamp),
    volumeMl = volumeMl,
    type = FeedType.entries.firstOrNull { it.name == type } ?: FeedType.FORMULA,
    notes = notes,
    createdAt = Instant.EPOCH, // display-only
    author = if (author == "PARTNER") FeedAuthor.PARTNER else FeedAuthor.OWNER,
)
```

`PartnerFeedHistoryScreen` structure (follow `UnifiedFeedingHistoryScreen`'s scaffold/topbar/list patterns and `PartnerDashboardScreen`'s look):
- `LazyColumn` of `BottleFeedHistoryCard(feed = entry.toDisplayFeed(), editable = viewModel.isEditable(entry), ...)`
- onEdit → open `BottleFeedSheet` driven by `PartnerBottleFeedViewModel.startEditing(entry)`; onDelete → confirmation dialog (reuse `FeedingDeleteConfirmationDialog`) → `viewModel.onDelete(entry)`
- FAB or top action "Log bottle" → `startLogging()` + sheet
- `LaunchedEffect(uiState.accessRevoked) { if (it) onNavigateBack() }`
- Empty/loading states mirroring the existing history screen.

- [ ] **Step 3: Navigation + dashboard entry points**

`Routes.kt`:

```kotlin
const val PARTNER_FEED_HISTORY = "partner_feed_history"
```

`AppNavGraph.kt` — alongside the existing `PARTNER_DASHBOARD` composable:

```kotlin
composable(Routes.PARTNER_FEED_HISTORY) {
    PartnerFeedHistoryScreen(
        onNavigateBack = { navController.popBackStack() },
    )
}
```

`PartnerDashboardScreen` gains `onNavigateToFeedHistory: () -> Unit` (wired in the nav graph to `navController.navigate(Routes.PARTNER_FEED_HISTORY)`) plus a "Log bottle" quick action that opens the sheet in place (dashboard hosts `PartnerBottleFeedViewModel` + `BottleFeedSheet`, passing `snapshot.milkBags` via `onBagsAvailable`). Place both following the dashboard's existing action/card patterns.

### Task 5: Tests

**Files:**
- Create: `app/src/test/java/com/babytracker/ui/partner/PartnerBottleFeedViewModelTest.kt`
- Create: `app/src/test/java/com/babytracker/ui/partner/PartnerFeedHistoryViewModelTest.kt`

- [ ] **Step 1: `PartnerBottleFeedViewModelTest`** (JUnit 5 + MockK + Turbine, mirror `BottleFeedViewModelTest` setup):
  - confirm in log mode → `LogPartnerFeedUseCase` invoked with selected bag snapshot resolved from `selectedBagId`
  - confirm in edit mode → `EditPartnerFeedUseCase` invoked with original entry; no bag argument exists
  - `startEditing` on OWNER entry → throws (gating, defense-in-depth)
  - invalid volume → `validationError` set, no use case call
  - use case failure → `isSaving = false`, error surfaced
  - `startLogging` after edit resets `isEditing`/fields, keeps bags + unit

- [ ] **Step 2: `PartnerFeedHistoryViewModelTest`**:
  - merged entries from `ObservePartnerFeedHistoryUseCase` land in state
  - `isEditable` true only for PARTNER + non-empty clientId (OWNER false, legacy empty-clientId false) — the spec's "ViewModel gating" test
  - pending-op count shrink triggers snapshot re-fetch (verify `fetchPartnerData` called twice)
  - `PartnerAccessRevokedException` → `accessRevoked = true`
  - listener `PERMISSION_DENIED` (wrapped `FirebaseFirestoreException`) → `accessRevoked = true`
  - delete delegates to `DeletePartnerFeedUseCase`

- [ ] **Step 3: Run**

Run: `./gradlew test --tests "com.babytracker.ui.partner.*" --tests "com.babytracker.ui.bottlefeed.*"`
Expected: PASS

### Task 6: Full validation + commit

- [ ] **Step 1:** `./gradlew test` → PASS; `./gradlew build` → PASS (resources + navigation changed).
- [ ] **Step 2: Manual smoke (two devices or two emulators):** partner logs a feed with a stash bag → appears on primary with Partner badge, bag consumed; partner edits/deletes own entry; OWNER entries read-only on partner; primary edits partner entry → badge persists.
- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(partner): bottle feed logging, history, and attribution UI"
```

---

## Acceptance Criteria

- [ ] Partner dashboard: "Log bottle" action + "Feeding history" entry point (`PARTNER_FEED_HISTORY` route).
- [ ] Same sheet flow as primary, including stash picker fed from `snapshot.milkBags` (volume prefilled on bag select — existing sheet behavior); bag selection absent when editing.
- [ ] Edit/delete affordances only on `author == PARTNER` entries; OWNER entries render read-only on the partner device; primary keeps full edit/delete on everything.
- [ ] "Partner" badge on PARTNER entries in both apps' history (shared `BottleFeedHistoryCard`); OWNER entries unlabeled.
- [ ] Optimistic UI: pending ops appear immediately; snapshot re-fetch on pending-op consumption (no flicker regression test at ViewModel level).
- [ ] Revoked access exits to dashboard flow via existing `PartnerAccessRevokedException` machinery.
- [ ] No BaseViewModel; ViewModels expose single `StateFlow<*UiState>` per project conventions.
- [ ] All listed unit tests green; full suite + build green.
