# Nap Reminder Settings Layer — Implementation Plan

LINEAR_ISSUE: AKA-30

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add DataStore keys, SettingsRepository methods, SettingsViewModel state, and Settings UI for the nap reminder toggle and delay configuration.

**Architecture:** Two new DataStore keys (`nap_reminder_enabled`, `nap_reminder_delay_minutes`) flow through the repository layer into SettingsViewModel's `combine` block, exposed via `SettingsUiState`, and surfaced in a new "SLEEP REMINDERS" section in `SettingsScreen` — following the exact same pattern as the existing predictive reminder feature.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences, Hilt, JUnit 5, MockK

**Suggested branch:** `feat/nap-reminder-settings`

**Dependencies:** none — this plan is standalone.

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `domain/repository/SettingsRepository.kt` | Modify | Add 4 new method signatures |
| `data/repository/SettingsRepositoryImpl.kt` | Modify | Add 2 DataStore keys + 4 implementations with clamping |
| `ui/settings/SettingsViewModel.kt` | Modify | Add 2 fields to `SettingsUiState`, extend combine, add 2 handlers |
| `ui/settings/SettingsScreen.kt` | Modify | Add "SLEEP REMINDERS" section (Switch + OutlinedTextField) |
| `app/src/main/res/values/strings.xml` | Modify | Add 4 string resources for the new section |
| `src/test/.../SettingsRepositoryImplTest.kt` | Modify | Add 7 new tests (defaults, clamping, write) |
| `src/test/.../SettingsViewModelTest.kt` | Modify | Add mocks to setup + 4 new tests |

---

### Task 1: Extend SettingsRepository interface

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`

- [ ] **Step 1: Add 4 new method signatures at end of interface**

Replace the contents of `SettingsRepository.kt` with:

```kotlin
package com.babytracker.domain.repository

import com.babytracker.domain.model.ThemeConfig
import com.babytracker.sharing.domain.model.AppMode
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

interface SettingsRepository {
    fun getThemeConfig(): Flow<ThemeConfig>
    suspend fun setThemeConfig(themeConfig: ThemeConfig)
    fun isOnboardingComplete(): Flow<Boolean>
    suspend fun setOnboardingComplete(complete: Boolean)
    fun getMaxPerBreastMinutes(): Flow<Int>
    suspend fun setMaxPerBreastMinutes(minutes: Int)
    fun getMaxTotalFeedMinutes(): Flow<Int>
    suspend fun setMaxTotalFeedMinutes(minutes: Int)
    fun getWakeTime(): Flow<LocalTime?>
    suspend fun setWakeTime(time: LocalTime)
    fun getAutoUpdateEnabled(): Flow<Boolean>
    suspend fun setAutoUpdateEnabled(enabled: Boolean)
    fun getRichNotificationsEnabled(): Flow<Boolean>
    suspend fun setRichNotificationsEnabled(enabled: Boolean)
    fun getAppMode(): Flow<AppMode>
    suspend fun setAppMode(mode: AppMode)
    fun getShareCode(): Flow<String?>
    suspend fun setShareCode(code: String)
    suspend fun clearShareCode()
    fun getPredictiveEnabled(): Flow<Boolean>
    suspend fun setPredictiveEnabled(enabled: Boolean)
    fun getPredictiveLeadMinutes(): Flow<Int>
    suspend fun setPredictiveLeadMinutes(minutes: Int)
    fun getQuietHoursStartMinute(): Flow<Int>
    suspend fun setQuietHoursStartMinute(minuteOfDay: Int)
    fun getQuietHoursEndMinute(): Flow<Int>
    suspend fun setQuietHoursEndMinute(minuteOfDay: Int)
    fun getNapReminderEnabled(): Flow<Boolean>
    suspend fun setNapReminderEnabled(enabled: Boolean)
    fun getNapReminderDelayMinutes(): Flow<Int>
    suspend fun setNapReminderDelayMinutes(minutes: Int)
}
```

- [ ] **Step 2: Verify expected compile errors (not unexpected ones)**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep "error:" | head -20
```

Expected: errors about `SettingsRepositoryImpl` not implementing the 4 new methods. No other errors should appear. If unexpected errors appear, fix them before proceeding.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt
git commit -m "feat(settings): add nap reminder methods to SettingsRepository interface"
```

---

### Task 2: Implement nap reminder keys in SettingsRepositoryImpl (TDD)

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt`
- Modify: `app/src/test/java/com/babytracker/data/repository/SettingsRepositoryImplTest.kt`

- [ ] **Step 1: Write 7 failing tests in SettingsRepositoryImplTest**

