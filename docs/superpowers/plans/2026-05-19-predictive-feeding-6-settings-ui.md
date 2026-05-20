# Predictive Feeding — Task 6: Settings UI section + runtime permission flow

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the "Feeding reminders" section to `SettingsScreen` (only visible when `appMode != PARTNER`, matching the existing trust boundary for feeding limits and notifications): predictive toggle, M3 `SingleChoiceSegmentedButtonRow` for lead time (5/10/15/30 min), two M3 `TimePickerDialog` launchers for quiet-hours start/end (locale-aware 12/24h) with a `Quiet hours disabled` helper when start==end, plus an empty-data hint when fewer than 3 valid intervals exist. Implement the full Android-13+ runtime permission state machine and the conditional warning row with `Open settings` action. After this task lands, the feature is reachable.

**Architecture:** `SettingsViewModel` folds the four predictive prefs, the live notification-permission state, and a dedicated `validIntervalCount` flow into `SettingsUiState`. The count is sourced from a new `CountRecentValidIntervalsUseCase` so the empty-data hint is driven by the actual filtered-interval count — not by `Flow<FeedPrediction?>` null, which conflates insufficient-data with active session, stale history, overdue-beyond-grace, and quiet-hours filtering. The composable owns the `ActivityResultContracts.RequestPermission` launcher and the lifecycle observer that re-checks `NotificationManagerCompat.areNotificationsEnabled()` on `ON_RESUME`. Permission denial does NOT auto-flip the toggle off — the warning row is the recovery surface.

**Tech Stack:** Kotlin · Jetpack Compose Material 3 · Hilt · `androidx.activity.result.contract` · `NotificationManagerCompat` · JUnit 5 · Compose UI Test.

**Branch:** `feat/predictive-feeding-6-settings-ui`
**Linear issue:** Predictive feeding — Settings section + permission flow
**Depends on:** Tasks 1, 2, 5
**Overview:** [`2026-05-19-predictive-feeding-overview.md`](./2026-05-19-predictive-feeding-overview.md)
**Source spec:** [`docs/superpowers/specs/2026-05-19-predictive-feeding-reminders-design.md`](../specs/2026-05-19-predictive-feeding-reminders-design.md)

---

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt` (update direct constructors for new deps)
- Modify: `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt` (update `buildPartnerViewModel()` + ad-hoc constructors for new deps; add partner-mode regression)
- Create: `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/CountRecentValidIntervalsUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/CountRecentValidIntervalsUseCaseTest.kt`
- Test: `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelPredictiveTest.kt`
- Test: `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenPredictionTest.kt`

## Steps

- [ ] **Step 1: Add strings**

Append to `app/src/main/res/values/strings.xml`:

```xml
<string name="settings_section_feeding_reminders">FEEDING REMINDERS</string>
<string name="settings_predictive_toggle_title">Predictive reminder</string>
<string name="settings_predictive_toggle_subtitle">Notify before next likely feed</string>
<string name="settings_notifications_blocked_title">Notifications blocked</string>
<string name="settings_notifications_blocked_subtitle">Android won\'t deliver reminders</string>
<string name="settings_open_settings">Open settings</string>
<string name="settings_lead_time_title">Notify ahead by</string>
<string name="settings_lead_time_5">5m</string>
<string name="settings_lead_time_10">10m</string>
<string name="settings_lead_time_15">15m</string>
<string name="settings_lead_time_30">30m</string>
<string name="settings_quiet_hours_title">Skip during quiet hours</string>
<string name="settings_quiet_hours_start">Start</string>
<string name="settings_quiet_hours_end">End</string>
<string name="settings_quiet_hours_helper">Intervals during this window are ignored</string>
<string name="settings_quiet_hours_disabled">Quiet hours disabled</string>
<string name="settings_predictive_need_more_feeds">Need 3+ recent feeds to predict.</string>
```

- [ ] **Step 2: Create `CountRecentValidIntervalsUseCase`**

Create `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/CountRecentValidIntervalsUseCase.kt`. The use case exposes `operator fun invoke(): Flow<Int>` returning the count of intervals that survive the same `INTERVAL_MAX_MINUTES` cap and quiet-hours endpoint filter as `PredictNextFeedUseCase`, capped at `PredictionTuning.SAMPLE_SIZE_TARGET`.

Why a separate use case (not derived from `Flow<FeedPrediction?>`): Task 2's prediction emits `null` for many distinct reasons (active session, stale most-recent, overdue past grace, post-filter sample below `SAMPLE_SIZE_MIN`). Mapping `null → 0` would show `Need 3+ recent feeds` to users who have plenty of history but no current prediction. The hint must reflect the true insufficient-data signal only.

Skeleton:

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.PredictionTuning
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class CountRecentValidIntervalsUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    operator fun invoke(): Flow<Int> = combine(
        breastfeedingRepository.getAllSessions()
            .map { it.take(PredictionTuning.LOOKBACK_LIMIT) },
        settingsRepository.getQuietHoursStartMinute(),
        settingsRepository.getQuietHoursEndMinute(),
    ) { sessions, qhStart, qhEnd ->
        countValidIntervals(sessions, qhStart, qhEnd)
    }

    private fun countValidIntervals(sessions: List<*>, qhStart: Int, qhEnd: Int): Int {
        // Same desc sort + zipWithNext + INTERVAL_MAX_MINUTES filter + quiet-hours
        // endpoint filter as PredictNextFeedUseCase.predict(). Skip the freshness
        // / overdue / SAMPLE_SIZE_MIN gates — those gate prediction emission, not
        // interval count. Cap returned count at PredictionTuning.SAMPLE_SIZE_TARGET.
        TODO("mirror filter pipeline from PredictNextFeedUseCase.predict")
    }
}
```

