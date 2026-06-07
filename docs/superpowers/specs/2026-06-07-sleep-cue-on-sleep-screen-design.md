# Sleep Cue Row on Sleep Screen — Design

**Date:** 2026-06-07
**Scope:** `ui/component/CueQuickTapRow.kt` (new) · `ui/home/HomeScreen.kt` · `ui/sleep/SleepViewModel.kt` · `ui/sleep/SleepTrackingScreen.kt`

---

## Problem

`CueQuickTapRow` (😪 Sleepy, 😋 Hungry, 😣 Fussy, 🤒 Sick, 🦷 Teething, ✈️ Travel) is only accessible from the home screen, below `SleepPredictionCard`. Cues are a sleep feature — they log `BabyEvent` records that feed `PredictSleepWindowUseCase` and lower prediction confidence during disruptions. A parent on the sleep screen has no way to log a cue without navigating away.

## Solution

Extract `CueQuickTapRow` to a shared component and wire it into `SleepTrackingScreen` below `SleepRecommendationSection`. Inject `LogBabyEventUseCase` into `SleepViewModel` and add an `onCueTapped` handler.

---

## Architecture

Three layers of change:

1. **New component file** — `ui/component/CueQuickTapRow.kt` owns the composable and `BabyEventType` UI extensions (`emoji`, `label`). Public visibility (matching `HistoryCard` and other components in that package). Single source of truth; both screens import from here.

2. **SleepViewModel** — gains `LogBabyEventUseCase` constructor injection and `onCueTapped(BabyEventType)` handler. Structure mirrors `HomeViewModel.onCueTapped` exactly.

3. **Screen wiring** — `SleepTrackingScreen` adds one `LazyColumn` item immediately after `SleepRecommendationSection`. `HomeScreen` drops its private copies and imports from the component.

---

## Component: `CueQuickTapRow`

Behaviour is unchanged from the existing `HomeScreen.kt` implementation:

- `FilterChip` per `BabyEventType` entry, horizontally scrollable `Row`
- `tappedCues: SnapshotStateSet<BabyEventType>` tracks recently-tapped chips locally
- Per-tap: add to set → `onCueTapped(type)` fires → coroutine delays 1 200 ms → removes from set
- Scale animation via `animateFloatAsState` with spring (`DampingRatioMediumBouncy`): 1.08f when selected, 1.0f otherwise
- `removalJobs: HashMap<BabyEventType, Job>` cancels any in-flight removal before starting a new one (prevents race on rapid re-tap)

### Signature

```kotlin
@Composable
fun CueQuickTapRow(  // public — matches ui/component/ conventions
    onCueTapped: (BabyEventType) -> Unit,
    modifier: Modifier = Modifier,
)
```

### Extension properties (file-private in `CueQuickTapRow.kt`)

```kotlin
private val BabyEventType.emoji: String
private val BabyEventType.label: String
```

These are UI-layer concerns and must not be added to the domain model.

---

## SleepViewModel Changes

```kotlin
@HiltViewModel
class SleepViewModel @Inject constructor(
    // existing deps …
    private val logBabyEvent: LogBabyEventUseCase,   // new
) : ViewModel() {

    fun onCueTapped(type: BabyEventType) {
        viewModelScope.launch { runCatching { logBabyEvent(type) } }
    }
}
```

`runCatching` swallows DB errors silently — a failed tap must not crash the UI. No `UiState` change needed; cue logging is fire-and-forget.

---

## SleepTrackingScreen Changes

In the `LazyColumn`, add one item immediately after the existing `SleepRecommendationSection` item:

```kotlin
item {
    SleepRecommendationSection(
        state = uiState.sleepPrediction,
        schedule = uiState.schedule,
    )
}
item {
    CueQuickTapRow(onCueTapped = viewModel::onCueTapped)
}
```

No conditional rendering — cues are always accessible, matching home screen behaviour.

---

## HomeScreen Changes

Remove private extension props (`BabyEventType.emoji`, `BabyEventType.label`) and private `CueQuickTapRow` composable. Add import from the new component file. Wire site unchanged:

```kotlin
CueQuickTapRow(onCueTapped = viewModel::onCueTapped)
```

---

## Files

| File | Action | Change |
|------|--------|--------|
| `ui/component/CueQuickTapRow.kt` | Create | Composable + private emoji/label extensions |
| `ui/home/HomeScreen.kt` | Modify | Remove private copies; import from component |
| `ui/sleep/SleepViewModel.kt` | Modify | Inject `LogBabyEventUseCase`; add `onCueTapped` |
| `ui/sleep/SleepTrackingScreen.kt` | Modify | Add `CueQuickTapRow` item after `SleepRecommendationSection` |

---

## Testing

**No new test file for `CueQuickTapRow` component** — `HomeScreenTest` already exercises chip tap, selected state, and 6-chip presence. Those tests continue passing after extraction because `HomeScreen` renders the same composable from its new location.

**`SleepTrackingScreenTest`** — add one test asserting all 6 cue chips are present on the sleep screen. No ViewModel interaction test needed; `SleepViewModel.onCueTapped` is structurally identical to `HomeViewModel.onCueTapped`, which is already covered.

---

## Non-Goals

- No section label ("CUES") above the row
- No conditional rendering based on prediction state
- No haptics
- No UiState changes in `SleepViewModel`
- No changes to `HomeViewModel` or prediction logic