The file already has tests for `getRichNotificationsEnabled` / `setRichNotificationsEnabled`. Add these 7 methods after the existing tests. Also ensure these imports are present at the top (add any missing ones):

```kotlin
import androidx.datastore.preferences.core.intPreferencesKey
import org.junit.jupiter.api.Assertions.assertEquals
```

New test methods to append:

```kotlin
@Test
fun `getNapReminderEnabled returns false when key is absent`() = runTest {
    val prefs = mockk<Preferences>()
    every { prefs[booleanPreferencesKey("nap_reminder_enabled")] } returns null
    every { dataStore.data } returns flowOf(prefs)

    assertFalse(repository.getNapReminderEnabled().first())
}

@Test
fun `getNapReminderEnabled returns stored value`() = runTest {
    val prefs = mockk<Preferences>()
    every { prefs[booleanPreferencesKey("nap_reminder_enabled")] } returns true
    every { dataStore.data } returns flowOf(prefs)

    assertTrue(repository.getNapReminderEnabled().first())
}

@Test
fun `setNapReminderEnabled persists value to DataStore`() = runTest {
    val editSlot = slot<suspend (MutablePreferences) -> Unit>()
    coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

    repository.setNapReminderEnabled(true)

    coVerify { dataStore.edit(any()) }
    val prefs = mutablePreferencesOf()
    editSlot.captured(prefs)
    assertTrue(prefs[booleanPreferencesKey("nap_reminder_enabled")]!!)
}

@Test
fun `getNapReminderDelayMinutes returns 60 when key is absent`() = runTest {
    val prefs = mockk<Preferences>()
    every { prefs[intPreferencesKey("nap_reminder_delay_minutes")] } returns null
    every { dataStore.data } returns flowOf(prefs)

    assertEquals(60, repository.getNapReminderDelayMinutes().first())
}

@Test
fun `setNapReminderDelayMinutes clamps value below 1 to 1`() = runTest {
    val editSlot = slot<suspend (MutablePreferences) -> Unit>()
    coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

    repository.setNapReminderDelayMinutes(0)

    val prefs = mutablePreferencesOf()
    editSlot.captured(prefs)
    assertEquals(1, prefs[intPreferencesKey("nap_reminder_delay_minutes")])
}

@Test
fun `setNapReminderDelayMinutes clamps value above 480 to 480`() = runTest {
    val editSlot = slot<suspend (MutablePreferences) -> Unit>()
    coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

    repository.setNapReminderDelayMinutes(999)

    val prefs = mutablePreferencesOf()
    editSlot.captured(prefs)
    assertEquals(480, prefs[intPreferencesKey("nap_reminder_delay_minutes")])
}

@Test
fun `setNapReminderDelayMinutes accepts value within range`() = runTest {
    val editSlot = slot<suspend (MutablePreferences) -> Unit>()
    coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

    repository.setNapReminderDelayMinutes(90)

    val prefs = mutablePreferencesOf()
    editSlot.captured(prefs)
    assertEquals(90, prefs[intPreferencesKey("nap_reminder_delay_minutes")])
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.SettingsRepositoryImplTest" 2>&1 | tail -30
```

Expected: compilation error — `getNapReminderEnabled` etc. not yet defined on `SettingsRepositoryImpl`. If tests somehow pass, stop — the methods already exist.

- [ ] **Step 3: Add 2 DataStore keys to the companion object**

In `SettingsRepositoryImpl.kt`, inside `companion object`, add after the `QUIET_HOURS_END_MINUTE` line:

```kotlin
val NAP_REMINDER_ENABLED = booleanPreferencesKey("nap_reminder_enabled")
val NAP_REMINDER_DELAY_MINUTES = intPreferencesKey("nap_reminder_delay_minutes")
```

- [ ] **Step 4: Add 4 method implementations at end of the class (before closing `}`)**

