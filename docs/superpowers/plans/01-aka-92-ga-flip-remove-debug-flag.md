# GA Flip: Remove `predictiveSleepEnabled` Debug Flag Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove all `BuildConfig.DEBUG` guards from the sleep prediction UI, startup wiring, and alarm receiver so `predictiveSleepEnabled` is reachable and fully functional in release builds.

**Architecture:** Pure guard removal across 8 files. No logic changes, no new files, no new tests required (the existing `PredictiveSleepReceiverTest.postsNotificationOnFireAction` already covers the notification-firing path via `buildReceiver()` — it bypasses the early-return guard, so removing the guard makes release behavior match what the test already exercises). `BabyTrackerApp.kt` must be included — it is the only place that calls `predictiveSleepCoordinator.start()`. `PredictiveSleepReceiver.kt` must be included — it is the AlarmManager target and currently drops every alarm in release. The debug data seeder block in `BabyTrackerApp.kt` (line 56) and the Developer section in `SettingsScreen` (line 633) remain debug-only. Existing test suite plus a release-variant build check and a grep gate confirm all guards are cleared.

**Linear issue:** AKA-92  
**Spec:** `docs/superpowers/specs/2026-06-03-sleep-window-prediction-design.md` §12 (Phase 0, issue 7 of 7)  
**Precondition (enforced outside this PR):** AKA-90 evaluation harness must pass baseline thresholds before this PR is merged. Attach harness output as a comment on AKA-92 before merging.

**Tech Stack:** Kotlin, Jetpack Compose, `BuildConfig`

---

### Task 1: Remove `BuildConfig.DEBUG` guard from `HomeViewModel`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`

The `combine` block currently gates `predictSleepWindow()` behind `BuildConfig.DEBUG`, returning a hardcoded `Unavailable("release")` in release builds. Remove the conditional so the predictor always runs.

- [ ] **Step 1.1: Replace the conditional with a direct call**

In `HomeViewModel.kt`, find line 118:

```kotlin
if (BuildConfig.DEBUG) predictSleepWindow() else flowOf(SleepPredictionState.Unavailable("release")),
```

Replace with:

```kotlin
predictSleepWindow(),
```

- [ ] **Step 1.2: Remove the two now-unused imports**

Remove these two import lines from the top of `HomeViewModel.kt`:

```kotlin
import com.babytracker.BuildConfig
import kotlinx.coroutines.flow.flowOf
```

Both become unused: `BuildConfig` had only one reference (line 118), and `flowOf` was only used in the removed `else` branch.

---

### Task 2: Remove `BuildConfig.DEBUG` guard from `BabyTrackerApp`

**Files:**
- Modify: `app/src/main/java/com/babytracker/BabyTrackerApp.kt`

`onCreate` wraps both `createPredictiveSleepNotificationChannel(this)` and `predictiveSleepCoordinator.start()` in a debug-only block. This means the notification coordinator never starts in release — sleep reminders cannot fire even if the user enables the feature. Move those two calls outside the guard. The debug data seeder block at line 56 must stay guarded.

- [ ] **Step 2.1: Unwrap the channel creation and coordinator start**

In `BabyTrackerApp.kt`, find lines 50–53:

```kotlin
        if (BuildConfig.DEBUG) {
            createPredictiveSleepNotificationChannel(this)
            predictiveSleepCoordinator.start()
        }
```

Replace with:

```kotlin
        createPredictiveSleepNotificationChannel(this)
        predictiveSleepCoordinator.start()
```

**Do not touch** the separate `if (BuildConfig.DEBUG)` block at lines 56–58:

```kotlin
        if (BuildConfig.DEBUG) {
            appScope.launch { debugDataSeeder.seedIfEmpty() }
        }
```

That block must remain guarded. After this change `BabyTrackerApp.kt` still has one `BuildConfig.DEBUG` reference — **do not remove the `import com.babytracker.BuildConfig` import**.

---

### Task 3: Remove `BuildConfig.DEBUG` early-return guard from `PredictiveSleepReceiver`

**Files:**
- Modify: `app/src/main/java/com/babytracker/receiver/PredictiveSleepReceiver.kt`

