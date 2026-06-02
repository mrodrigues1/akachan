# InventorySettingsScreen + InventorySettingsViewModel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. When implementing the composable, also invoke the `mobile-android-design` skill for Material 3 / Compose conventions.

**LINEAR_ISSUE:** AKA-77

**Goal:** Add the Milk Stash Settings screen (master expiration toggle, expiration-days field, notification toggle, notification-time picker) and its ViewModel, which persists settings via `InventorySettingsRepository` and arms/cancels the daily alarm via `StashExpirationScheduler`.

**Architecture:** `@HiltViewModel` exposing a single `StateFlow<InventorySettingsUiState>` built by combining the four repository Flows, plus a locally-edited days string with validation. The ViewModel owns the scheduler side effects: enabling the notification (or changing its time) calls `scheduleDaily`; disabling the notification **or** the master toggle calls `cancel()` (spec: master-disable must cancel immediately, not rely on the receiver guard). The screen is a stateless composable driven by the state + event callbacks, following the existing settings UI style.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3 (`Switch`, `OutlinedTextField`, `TimePicker`), Hilt, Coroutines/Flow.

**Dependencies:** AKA-75 (`InventorySettingsRepository`), AKA-79 (`StashExpirationScheduler`). Navigation wiring is AKA-81 (this plan exposes `onNavigateBack`).

**Suggested implementation branch:** `feat/aka-77-settings-screen`

---

## File Structure

- Create: `app/src/main/java/com/babytracker/ui/inventory/InventorySettingsViewModel.kt`
- Create: `app/src/main/java/com/babytracker/ui/inventory/InventorySettingsScreen.kt`
- Test: `app/src/test/java/com/babytracker/ui/inventory/InventorySettingsViewModelTest.kt`

Pattern sources: `ui/inventory/InventoryViewModel.kt` (`@HiltViewModel`, single `StateFlow<*UiState>`, `combine` in `init`), `ui/settings/SettingsScreen.kt` (toggle rows, section headers, `TimePicker` dialog usage), CLAUDE.md design tokens (section headers `labelMedium` UPPERCASE; toggle label `titleMedium` + description `bodyMedium`; cards `shapes.medium`).

---

## Scheduler side-effect contract (locked, tested)

| User action | Persist | Scheduler call |
|-------------|---------|----------------|
| Master toggle ON | `setExpirationEnabled(true)` | `scheduleDaily(currentTime)` **iff** notif already enabled (restore); else none |
| Master toggle OFF | `setExpirationEnabled(false)` | `cancel()` |
| Notif toggle ON | `setExpirationNotifEnabled(true)` | `scheduleDaily(currentTime)` |
| Notif toggle OFF | `setExpirationNotifEnabled(false)` | `cancel()` |
| Notif time changed | `setExpirationNotifTimeMinutes(t)` | `scheduleDaily(t)` (only if notif currently enabled) |
| Days changed (valid) | `setExpirationDays(n)` | none |
| Days changed (invalid) | none | none (show validation error) |