```kotlin
override fun getNapReminderEnabled(): Flow<Boolean> =
    dataStore.data.map { it[NAP_REMINDER_ENABLED] ?: false }

override suspend fun setNapReminderEnabled(enabled: Boolean) {
    dataStore.edit { it[NAP_REMINDER_ENABLED] = enabled }
}

override fun getNapReminderDelayMinutes(): Flow<Int> =
    dataStore.data.map { it[NAP_REMINDER_DELAY_MINUTES] ?: 60 }

override suspend fun setNapReminderDelayMinutes(minutes: Int) {
    dataStore.edit { it[NAP_REMINDER_DELAY_MINUTES] = minutes.coerceIn(1, 480) }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.repository.SettingsRepositoryImplTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, 10 tests pass (3 existing + 7 new).

- [ ] **Step 6: Check and update any androidTest SettingsRepository fakes**

```bash
grep -rl "SettingsRepository" app/src/androidTest/ --include="*.kt" 2>/dev/null | head -10
```

For every file returned: open it and find any class that `implements SettingsRepository` (or `object` that does). Add these 4 stub overrides so it keeps compiling after the interface change:

```kotlin
override fun getNapReminderEnabled(): Flow<Boolean> = flowOf(false)
override suspend fun setNapReminderEnabled(enabled: Boolean) = Unit
override fun getNapReminderDelayMinutes(): Flow<Int> = flowOf(60)
override suspend fun setNapReminderDelayMinutes(minutes: Int) = Unit
```

If the file does not already import `kotlinx.coroutines.flow.flowOf`, add the import.

- [ ] **Step 7: Verify androidTest compiles**

```bash
./gradlew :app:compileDebugAndroidTestKotlin 2>&1 | grep "error:" | head -20
```

Expected: no errors. If "must override" errors appear for the new methods, fix them with the stubs above.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt
git add app/src/test/java/com/babytracker/data/repository/SettingsRepositoryImplTest.kt
git commit -m "feat(settings): implement nap reminder DataStore keys in SettingsRepositoryImpl"
```

---

### Task 3: Extend SettingsViewModel with nap reminder state

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Add 2 fields to SettingsUiState**

In `SettingsViewModel.kt`, the `SettingsUiState` data class ends with `val validIntervalCount: Int = 0,`. Add after it:

```kotlin
val napReminderEnabled: Boolean = false,
val napReminderDelayMinutes: Int = 60,
```

Full updated `SettingsUiState`:

```kotlin
data class SettingsUiState(
    val baby: Baby? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val autoUpdateEnabled: Boolean = true,
    val richNotificationsEnabled: Boolean = true,
    val appMode: AppMode? = null,
    val isDisconnected: Boolean = false,
    val predictiveEnabled: Boolean = false,
    val predictiveLeadMinutes: Int = 15,
    val quietHoursStartMinute: Int = 0,
    val quietHoursEndMinute: Int = 480,
    val notificationsPermissionGranted: Boolean = true,
    val showPermissionWarning: Boolean = false,
    val validIntervalCount: Int = 0,
    val napReminderEnabled: Boolean = false,
    val napReminderDelayMinutes: Int = 60,
)
```

- [ ] **Step 2: Update SettingsViewModelTest — promote mocks to fields, add mocks to setup, add 5 new tests**

**a) Promote 3 local variables in `setUp()` to class-level `lateinit var` fields** (they are currently declared with `val` inside `setup()`):

```kotlin
private lateinit var getBabyProfile: GetBabyProfileUseCase
private lateinit var countRecentValidIntervals: CountRecentValidIntervalsUseCase
private lateinit var permissionChecker: NotificationPermissionChecker
```

Also add this import at the top:
```kotlin
import com.babytracker.manager.NotificationPermissionChecker
```

**b) In `setUp()`, replace the 3 local `val` declarations** with assignments to the new fields:

```kotlin
getBabyProfile = mockk()
every { getBabyProfile() } returns flowOf(null)
```

```kotlin
countRecentValidIntervals = mockk()
every { countRecentValidIntervals() } returns flowOf(0)
```

```kotlin
permissionChecker = mockk { every { areNotificationsEnabled() } returns true }
```

Update the `SettingsViewModel(...)` constructor call to use the field names (same values, just no `val`):

```kotlin
viewModel = SettingsViewModel(
    getBabyProfile,
    settingsRepository,
    mockk(),
    countRecentValidIntervals,
    permissionChecker,
)
```

**c) In `setUp()`, after `every { settingsRepository.getQuietHoursEndMinute() } returns flowOf(480)`, add:**

```kotlin
every { settingsRepository.getNapReminderEnabled() } returns flowOf(false)
every { settingsRepository.getNapReminderDelayMinutes() } returns flowOf(60)
```

**d) Append 5 new test methods at the end of the class.** Note: `coJustRun`, `assertEquals`, `assertTrue`, `assertFalse` are already imported in this file.