`onReceive` opens with `if (!BuildConfig.DEBUG) return`, discarding every alarm in release builds. After the coordinator starts (Task 2) and the UI is exposed (Tasks 4–8), this guard is the remaining gate that prevents notifications from ever firing in release. Remove it along with its now-unused import.

The existing instrumentation test `PredictiveSleepReceiverTest.postsNotificationOnFireAction` already covers the notification-posting path via `buildReceiver()`, which manually injects the repository and never hits this early-return. No new test is needed — removing the guard makes the receiver's release behavior match what the test already exercises.

- [ ] **Step 3.1: Remove the early-return guard and its comment**

In `PredictiveSleepReceiver.kt`, find lines 25–32:

```kotlin
    override fun onReceive(context: Context, intent: Intent) {
        // Guard against a debug-scheduled alarm firing on a release build after upgrade.
        // The manifest declares the receiver for all build types (required for AlarmManager
        // to resolve the PendingIntent), so this is the last line of defence.
        if (!BuildConfig.DEBUG) {
            Log.d(TAG, "Dropping alarm in release build")
            return
        }
        when (intent.action) {
```

Replace with:

```kotlin
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
```

- [ ] **Step 3.2: Remove the now-unused import**

Remove:

```kotlin
import com.babytracker.BuildConfig
```

---

### Task 4: Remove `BuildConfig.DEBUG` guard from `HomeScreen`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

The `SleepPredictionCard` composable is wrapped in an `if (BuildConfig.DEBUG)` block. Unwrap it so the card renders for all users.

- [ ] **Step 4.1: Unwrap the prediction card**

In `HomeScreen.kt`, find lines 425–427:

```kotlin
            if (BuildConfig.DEBUG) {
                SleepPredictionCard(state = uiState.sleepPrediction)
            }
```

Replace with:

```kotlin
            SleepPredictionCard(state = uiState.sleepPrediction)
```

- [ ] **Step 4.2: Remove the now-unused import**

Remove:

```kotlin
import com.babytracker.BuildConfig
```

---

### Task 5: Remove `BuildConfig.DEBUG` guard from `SettingsScreen` sleep-reminders section

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`

The `predictiveSleepEnabled` toggle, lead-time selector, and quiet-hours row are all wrapped in `if (BuildConfig.DEBUG)`. Unwrap them. **Do not touch the `if (BuildConfig.DEBUG)` block at line 633** — that wraps the Developer section and must stay.

- [ ] **Step 5.1: Unwrap the sleep-reminders section**

In `SettingsScreen.kt`, find lines 543 and 578:

```kotlin
                if (BuildConfig.DEBUG) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_predictive_sleep_toggle_title),
                        description = stringResource(R.string.settings_predictive_sleep_toggle_subtitle),
                        checked = uiState.predictiveSleepEnabled,
                        onCheckedChange = { newValue ->
                            if (newValue && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = NotificationManagerCompat.from(context)
                                    .areNotificationsEnabled()
                                viewModel.onPredictiveSleepToggleChanged(true)
                                if (!granted) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                viewModel.onPredictiveSleepToggleChanged(newValue)
                            }
                        },
                        modifier = Modifier.testTag("predictive_sleep_switch"),
                        leadingIcon = { Icon(Icons.Outlined.Bedtime, contentDescription = null) },
                    )
                    LeadTimeSegmentedRow(
                        enabled = uiState.predictiveSleepEnabled,
                        selectedMinutes = uiState.predictiveSleepLeadMinutes,
                        onSelect = viewModel::onSleepLeadMinutesChanged,
                    )
                    val is24Hour = DateFormat.is24HourFormat(context)
                    QuietHoursRow(
                        enabled = uiState.predictiveSleepEnabled,
                        startMinute = uiState.quietHoursStartMinute,
                        endMinute = uiState.quietHoursEndMinute,
                        is24Hour = is24Hour,
                        onStartPicked = viewModel::onQuietHoursStartChanged,
                        onEndPicked = viewModel::onQuietHoursEndChanged,
                    )
                }