Tests in `CountRecentValidIntervalsUseCaseTest.kt` must cover:
1. Active session in history → still counts completed intervals (does NOT short-circuit to 0).
2. Stale most-recent (`> FRESHNESS_HORIZON_HOURS`) → still returns full filtered count.
3. Overdue past grace → still returns full filtered count.
4. Fewer than 3 valid intervals after filtering → returns 0..2 (the only case the Settings hint should fire on).
5. Quiet-hours wrap-around filter applied symmetrically to both endpoints.
6. Returned count never exceeds `PredictionTuning.SAMPLE_SIZE_TARGET`.

- [ ] **Step 3: Extend `SettingsUiState` and `SettingsViewModel`**

Add to `SettingsUiState`:

```kotlin
val predictiveEnabled: Boolean = false,
val predictiveLeadMinutes: Int = 15,
val quietHoursStartMinute: Int = 0,
val quietHoursEndMinute: Int = 480,
val notificationsPermissionGranted: Boolean = true,
val showPermissionWarning: Boolean = false,
val validIntervalCount: Int = 0,
```

In `SettingsViewModel`:

- Inject `CountRecentValidIntervalsUseCase` (drives `validIntervalCount`) and `Application` to read `NotificationManagerCompat.from(app).areNotificationsEnabled()` at start. Do NOT inject `PredictNextFeedUseCase` — see Step 2 rationale.
- Fold the four predictive prefs + permission state + `validIntervalCount` flow into the existing `combine` building `uiState`. Compute `showPermissionWarning = predictiveEnabled && !notificationsPermissionGranted` in the same combine.
- Update the existing direct-construction sites added to the file list — `SettingsViewModelTest.setup()` and every `SettingsViewModel(...)` call in `SettingsScreenTest.kt` (`buildPartnerViewModel()` and the ad-hoc construction in `clickingEditAllergiesOpensSheetWithoutCrash`). Pass:
  - `countRecentValidIntervals = mockk { every { invoke() } returns flowOf(0) }` (or a higher count where the test asserts on the hint)
  - `application = mockk(relaxed = true)` for unit tests; `androidx.test.core.app.ApplicationProvider.getApplicationContext()` cast to `Application` for instrumentation
  - Stub the four new `SettingsRepository` getters (`getPredictiveEnabled`, `getPredictiveLeadMinutes`, `getQuietHoursStartMinute`, `getQuietHoursEndMinute`) on `FakeSettingsRepository` and the MockK `settingsRepository`. Without these, `combine` will not emit and the existing assertions will hang or fail.
- Expose:

```kotlin
fun onPredictiveToggleChanged(requested: Boolean) {
    viewModelScope.launch {
        // Permission denial does NOT auto-flip enabled off.
        settingsRepository.setPredictiveEnabled(requested)
    }
}

fun onLeadMinutesChanged(minutes: Int) =
    viewModelScope.launch { settingsRepository.setPredictiveLeadMinutes(minutes) }

fun onQuietHoursStartChanged(minute: Int) =
    viewModelScope.launch { settingsRepository.setQuietHoursStartMinute(minute) }

fun onQuietHoursEndChanged(minute: Int) =
    viewModelScope.launch { settingsRepository.setQuietHoursEndMinute(minute) }

fun refreshNotificationsPermission(granted: Boolean) {
    _permissionGranted.value = granted
}
```

Hold permission state in a `MutableStateFlow<Boolean>` (`_permissionGranted`) seeded from `NotificationManagerCompat.areNotificationsEnabled()`.

- [ ] **Step 4: Write the failing ViewModel test**

Create `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelPredictiveTest.kt`:

