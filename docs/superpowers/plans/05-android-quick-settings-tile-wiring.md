LINEAR_ISSUE: AKA-64

# Android Quick Settings Tile Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Register two Android Quick Settings tiles, Feed and Sleep, and wire them to render state, lockscreen policy, silent toggles, app routing, and long-press preferences routing.

**Architecture:** Add two thin `TileService` subclasses that obtain app dependencies through a Hilt `@EntryPoint`, resolve render state on `onStartListening`, and delegate tap behavior to `TileToggleHandler` only when `shouldOpenAppOnTap` returns `TOGGLE_SILENTLY`. Android framework code stays in the services; all decisions remain in helpers from Plans 3 and 4. Manifest entries expose each tile with dedicated labels/icons and route long-press/preferences into `MainActivity` with the existing navigation extra.

**Tech Stack:** Android `TileService` API 26+, API-gated `Tile.subtitle` on 29+, Hilt entry points, Kotlin Coroutines, XML manifest/drawables, JUnit 5 helper tests, optional Robolectric smoke tests.

---

## Dependencies

- Depends on Plan 3 / `AKA-62` for `TileStateResolver`, `TileTapPolicy`, and render models.
- Depends on Plan 4 / `AKA-63` for `TileToggleHandler`.

## Suggested Branch

`feat/android-quick-settings-tiles`

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/src/main/java/com/babytracker/tile/TileEntryPoint.kt` | Hilt entry point for non-Hilt `TileService` classes. | Create |
| `app/src/main/java/com/babytracker/tile/BaseBabyTileService.kt` | Shared service scope, entry-point lookup, rendering, tap routing. | Create |
| `app/src/main/java/com/babytracker/tile/FeedTileService.kt` | Feed tile-specific resolver/toggle/open-app target. | Create |
| `app/src/main/java/com/babytracker/tile/SleepTileService.kt` | Sleep tile-specific resolver/toggle/open-app target. | Create |
| `app/src/main/java/com/babytracker/tile/TileIntents.kt` | MainActivity intents for feed, sleep, onboarding/home fallback. | Create |
| `app/src/main/res/drawable/ic_qs_feed.xml` | Monochrome feed tile icon. | Create |
| `app/src/main/res/drawable/ic_qs_sleep.xml` | Monochrome sleep tile icon. | Create |
| `app/src/main/res/values/strings.xml` | Tile labels and descriptions. | Modify |
| `app/src/main/AndroidManifest.xml` | Add two `TileService` declarations and preferences activity intent filters if required by API verification. | Modify |
| `app/src/test/java/com/babytracker/tile/TileIntentsTest.kt` | Verify navigation extras and target routes. | Create |
| `app/src/test/java/com/babytracker/tile/TileRenderMapperTest.kt` | Verify tile state/subtitle mapping logic without Android service lifecycle. | Create |

## Implementation Tasks

### Task 1: Add tile strings and icons

- [ ] Add strings:

```xml
<string name="tile_feed_label">Feed</string>
<string name="tile_sleep_label">Sleep</string>
<string name="tile_feed_description">Start or stop a feeding session</string>
<string name="tile_sleep_description">Start or stop a sleep session</string>
```

- [ ] Create `ic_qs_feed.xml` and `ic_qs_sleep.xml` as simple white vector drawables with `android:tint="?attr/colorControlNormal"` omitted; Quick Settings applies its own tint.
- [ ] Keep icons monochrome and legible at 24dp.

### Task 2: Add tile entry point

- [ ] Create `TileEntryPoint.kt`:

```kotlin
package com.babytracker.tile

import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TileEntryPoint {
    fun babyRepository(): BabyRepository
    fun settingsRepository(): SettingsRepository
    fun breastfeedingRepository(): BreastfeedingRepository
    fun sleepRepository(): SleepRepository
    fun tileToggleHandler(): TileToggleHandler
    fun clock(): Clock
}
```

- [ ] Do not reuse `WidgetEntryPoint`; keep tile dependencies explicit so widget changes do not accidentally affect tile services.

### Task 3: Add intent helpers

- [ ] Create `TileIntents.kt`:

```kotlin
package com.babytracker.tile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.babytracker.MainActivity
import com.babytracker.navigation.Routes
import com.babytracker.util.NotificationHelper