```kotlin
@Test
fun `initial state has napReminderEnabled false`() = runTest {
    testDispatcher.scheduler.advanceUntilIdle()
    assertFalse(viewModel.uiState.value.napReminderEnabled)
}

@Test
fun `initial state has napReminderDelayMinutes 60`() = runTest {
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(60, viewModel.uiState.value.napReminderDelayMinutes)
}

@Test
fun `onNapReminderToggleChanged calls setNapReminderEnabled`() = runTest {
    coJustRun { settingsRepository.setNapReminderEnabled(any()) }

    viewModel.onNapReminderToggleChanged(true)
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify { settingsRepository.setNapReminderEnabled(true) }
}

@Test
fun `onNapReminderDelayChanged calls setNapReminderDelayMinutes`() = runTest {
    coJustRun { settingsRepository.setNapReminderDelayMinutes(any()) }

    viewModel.onNapReminderDelayChanged(90)
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify { settingsRepository.setNapReminderDelayMinutes(90) }
}

@Test
fun `showPermissionWarning true when nap reminder enabled and notifications denied`() = runTest {
    every { settingsRepository.getNapReminderEnabled() } returns flowOf(true)
    val deniedChecker = mockk<NotificationPermissionChecker> {
        every { areNotificationsEnabled() } returns false
    }
    val vm = SettingsViewModel(
        getBabyProfile,
        settingsRepository,
        mockk(),
        countRecentValidIntervals,
        deniedChecker,
    )
    testDispatcher.scheduler.advanceUntilIdle()
    assertTrue(vm.uiState.value.showPermissionWarning)
}
```

- [ ] **Step 3: Run tests to confirm handler tests fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.settings.SettingsViewModelTest" 2>&1 | tail -30
```

Expected: compile errors — `onNapReminderToggleChanged` and `onNapReminderDelayChanged` not yet defined. The default-value tests may pass or fail depending on compiler state; that's fine.

- [ ] **Step 4: Extend combine block in SettingsViewModel**

In `SettingsViewModel.kt`, update the `combine(...)` call inside `viewModelScope.launch` to add 2 more flows and extract them. The existing call has 13 flows (indices 0–12). Add `getNapReminderEnabled` at index 13 and `getNapReminderDelayMinutes` at index 14:

```kotlin
combine(
    getBabyProfile(),
    settingsRepository.getMaxPerBreastMinutes(),
    settingsRepository.getMaxTotalFeedMinutes(),
    settingsRepository.getThemeConfig(),
    settingsRepository.getAutoUpdateEnabled(),
    settingsRepository.getRichNotificationsEnabled(),
    settingsRepository.getAppMode(),
    settingsRepository.getPredictiveEnabled(),
    settingsRepository.getPredictiveLeadMinutes(),
    settingsRepository.getQuietHoursStartMinute(),
    settingsRepository.getQuietHoursEndMinute(),
    countRecentValidIntervals(),
    _permissionGranted,
    settingsRepository.getNapReminderEnabled(),
    settingsRepository.getNapReminderDelayMinutes(),
) { values ->
    val baby = values[0] as? Baby
    val maxPerBreast = values[1] as Int
    val maxTotal = values[2] as Int
    val themeConfig = values[3] as ThemeConfig
    val autoUpdate = values[4] as Boolean
    val richNotifications = values[5] as Boolean
    val appMode = values[6] as AppMode
    val predictiveEnabled = values[7] as Boolean
    val predictiveLeadMinutes = values[8] as Int
    val quietHoursStart = values[9] as Int
    val quietHoursEnd = values[10] as Int
    val validIntervalCount = values[11] as Int
    val permissionGranted = values[12] as Boolean
    val napReminderEnabled = values[13] as Boolean
    val napReminderDelayMinutes = values[14] as Int
    SettingsUiState(
        baby = baby,
        maxPerBreastMinutes = maxPerBreast,
        maxTotalFeedMinutes = maxTotal,
        themeConfig = themeConfig,
        autoUpdateEnabled = autoUpdate,
        richNotificationsEnabled = richNotifications,
        appMode = appMode,
        predictiveEnabled = predictiveEnabled,
        predictiveLeadMinutes = predictiveLeadMinutes,
        quietHoursStartMinute = quietHoursStart,
        quietHoursEndMinute = quietHoursEnd,
        notificationsPermissionGranted = permissionGranted,
        showPermissionWarning = (predictiveEnabled || napReminderEnabled) && !permissionGranted,
        validIntervalCount = validIntervalCount,
        napReminderEnabled = napReminderEnabled,
        napReminderDelayMinutes = napReminderDelayMinutes,
    )
}.collect { next ->
    _uiState.update { current -> next.copy(isDisconnected = current.isDisconnected) }
}
```

- [ ] **Step 5: Add 2 handler functions at end of SettingsViewModel class**

Add before the final closing `}` of the class:

```kotlin
fun onNapReminderToggleChanged(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setNapReminderEnabled(enabled) }
}