```kotlin
class SettingsViewModelPredictiveTest {

    @Test
    fun `warning row hidden when toggle off`() = runTest {
        // enabled=false, granted=false -> showPermissionWarning=false
    }

    @Test
    fun `warning row visible when enabled and permission denied`() = runTest { }

    @Test
    fun `warning row hidden when enabled and permission granted`() = runTest { }

    @Test
    fun `toggle on persists predictiveEnabled true even when permission denied`() = runTest {
        // The spec requires permission denial does NOT auto-flip enabled off.
    }

    @Test
    fun `setting lead minutes persists`() = runTest { }

    @Test
    fun `setting quiet hours start equal to end exposes disabled-window flag in UI state`() = runTest {
        // Derived flag: uiState.quietHoursStartMinute == uiState.quietHoursEndMinute.
    }

    @Test
    fun `validIntervalCount mirrors CountRecentValidIntervalsUseCase output`() = runTest {
        // Drive the count flow to 0, 2, 5 and assert uiState reflects each value.
    }

    @Test
    fun `hint is gated only on validIntervalCount and not on prediction null`() = runTest {
        // When CountRecentValidIntervalsUseCase emits 5 but PredictNextFeedUseCase
        // would emit null (e.g., stale most-recent), uiState.validIntervalCount == 5
        // so the Settings UI must NOT show the empty-data hint.
    }
}
```

- [ ] **Step 5: Run test, verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.settings.SettingsViewModelPredictiveTest"
```

Expected: FAIL with unresolved references or default mismatches.

- [ ] **Step 6: Implement the VM changes until tests pass**

Iterate on `SettingsViewModel` and the existing `SettingsViewModelTest` / `SettingsScreenTest` constructor sites until both the new predictive tests AND the pre-existing Settings tests are green. The pre-existing tests MUST compile and pass — the new constructor params (`countRecentValidIntervals`, `application`) must be supplied at every direct instantiation, and `FakeSettingsRepository` must provide the four new flow getters.

- [ ] **Step 7: Render the "Feeding reminders" section in `SettingsScreen.kt`**

Render the section **inside the same `if (uiState.appMode != AppMode.PARTNER) { ... }` block that already wraps the Feeding Limits and NOTIFICATIONS sections**, immediately after NOTIFICATIONS. Partner-mode devices receive a read-only snapshot and have no local sessions or notification scheduler — exposing predictive controls there leaks a write-mode surface across the trust boundary. The instrumentation suite below asserts this regression.

Use `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` for the runtime permission request. Re-check permission on `Lifecycle.Event.ON_RESUME` and forward to `viewModel.refreshNotificationsPermission(...)`.

Skeleton:

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
) { granted ->
    viewModel.refreshNotificationsPermission(granted)
}
val context = LocalContext.current
val lifecycleOwner = LocalLifecycleOwner.current
val activity = LocalActivity.current
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            val granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
            viewModel.refreshNotificationsPermission(granted)
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}

Column {
    Text(stringResource(R.string.settings_section_feeding_reminders), style = MaterialTheme.typography.labelMedium)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Schedule, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_predictive_toggle_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_predictive_toggle_subtitle), style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = uiState.predictiveEnabled,
            onCheckedChange = { requested ->
                if (requested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    viewModel.onPredictiveToggleChanged(true)
                    if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onPredictiveToggleChanged(requested)
                }
            },
        )
    }

    if (uiState.showPermissionWarning) {
        WarningRow(
            title = stringResource(R.string.settings_notifications_blocked_title),
            subtitle = stringResource(R.string.settings_notifications_blocked_subtitle),
            actionLabel = stringResource(R.string.settings_open_settings),
            onAction = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            },
        )
    }

    LeadTimeSegmentedRow(
        enabled = uiState.predictiveEnabled,
        selectedMinutes = uiState.predictiveLeadMinutes,
        onSelect = viewModel::onLeadMinutesChanged,
    )

    QuietHoursRow(
        enabled = uiState.predictiveEnabled,
        startMinute = uiState.quietHoursStartMinute,
        endMinute = uiState.quietHoursEndMinute,
        is24Hour = DateFormat.is24HourFormat(context),
        onStartPicked = viewModel::onQuietHoursStartChanged,
        onEndPicked = viewModel::onQuietHoursEndChanged,
    )

    if (uiState.predictiveEnabled && uiState.validIntervalCount in 0..2) {
        Text(stringResource(R.string.settings_predictive_need_more_feeds), style = MaterialTheme.typography.bodySmall)
    }
}
```

Define `WarningRow`, `LeadTimeSegmentedRow` (M3 `SingleChoiceSegmentedButtonRow` over the four values 5/10/15/30), and `QuietHoursRow` (two clickable rows that launch `TimePickerDialog` with `is24Hour`) as private composables in the same file. For disabled rows, pass `enabled = uiState.predictiveEnabled` and apply `Modifier.alpha(if (enabled) 1f else 0.5f)`. When `startMinute == endMinute`, the `QuietHoursRow` helper text reads `Quiet hours disabled` (use `stringResource(R.string.settings_quiet_hours_disabled)`); otherwise it reads the normal helper text.