> Rationale: scheduling is gated on the **notification** toggle, not the master toggle. Disabling the master toggle must `cancel()`. Because master-off leaves `notifEnabled` persisted as-is (the notif toggle isn't auto-flipped), re-enabling the master toggle must **restore** the alarm when notif was previously on — otherwise the UI shows notifications enabled with no alarm scheduled (silent failure). The receiver's runtime guard is a secondary defense (AKA-80).

---

## Task 1: ViewModel tests (TDD)

**Files:**
- Test: `app/src/test/java/com/babytracker/ui/inventory/InventorySettingsViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.babytracker.ui.inventory

import app.cash.turbine.test
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class InventorySettingsViewModelTest {

    private lateinit var settings: InventorySettingsRepository
    private lateinit var scheduler: StashExpirationScheduler

    private val enabled = MutableStateFlow(false)
    private val days = MutableStateFlow(4)
    private val notifEnabled = MutableStateFlow(false)
    private val notifTime = MutableStateFlow(480)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        settings = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        every { settings.getExpirationEnabled() } returns enabled
        every { settings.getExpirationDays() } returns days
        every { settings.getExpirationNotifEnabled() } returns notifEnabled
        every { settings.getExpirationNotifTimeMinutes() } returns notifTime
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = InventorySettingsViewModel(settings, scheduler)

    @Test
    fun `master toggle off cancels scheduler`() = runTest {
        val viewModel = vm()
        runCurrent()
        viewModel.onExpirationEnabledChanged(false)
        runCurrent()
        coVerify { settings.setExpirationEnabled(false) }
        verify { scheduler.cancel() }
    }

    @Test
    fun `master off then on restores alarm when notif was enabled`() = runTest {
        notifEnabled.value = true
        notifTime.value = 510
        val viewModel = vm()
        runCurrent()
        viewModel.onExpirationEnabledChanged(false)
        runCurrent()
        viewModel.onExpirationEnabledChanged(true)
        runCurrent()
        verify { scheduler.cancel() }
        verify { scheduler.scheduleDaily(510) } // re-armed on re-enable
    }

    @Test
    fun `master on does not schedule when notif disabled`() = runTest {
        notifEnabled.value = false
        val viewModel = vm()
        runCurrent()
        viewModel.onExpirationEnabledChanged(true)
        runCurrent()
        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `notif toggle on schedules at current time`() = runTest {
        notifTime.value = 540
        val viewModel = vm()
        runCurrent()
        viewModel.onNotifEnabledChanged(true)
        runCurrent()
        coVerify { settings.setExpirationNotifEnabled(true) }
        verify { scheduler.scheduleDaily(540) }
    }

    @Test
    fun `notif toggle off cancels scheduler`() = runTest {
        val viewModel = vm()
        runCurrent()
        viewModel.onNotifEnabledChanged(false)
        runCurrent()
        coVerify { settings.setExpirationNotifEnabled(false) }
        verify { scheduler.cancel() }
    }

    @Test
    fun `notif time change reschedules when notif enabled`() = runTest {
        notifEnabled.value = true
        val viewModel = vm()
        runCurrent()
        viewModel.onNotifTimeChanged(600)
        runCurrent()
        coVerify { settings.setExpirationNotifTimeMinutes(600) }
        verify { scheduler.scheduleDaily(600) }
    }

    @Test
    fun `notif time change does not schedule when notif disabled`() = runTest {
        notifEnabled.value = false
        val viewModel = vm()
        runCurrent()
        viewModel.onNotifTimeChanged(600)
        runCurrent()
        coVerify { settings.setExpirationNotifTimeMinutes(600) }
        verify(exactly = 0) { scheduler.scheduleDaily(any()) }
    }

    @Test
    fun `valid days input persists`() = runTest {
        val viewModel = vm()
        runCurrent()
        viewModel.onDaysChanged("7")
        runCurrent()
        coVerify { settings.setExpirationDays(7) }
        assertEquals(null, viewModel.uiState.value.validationError)
    }

    @Test
    fun `invalid days input shows error and does not persist`() = runTest {
        val viewModel = vm()
        runCurrent()
        viewModel.onDaysChanged("0")
        runCurrent()
        assertEquals("Must be at least 1 day", viewModel.uiState.value.validationError)
        coVerify(exactly = 0) { settings.setExpirationDays(any()) }
    }

    @Test
    fun `blank days input shows error and does not persist`() = runTest {
        val viewModel = vm()
        runCurrent()
        viewModel.onDaysChanged("")
        runCurrent()
        assertEquals("Must be at least 1 day", viewModel.uiState.value.validationError)
        coVerify(exactly = 0) { settings.setExpirationDays(any()) }
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.inventory.InventorySettingsViewModelTest" -PfastTests`

---

## Task 2: Implement the ViewModel

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/inventory/InventorySettingsViewModel.kt`

- [ ] **Step 1: Write the ViewModel**

```kotlin
package com.babytracker.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.StashExpirationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventorySettingsUiState(
    val expirationEnabled: Boolean = false,
    val expirationDays: String = "4",
    val notifEnabled: Boolean = false,
    val notifTimeMinutes: Int = 480,
    val validationError: String? = null,
    val showTimePicker: Boolean = false,
)

@HiltViewModel
class InventorySettingsViewModel @Inject constructor(
    private val settings: InventorySettingsRepository,
    private val scheduler: StashExpirationScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventorySettingsUiState())
    val uiState: StateFlow<InventorySettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settings.getExpirationEnabled(),
                settings.getExpirationDays(),
                settings.getExpirationNotifEnabled(),
                settings.getExpirationNotifTimeMinutes(),
            ) { enabled, days, notifEnabled, notifTime ->
                Quad(enabled, days, notifEnabled, notifTime)
            }.collect { (enabled, days, notifEnabled, notifTime) ->
                _uiState.value = _uiState.value.copy(
                    expirationEnabled = enabled,
                    // Only sync the days field from the store when it is not showing a
                    // validation error, so the user's in-progress invalid edit is preserved.
                    expirationDays = if (_uiState.value.validationError == null) {
                        days.toString()
                    } else {
                        _uiState.value.expirationDays
                    },
                    notifEnabled = notifEnabled,
                    notifTimeMinutes = notifTime,
                )
            }
        }
    }

    fun onExpirationEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            settings.setExpirationEnabled(enabled)
            if (enabled) {
                // Restore the alarm if the notification was left enabled from before the
                // master toggle was switched off, so state and scheduling stay consistent.
                if (_uiState.value.notifEnabled) scheduler.scheduleDaily(_uiState.value.notifTimeMinutes)
            } else {
                scheduler.cancel()
            }
        }
    }

    fun onDaysChanged(input: String) {
        val parsed = input.toIntOrNull()
        if (parsed == null || parsed < MIN_DAYS) {
            _uiState.value = _uiState.value.copy(
                expirationDays = input,
                validationError = "Must be at least 1 day",
            )
            return
        }
        _uiState.value = _uiState.value.copy(expirationDays = input, validationError = null)
        viewModelScope.launch { settings.setExpirationDays(parsed) }
    }

    fun onNotifEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            settings.setExpirationNotifEnabled(enabled)
            if (enabled) {
                scheduler.scheduleDaily(_uiState.value.notifTimeMinutes)
            } else {
                scheduler.cancel()
            }
        }
    }

    fun onNotifTimeChanged(minuteOfDay: Int) {
        _uiState.value = _uiState.value.copy(notifTimeMinutes = minuteOfDay, showTimePicker = false)
        viewModelScope.launch {
            settings.setExpirationNotifTimeMinutes(minuteOfDay)
            if (_uiState.value.notifEnabled) scheduler.scheduleDaily(minuteOfDay)
        }
    }

    fun onTimePickerOpen() {
        _uiState.value = _uiState.value.copy(showTimePicker = true)
    }

    fun onTimePickerDismiss() {
        _uiState.value = _uiState.value.copy(showTimePicker = false)
    }

    private data class Quad(val a: Boolean, val b: Int, val c: Boolean, val d: Int)

    private companion object {
        const val MIN_DAYS = 1
    }
}
```

- [ ] **Step 2: Run tests, expect PASS** (10 tests)

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.inventory.InventorySettingsViewModelTest" -PfastTests`