fun onNapReminderDelayChanged(minutes: Int) {
    viewModelScope.launch { settingsRepository.setNapReminderDelayMinutes(minutes) }
}
```

- [ ] **Step 6: Run all SettingsViewModelTest to confirm all pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.settings.SettingsViewModelTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass (existing + 4 new).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt
git add app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): expose nap reminder state in SettingsViewModel"
```

---

### Task 4: Add Sleep Reminders section to SettingsScreen

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add string resources to strings.xml**

In `strings.xml`, add before `</resources>` (after the last `settings_quiet_*` entry):

```xml
<!-- Settings — nap reminder section -->
<string name="settings_section_sleep_reminders">SLEEP REMINDERS</string>
<string name="settings_nap_reminder_toggle_title">Nap reminder</string>
<string name="settings_nap_reminder_toggle_subtitle">Notify after a nap ends</string>
<string name="settings_nap_reminder_delay_label">Remind after (minutes)</string>
```

- [ ] **Step 2: Add the SLEEP REMINDERS section in SettingsScreen.kt**

Locate the `HorizontalDivider()` that appears just after the `QuietHoursRow(...)` call and immediately before the closing `}` of the `if (uiState.appMode != null && uiState.appMode != AppMode.PARTNER)` block (around line 402). Insert the new section after that `HorizontalDivider()`:

```kotlin
HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
Text(
    text = stringResource(R.string.settings_section_sleep_reminders),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
)

Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f)) {
        Text(
            stringResource(R.string.settings_nap_reminder_toggle_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.settings_nap_reminder_toggle_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Switch(
        checked = uiState.napReminderEnabled,
        modifier = Modifier.testTag("nap_reminder_switch"),
        onCheckedChange = { viewModel.onNapReminderToggleChanged(it) },
    )
}

OutlinedTextField(
    value = if (uiState.napReminderDelayMinutes > 0) uiState.napReminderDelayMinutes.toString() else "",
    onValueChange = { input ->
        input.toIntOrNull()?.let { viewModel.onNapReminderDelayChanged(it) }
    },
    label = { Text(stringResource(R.string.settings_nap_reminder_delay_label)) },
    enabled = uiState.napReminderEnabled,
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .alpha(if (uiState.napReminderEnabled) 1f else 0.38f),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    singleLine = true,
)
```

`Modifier.alpha`, `KeyboardOptions`, and `KeyboardType` are already imported in `SettingsScreen.kt`.

- [ ] **Step 3: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. Any compile error in `SettingsScreen.kt` means the insertion point was wrong or an import is missing — check the imports at the top of the file.

- [ ] **Step 4: Format and lint**

```bash
./gradlew ktlintFormat
./gradlew detekt
```

Expected: no violations. If detekt reports a finding, fix the code (never use `@Suppress`).

- [ ] **Step 5: Run full test suite one final time**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt
git add app/src/main/res/values/strings.xml
git commit -m "feat(settings): add nap reminder UI section to SettingsScreen"
```

---

## Acceptance Criteria

- [ ] `SettingsRepository` interface declares `getNapReminderEnabled`, `setNapReminderEnabled`, `getNapReminderDelayMinutes`, `setNapReminderDelayMinutes`
- [ ] `SettingsRepositoryImpl`: `getNapReminderEnabled` defaults `false`; `getNapReminderDelayMinutes` defaults `60`; `setNapReminderDelayMinutes` clamps input to `1..480`
- [ ] `SettingsUiState` has `napReminderEnabled: Boolean = false` and `napReminderDelayMinutes: Int = 60`
- [ ] `SettingsViewModel` exposes `onNapReminderToggleChanged(Boolean)` and `onNapReminderDelayChanged(Int)`
- [ ] Settings screen shows "SLEEP REMINDERS" section with Switch (testTag `nap_reminder_switch`) and `OutlinedTextField`
- [ ] `OutlinedTextField` is disabled and renders at 38% alpha when toggle is off
- [ ] `SettingsUiState.showPermissionWarning` is `true` when `napReminderEnabled == true` and notifications permission is denied (in addition to the existing predictive case)
- [ ] 7 new `SettingsRepositoryImplTest` tests pass
- [ ] 5 new `SettingsViewModelTest` tests pass (4 state/handler + 1 permission warning)
- [ ] Every `SettingsRepository` fake in `androidTest/` implements the 4 new methods
- [ ] `./gradlew :app:compileDebugAndroidTestKotlin` — no errors
- [ ] `./gradlew ktlintFormat && ./gradlew detekt` clean
- [ ] `./gradlew :app:testDebugUnitTest` — zero failures including pre-existing tests