fun tileMainActivityIntent(context: Context, route: String): Intent =
    Intent().apply {
        component = ComponentName(context.packageName, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(NotificationHelper.EXTRA_NAV_ROUTE, route)
    }

fun feedTileIntent(context: Context): Intent =
    tileMainActivityIntent(context, Routes.BREASTFEEDING)

fun sleepTileIntent(context: Context): Intent =
    tileMainActivityIntent(context, Routes.SLEEP_TRACKING)

fun unavailableTileIntent(context: Context): Intent =
    Intent().apply {
        component = ComponentName(context.packageName, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
```

- [ ] Add `TileIntentsTest` verifying component package/class, flags, feed/sleep route extras, and that `unavailableTileIntent` has no feature route extra. No-route launch lets `AppNavGraph` choose the guarded start destination for onboarding or partner mode.

### Task 4: Add render mapper

- [ ] Add a small mapper function, either in `BaseBabyTileService.kt` or `TileRenderMapper.kt`, that maps:
  - `TileAvailability.ACTIVE` -> `Tile.STATE_ACTIVE`;
  - `INACTIVE` -> `Tile.STATE_INACTIVE`;
  - `UNAVAILABLE` -> `Tile.STATE_UNAVAILABLE`.
- [ ] Add a subtitle helper that returns the resolver subtitle only on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`.
- [ ] Add `TileRenderMapperTest` for availability mapping and subtitle API gating using Robolectric SDK configs or a pure function that accepts `sdkInt`.

### Task 5: Implement base service

- [ ] Create `BaseBabyTileService : TileService`.
- [ ] Create a `SupervisorJob` + `CoroutineScope(Dispatchers.Main.immediate + job)`.
- [ ] Cancel the job in `onDestroy`.
- [ ] In `onStartListening`, launch a coroutine, call subclass `resolveState(entryPoint)`, map it to `qsTile.state`, `qsTile.label`, and API 29+ `qsTile.subtitle`, then call `qsTile.updateTile()`.
- [ ] Wrap render failures by setting `STATE_UNAVAILABLE`, base label, no subtitle, and `updateTile()`.
- [ ] In `onClick`, launch a coroutine:
  - resolve current render state;
  - compute `TileTapAction` using `shouldOpenAppOnTap(isSecureDevice(), isLockedDevice(), state.availability)`;
  - if `OPEN_APP`, call `startActivityAndCollapse(intentForState(state))`;
  - if `TOGGLE_SILENTLY`, call subclass `toggle(entryPoint)`, then re-render from repositories regardless of result so UI reflects actual state.
- [ ] Implement `isLockedDevice()` using `TileService.isLocked`; implement `isSecureDevice()` through `KeyguardManager.isDeviceSecure`.
- [ ] Use the API-appropriate `startActivityAndCollapse` overload available to the app compile SDK; verify on current Android docs during implementation and keep API-gated code if needed.

### Task 6: Implement concrete services

- [ ] `FeedTileService`:
  - base label `Feed`;
  - `resolveState` creates `TileStateResolver` from entry-point dependencies and calls `resolveFeed()`;
  - `toggle` calls `entryPoint.tileToggleHandler().toggleFeed()`;
  - `intentForState` returns `unavailableTileIntent(this)` when `state.availability == UNAVAILABLE`, otherwise `feedTileIntent(this)`.
- [ ] `SleepTileService`:
  - base label `Sleep`;
  - resolver calls `resolveSleep()`;
  - toggle calls `toggleSleep()`;
  - `intentForState` returns `unavailableTileIntent(this)` when `state.availability == UNAVAILABLE`, otherwise `sleepTileIntent(this)`.
- [ ] Add unit tests for the state-aware route selector: active/inactive feed routes to breastfeeding, active/inactive sleep routes to sleep tracking, unavailable feed/sleep route has no feature route extra.

### Task 7: Register manifest services

- [ ] Add two services under `<application>`:

```xml
<service
    android:name=".tile.FeedTileService"
    android:exported="true"
    android:icon="@drawable/ic_qs_feed"
    android:label="@string/tile_feed_label"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
</service>

<service
    android:name=".tile.SleepTileService"
    android:exported="true"
    android:icon="@drawable/ic_qs_sleep"
    android:label="@string/tile_sleep_label"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
</service>
```

- [ ] Verify long-press behavior on Android documentation/current emulator. If the platform requires an activity for `android.service.quicksettings.action.QS_TILE_PREFERENCES`, add the relevant activity intent-filter to `MainActivity` and route by service component extra; otherwise document that long-press falls back to app details/settings on the tested platform and keep single-tap open-app routing as the in-app navigation path.

### Task 8: Add tests and smoke verification

- [ ] Run tile helper tests:

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.tile.*" -PfastTests
```

- [ ] Run manifest/resource compile:

```bash
./gradlew :app:assembleDebug
```

- [ ] Manual emulator smoke checklist:
  - both tiles appear in Quick Settings edit list with correct labels/icons;
  - unlocked tap toggles feed/sleep without launching the app;
  - secure locked tap opens the app behind keyguard and does not mutate before unlock;
  - Android 10+ active tile shows elapsed subtitle on panel open;
  - Android 8/9 tile omits subtitle and still toggles;
  - partner/pre-onboarding state renders unavailable and opens app.

## Acceptance Criteria

- Two Quick Settings tiles are registered and installable.
- `onStartListening` renders active/inactive/unavailable state from repository state.
- Unlocked available taps silently toggle via `TileToggleHandler`.
- Secure locked taps open the app and do not silently mutate data.
- Unavailable taps launch the app without a feature route so onboarding/partner guards choose the correct destination.
- Active elapsed subtitle appears only on API 29+.
- Manifest/resource build succeeds and tile helper tests pass.