```

Remove the outer `if (BuildConfig.DEBUG) {` line and the matching closing `}` at line 578. Leave all inner content unchanged and at the same indentation level minus one level. The result:

```kotlin
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_predictive_sleep_toggle_title),
                    description = stringResource(R.string.settings_predictive_sleep_toggle_subtitle),
                    checked = uiState.predictiveSleepEnabled,
                    onCheckedChange = { newValue ->
                        if (newValue && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = NotificationManagerCompat.from(context)
                                .areNotificationsEnabled()
                            viewModel.onPredictiveSleepToggleChanged(true)
                            if (!granted) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            viewModel.onPredictiveSleepToggleChanged(newValue)
                        }
                    },
                    modifier = Modifier.testTag("predictive_sleep_switch"),
                    leadingIcon = { Icon(Icons.Outlined.Bedtime, contentDescription = null) },
                )
                LeadTimeSegmentedRow(
                    enabled = uiState.predictiveSleepEnabled,
                    selectedMinutes = uiState.predictiveSleepLeadMinutes,
                    onSelect = viewModel::onSleepLeadMinutesChanged,
                )
                val is24Hour = DateFormat.is24HourFormat(context)
                QuietHoursRow(
                    enabled = uiState.predictiveSleepEnabled,
                    startMinute = uiState.quietHoursStartMinute,
                    endMinute = uiState.quietHoursEndMinute,
                    is24Hour = is24Hour,
                    onStartPicked = viewModel::onQuietHoursStartChanged,
                    onEndPicked = viewModel::onQuietHoursEndChanged,
                )
```

**Do not remove the `import com.babytracker.BuildConfig` import** — it is still used by the Developer section guard at line 633.

---

### Task 6: Fix permission-warning expression in `SettingsViewModel`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt`

The `showPermissionWarning` computation gates the sleep condition behind `BuildConfig.DEBUG`. A user who enables `predictiveSleepEnabled` in release must also trigger the permission warning.

- [ ] **Step 6.1: Drop the `BuildConfig.DEBUG &&` sub-expression**

In `SettingsViewModel.kt`, find line 114:

```kotlin
                    showPermissionWarning = (predictiveEnabled || napReminderEnabled || (BuildConfig.DEBUG && predictiveSleepEnabled)) && !permissionGranted,
```

Replace with:

```kotlin
                    showPermissionWarning = (predictiveEnabled || napReminderEnabled || predictiveSleepEnabled) && !permissionGranted,
```

- [ ] **Step 6.2: Remove the now-unused import**

Remove:

```kotlin
import com.babytracker.BuildConfig
```

---

### Task 7: Remove `BuildConfig.DEBUG` guard from `SleepViewModel`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt`

The `init` block launches the prediction collector only in debug builds. Remove the guard; keep the `viewModelScope.launch` block.

- [ ] **Step 7.1: Unwrap the launch block**

In `SleepViewModel.kt`, find lines 114–121:

```kotlin
    init {
        if (BuildConfig.DEBUG) {
            viewModelScope.launch {
                predictSleepWindow().collect { prediction ->
                    _uiState.value = _uiState.value.copy(sleepPrediction = prediction)
                }
            }
        }
        viewModelScope.launch {
```

Replace with:

```kotlin
    init {
        viewModelScope.launch {
            predictSleepWindow().collect { prediction ->
                _uiState.value = _uiState.value.copy(sleepPrediction = prediction)
            }
        }
        viewModelScope.launch {
```

- [ ] **Step 7.2: Remove the now-unused import**

Remove:

```kotlin
import com.babytracker.BuildConfig
```

---

### Task 8: Remove `BuildConfig.DEBUG` guard from `SleepTrackingScreen`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt`

The `SleepRecommendationSection` lazy list item is wrapped in `if (BuildConfig.DEBUG)`. Unwrap it.

- [ ] **Step 8.1: Unwrap the recommendation section item**

In `SleepTrackingScreen.kt`, find lines 228–235:

```kotlin
            if (BuildConfig.DEBUG) {
                item {
                    SleepRecommendationSection(
                        state = uiState.sleepPrediction,
                        schedule = uiState.schedule,
                    )
                }
            }
```

Replace with:

```kotlin
            item {
                SleepRecommendationSection(
                    state = uiState.sleepPrediction,
                    schedule = uiState.schedule,
                )
            }
```

- [ ] **Step 8.2: Remove the now-unused import**

Remove:

```kotlin
import com.babytracker.BuildConfig
```

---

### Task 9: Format, lint, test, and commit

- [ ] **Step 9.1: Auto-fix formatting**

```bash
./gradlew ktlintFormat
```

Expected: exits 0, may reformat indentation on the unwrapped blocks.

- [ ] **Step 9.2: Run static analysis**

```bash
./gradlew detekt
```

Expected: exits 0 with no new violations. If a violation is reported, fix the code (never suppress with `@Suppress`).

- [ ] **Step 9.3: Run the unit test suite (fast loop)**

```bash
./gradlew :app:testDebugUnitTest -PfastTests
```

Expected: BUILD SUCCESSFUL. All tests pass. (The `-PfastTests` flag skips the slow architecture tests for a fast inner loop.)

- [ ] **Step 9.4: Run the full unit test suite (including architecture tests)**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All tests pass. Run this before committing to match CI behaviour.

- [ ] **Step 9.5: Verify release variant compiles cleanly**

This change is specifically about `BuildConfig.DEBUG == false`. A debug test run cannot catch a leftover release-only guard. Confirm there are no compile errors in the release variant:

```bash
./gradlew :app:assembleRelease
```

Expected: BUILD SUCCESSFUL. No compilation errors. If the release build fails, investigate — do not disable minification or ProGuard rules to work around it.

- [ ] **Step 9.6: Confirm no sleep-prediction path remains behind a DEBUG guard**

```bash
grep -rn "BuildConfig.DEBUG" app/src/main/java/com/babytracker/ | grep -i "sleep\|predictive"
```

Expected output: **empty**. The only remaining `BuildConfig.DEBUG` references in production code are in `SettingsScreen.kt` (Developer section) and `BabyTrackerApp.kt` (debug data seeder) — neither is sleep-prediction-related. If this grep returns any matches, those are missed guards; fix them before committing.

- [ ] **Step 9.7: Commit**

The commit body must include the harness validation record (fixture metrics snapshot from AKA-90). Attach the same output as a comment on AKA-92 in Linear before the PR is merged.

```bash
git add \
  app/src/main/java/com/babytracker/BabyTrackerApp.kt \
  app/src/main/java/com/babytracker/receiver/PredictiveSleepReceiver.kt \
  app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt \
  app/src/main/java/com/babytracker/ui/home/HomeScreen.kt \
  app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt \
  app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt \
  app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt \
  app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt

git commit -m "$(cat <<'EOF'
chore(sleep): flip predictiveSleepEnabled to GA — remove debug flag [AKA-92]

Remove BuildConfig.DEBUG guards from eight files so predictiveSleepEnabled
is reachable and fully functional in release builds:

- BabyTrackerApp: createPredictiveSleepNotificationChannel + coordinator.start()
  moved outside the debug block so the notification coordinator runs in release
- PredictiveSleepReceiver: remove early-return guard so alarms are handled in release
- HomeViewModel: always call predictSleepWindow() (drop Unavailable("release") fallback)
- HomeScreen: SleepPredictionCard renders for all users
- SettingsScreen: sleep-reminders section (toggle + lead time + quiet hours) visible in release
- SettingsViewModel: permission warning includes predictiveSleepEnabled without DEBUG gate
- SleepViewModel: prediction collector starts unconditionally in init
- SleepTrackingScreen: SleepRecommendationSection item renders for all users

Remaining debug-only blocks (untouched):
- BabyTrackerApp: debug data seeder
- SettingsScreen: Developer section (Design System link etc.)

predictiveSleepEnabled default remains false (opt-in); toggle is now reachable.

Harness validation record (attach output from AKA-90 before merging):
<PASTE_HARNESS_OUTPUT_HERE>

Blocked-by: AKA-90
EOF
)"
```

> Replace `<PASTE_HARNESS_OUTPUT_HERE>` with the fixture metrics snapshot from the AKA-90 evaluation harness before committing.