- [ ] **Step 8: Write the instrumentation test**

Create `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenPredictionTest.kt`:

```kotlin
@Test fun toggleRevealsConfigRows() { /* toggle off -> 0.5f alpha; toggle on -> 1.0f */ }

@Test fun quietHoursStartEqualsEndShowsDisabledHelper() { /* assert helper text */ }

@Test fun segmentedButtonSelectionPersists() {
    // Click "30m"; observe SettingsRepository.getPredictiveLeadMinutes() emits 30.
}

@Test fun permissionGrantedHidesWarning() { /* GrantPermissionRule */ }

@Test fun permissionDeniedShowsWarningAndKeepsToggleOn() {
    // Fake NotificationManagerCompat returns false; assert toggle stays ON and warning row visible.
}

@Test fun openSettingsButtonLaunchesAppNotificationSettingsIntent() {
    Intents.init()
    // Click "Open settings" -> assert IntentMatchers.hasAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS).
    Intents.release()
}

@Test fun permissionRevokedOnResumeShowsWarning() {
    // Start granted -> ON_RESUME re-check returns false -> warning visible while toggle ON.
}

@Test fun toggleOffWhilePermissionDeniedHidesWarning() { /* deliberate user choice respected */ }

@Test fun partnerModeHidesFeedingRemindersSection() {
    // buildPartnerViewModel() uses FakeSettingsRepository.getAppMode() == PARTNER.
    composeRule.onNodeWithText("FEEDING REMINDERS").assertDoesNotExist()
    composeRule.onNodeWithText("Predictive reminder").assertDoesNotExist()
}

@Test fun emptyDataHintAbsentWhenValidIntervalCountAtLeastThree() {
    // CountRecentValidIntervalsUseCase emits 5, PredictNextFeedUseCase emits null
    // (e.g., stale anchor) — assert hint string is NOT shown.
}
```

For paths needing a controlled permission state, inject a fake `NotificationManagerCompat`-equivalent through a Hilt test module, or wrap the permission check behind a small `NotificationsPermissionChecker` interface so the test double can flip values.

- [ ] **Step 9: Run tests**

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.babytracker.domain.usecase.breastfeeding.CountRecentValidIntervalsUseCaseTest" \
  --tests "com.babytracker.ui.settings.SettingsViewModelTest" \
  --tests "com.babytracker.ui.settings.SettingsViewModelPredictiveTest"
./gradlew :app:connectedDebugAndroidTest \
  --tests "com.babytracker.ui.settings.SettingsScreenTest" \
  --tests "com.babytracker.ui.settings.SettingsScreenPredictionTest"
```

Expected: PASS for all five suites — the previously-passing `SettingsViewModelTest` and `SettingsScreenTest` MUST stay green after the constructor changes.

- [ ] **Step 10: Manual QA pass**

Run the app and verify:
- Toggle ON with notification permission granted → warning hidden, prediction subtitle appears on Home once 3+ recent feeds exist.
- Toggle ON with permission denied → toggle stays ON, warning row visible, `Open settings` opens system notification settings.
- Change lead time → after the next session stop, the alarm is reconciled (logcat: `PredictiveFeedScheduler` schedule line at the new offset).
- Set quiet hours `22:00 → 06:00` → overnight intervals dropped (verify via Home subtitle changing or instrumentation logs).
- Set start == end → helper text reads `Quiet hours disabled`.
- Snooze 15m on the fired notification → notification reappears ~15 minutes later.

Document the outcome in the PR description.

- [ ] **Step 11: Lint + detekt**

```bash
./gradlew ktlintFormat detekt
```

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/babytracker/domain/usecase/breastfeeding/CountRecentValidIntervalsUseCase.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/babytracker/domain/usecase/breastfeeding/CountRecentValidIntervalsUseCaseTest.kt \
        app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt \
        app/src/test/java/com/babytracker/ui/settings/SettingsViewModelPredictiveTest.kt \
        app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt \
        app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenPredictionTest.kt
git commit -m "feat(settings): add predictive feeding section with runtime permission flow"
```

- [ ] **Step 13: Push branch and open PR**

```bash
git push -u origin feat/predictive-feeding-6-settings-ui
gh pr create --title "feat(settings): predictive feeding section + permission flow" \
  --body "Adds the Feeding reminders section to Settings with toggle, lead-time SegmentedButton, quiet-hours TimePickers, empty-data hint, and full Android-13+ runtime permission state machine with Open-settings recovery. Closes the predictive feeding feature. Task 6 of 6."
```

After merge, mark the Linear project as Done.