---

## Task 3: Implement the screen

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/inventory/InventorySettingsScreen.kt`

Layout (per spec): TopAppBar "Milk Stash Settings" + back; section header "EXPIRATION TRACKING"; master `Switch` row; when enabled → days `OutlinedTextField` (numeric) + "NOTIFICATIONS" header + notif `Switch`; when notif enabled → "Notify at: HH:MM" row opening a `TimePicker` dialog. Use `MaterialTheme.shapes.medium`, `labelMedium` (uppercase) for section headers, `titleMedium`/`bodyMedium` for rows.

- [ ] **Step 1: Write the composable**

```kotlin
package com.babytracker.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventorySettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InventorySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Milk Stash Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("EXPIRATION TRACKING")
            ToggleRow(
                title = "Enable expiration tracking",
                description = "Flag bags approaching their expiration date",
                checked = state.expirationEnabled,
                onCheckedChange = viewModel::onExpirationEnabledChanged,
            )

            if (state.expirationEnabled) {
                OutlinedTextField(
                    value = state.expirationDays,
                    onValueChange = viewModel::onDaysChanged,
                    label = { Text("Expires after (days)") },
                    isError = state.validationError != null,
                    supportingText = state.validationError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionHeader("NOTIFICATIONS")
                ToggleRow(
                    title = "Notify when expiring",
                    description = "Daily reminder of bags expiring today",
                    checked = state.notifEnabled,
                    onCheckedChange = viewModel::onNotifEnabledChanged,
                )

                if (state.notifEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Notify at", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = viewModel::onTimePickerOpen) {
                            Text(formatMinuteOfDay(state.notifTimeMinutes))
                        }
                    }
                }
            }
        }
    }

    if (state.showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = state.notifTimeMinutes / 60,
            initialMinute = state.notifTimeMinutes % 60,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = viewModel::onTimePickerDismiss,
            confirmButton = {
                Button(onClick = { viewModel.onNotifTimeChanged(pickerState.hour * 60 + pickerState.minute) }) {
                    Text("OK")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::onTimePickerDismiss) { Text("Cancel") } },
            text = { TimePicker(state = pickerState) },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(0.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun formatMinuteOfDay(minuteOfDay: Int): String {
    val hour = minuteOfDay / 60
    val minute = minuteOfDay % 60
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(displayHour, minute, amPm)
}
```

> Match the exact toggle-row / section-header styling of `SettingsScreen.kt` if it differs from the above; reuse any shared composable it already exposes rather than duplicating. The above is a self-contained baseline.

- [ ] **Step 2: Verify it compiles** — `./gradlew :app:compileDebugKotlin`

---

## Task 4: Quality gates and commit

- [ ] **Step 1: ktlint + detekt** — `./gradlew ktlintFormat detekt` → BUILD SUCCESSFUL
- [ ] **Step 2: Tests** — `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.inventory.InventorySettingsViewModelTest" -PfastTests` → PASS
- [ ] **Step 3: Build** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/inventory/InventorySettingsViewModel.kt \
        app/src/main/java/com/babytracker/ui/inventory/InventorySettingsScreen.kt \
        app/src/test/java/com/babytracker/ui/inventory/InventorySettingsViewModelTest.kt
git commit -m "feat(inventory): add InventorySettings screen and ViewModel [AKA-77]"
```

---

## Acceptance Criteria

- ViewModel exposes a single `StateFlow<InventorySettingsUiState>` built from the four repo Flows.
- Scheduler side effects match the contract table exactly (master-off → `cancel`; master-on → `scheduleDaily` iff notif already enabled; notif-on → `scheduleDaily`; notif-off → `cancel`; time change → `scheduleDaily` only when notif enabled). Covered by a notif-enabled → master-off → master-on **sequence** test, not just single actions.
- Days field validates `>= 1`; invalid/blank input shows an error and does **not** persist; valid input persists (repo also coerces).
- An in-progress invalid days edit is not overwritten by the store flow.
- Time picker shows the current time and writes minute-of-day on confirm.
- Conditional disclosure: days + notif section only when master enabled; time row only when notif enabled.
- All 10 ViewModel tests pass; the screen compiles and the app builds.
