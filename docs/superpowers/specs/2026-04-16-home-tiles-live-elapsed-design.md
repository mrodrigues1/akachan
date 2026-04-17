# Home Tiles — Live Elapsed Time & Size Bump — Design Spec

**Date:** 2026-04-16
**Status:** Approved

---

## Overview

Two UI changes to the home screen:

1. Increase the visual size of both main summary tiles (Breastfeeding, Sleep).
2. Add a live, minute-resolution elapsed-time display to the Breastfeeding tile, showing the time since the most recent session started (whether active or completed).

Tile shape, colors, and navigation behavior are unchanged.

---

## Goals

- Tiles feel more spacious and visually prominent.
- Breastfeeding tile shows a live "time since last feed started" value that updates every minute without manual refresh.
- Display rules match parent expectations: active session → live counter; no active but has history → time since last feed; no history → show nothing.

## Non-Goals

- No changes to tile shape, color, elevation, or click/navigation behavior.
- No seconds-level ticker (minute resolution only).
- No changes to the Sleep tile's body content — only its size changes.
- No changes to the "active session" banner that sits above the tiles.

---

## Data Layer

No repository, DAO, or schema changes. The data needed is already emitted by `GetBreastfeedingHistoryUseCase()` — the full session history in reverse-chronological order.

---

## Domain / ViewModel Layer

### `HomeUiState`

Add one nullable field:

```kotlin
val lastSessionStartTime: Instant? = null
```

Semantics:

- `null` — no breastfeeding sessions exist (first-time user).
- non-null — the `startTime` of the most recent session (active or completed). The UI decides how to render elapsed time from this value plus "now".

### `HomeViewModel`

In the `combine { ... }` block, compute:

```kotlin
val lastSessionStartTime = feedings.firstOrNull()?.startTime
```

`feedings` is already reverse-chronological, so `firstOrNull()` is the most recent (active if one is in progress, otherwise the most recent completed one — matches both display rules with one field).

---

## UI Layer

### Tile size bump (both tiles)

Applied identically to the Breastfeeding and Sleep `Card`s in `HomeScreen.kt`:

| Property | Before | After |
|----------|--------|-------|
| Internal padding | `16.dp` | `20.dp` |
| Minimum height | (none) | `heightIn(min = 140.dp)` |
| Emoji style | `titleLarge` | `headlineMedium` |
| Title style | `titleSmall` | `titleMedium` |
| Spacer under emoji | `8.dp` | `12.dp` |

Shape (`MaterialTheme.shapes.large`), colors, elevation, and `onClick` handlers are untouched.

### Live elapsed-time display

Inside `HomeScreen`, when `uiState.lastSessionStartTime != null`:

```kotlin
val now by produceState(
    initialValue = Instant.now(),
    key1 = uiState.lastSessionStartTime
) {
    while (true) {
        value = Instant.now()
        kotlinx.coroutines.delay(60_000L)
    }
}
val elapsedLabel = Duration
    .between(uiState.lastSessionStartTime, now)
    .formatElapsedShort()
```

The current `"${uiState.sessionsTodayCount} sessions today"` line is **replaced** by `elapsedLabel`. When `lastSessionStartTime == null`, no text is shown in its place (matches current first-time-user behavior).

Keying `produceState` on `lastSessionStartTime` ensures the ticker resets whenever a new session starts or the underlying session changes, and the first emission is always "now" so the display updates immediately on state change.

### Utility

Add to `util/DateTimeExt.kt`:

```kotlin
fun Duration.formatElapsedShort(): String {
    if (isNegative || isZero) return "0m"
    val hours = toHours()
    val minutes = (toMinutes() % 60).toInt()
    return if (hours > 0) "${hours}h ${"%02d".format(minutes)}m" else "${minutes}m"
}
```

Distinct from the existing `formatElapsedAgo()` (which appends "ago" and prints "Just now" for zero-duration). The "Xh Ym" form uses a zero-padded minutes component so "1h 04m" reads evenly; the "Ym" form does not pad.

---

## Testing

### Unit tests

**`HomeViewModelTest`** — add cases:

- `lastSessionStartTime` is null when history is empty.
- `lastSessionStartTime` equals the start time of an active session when one exists.
- `lastSessionStartTime` equals the start time of the most recent completed session when no active session exists.

**`DateTimeExtTest`** — add cases for `formatElapsedShort()`:

- `Duration.ofMinutes(0)` → `"0m"`
- `Duration.ofMinutes(-5)` → `"0m"`
- `Duration.ofMinutes(43)` → `"43m"`
- `Duration.ofMinutes(64)` → `"1h 04m"`
- `Duration.ofHours(2)` → `"2h 00m"`

### UI tests

No new instrumentation test. The `produceState` ticker is a thin Compose wrapper over the state field already exercised by the ViewModel test, and the tile layout is visual-only.

---

## Files Touched

- `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt` — add field, compute value.
- `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt` — tile size, live ticker, swap body text.
- `app/src/main/java/com/babytracker/util/DateTimeExt.kt` — add `formatElapsedShort()`.
- `app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt` — new assertions.
- `app/src/test/java/com/babytracker/util/DateTimeExtTest.kt` — new assertions (create file if absent).
