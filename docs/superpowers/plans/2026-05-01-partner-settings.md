# Partner Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Settings bottom NavigationBar to `PartnerDashboardScreen` and filter `SettingsScreen` sections for partner users, including a Disconnect action that clears partner mode and returns to home/onboarding.

**Architecture:** Reuses the existing `SettingsScreen` with a nullable-`appMode` loading guard (`AppMode? = null`) and conditional rendering to hide Baby Profile, Feeding Limits, and Notifications for PARTNER users. `PartnerDashboardScreen` gains a bottom `NavigationBar` mirroring `HomeScreen`'s pattern. `AppNavGraph` wires the new navigation and disconnect paths. No new routes or files are created.

**Tech Stack:** Jetpack Compose, Hilt, `SettingsRepository`, Kotlin Coroutines / `StateFlow`, MockK, Compose UI Test (`createAndroidComposeRule`)

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `ui/settings/SettingsViewModel.kt` | Modify | Add `appMode: AppMode? = null`, `isDisconnected: Boolean`, `disconnect()` |
| `ui/settings/SettingsScreen.kt` | Modify | Add `onDisconnect` param, `LaunchedEffect`, PARTNER filtering, Disconnect `SettingsRow`; extend `SettingsRow` with `actionLabel`/`actionColor` |
| `ui/partner/PartnerDashboardScreen.kt` | Modify | Add `onNavigateToSettings` param and `NavigationBar` `bottomBar` |
| `navigation/AppNavGraph.kt` | Modify | Wire `onNavigateToSettings` → `Routes.SETTINGS`; wire `onDisconnect` → HOME/ONBOARDING with full stack pop |
| `test/…/settings/SettingsViewModelTest.kt` | Modify | Add tests for `disconnect()` ordering and nullable `appMode` |
| `androidTest/…/partner/PartnerDashboardScreenTest.kt` | Create | UI tests: NavigationBarItem visible, tap triggers callback |
| `androidTest/…/settings/SettingsScreenTest.kt` | Create | UI tests: PARTNER filtering and Disconnect row |

---

## Task 1: SettingsViewModel — nullable appMode, isDisconnected, disconnect()

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Update `SettingsUiState`**

In `SettingsViewModel.kt`, change `appMode` from `AppMode = AppMode.NONE` to `AppMode? = null` and add `isDisconnected`:

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
)
```

- [ ] **Step 2: Add `disconnect()` and missing import**

Add `import kotlinx.coroutines.flow.update` to the import block, then add `disconnect()` at the bottom of `SettingsViewModel`:

```kotlin
fun disconnect() {
    viewModelScope.launch {
        settingsRepository.setAppMode(AppMode.NONE)
        settingsRepository.clearShareCode()
        _uiState.update { it.copy(isDisconnected = true) }
    }
}
```

The full updated `SettingsViewModel.kt` should look like:

```kotlin
package com.babytracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val baby: Baby? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val autoUpdateEnabled: Boolean = true,
    val richNotificationsEnabled: Boolean = true,
    val appMode: AppMode? = null,
    val isDisconnected: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getBabyProfile: GetBabyProfileUseCase,
    private val settingsRepository: SettingsRepository,
    private val saveBabyProfile: SaveBabyProfileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getBabyProfile(),
                settingsRepository.getMaxPerBreastMinutes(),
                settingsRepository.getMaxTotalFeedMinutes(),
                settingsRepository.getThemeConfig(),
                settingsRepository.getAutoUpdateEnabled(),
                settingsRepository.getRichNotificationsEnabled(),
                settingsRepository.getAppMode(),
            ) { values ->
                val baby = values[0] as? Baby
                val maxPerBreast = values[1] as Int
                val maxTotal = values[2] as Int
                val themeConfig = values[3] as ThemeConfig
                val autoUpdate = values[4] as Boolean
                val richNotifications = values[5] as Boolean
                val appMode = values[6] as AppMode
                SettingsUiState(
                    baby = baby,
                    maxPerBreastMinutes = maxPerBreast,
                    maxTotalFeedMinutes = maxTotal,
                    themeConfig = themeConfig,
                    autoUpdateEnabled = autoUpdate,
                    richNotificationsEnabled = richNotifications,
                    appMode = appMode,
                )
            }.collect { next ->
                _uiState.update { current -> next.copy(isDisconnected = current.isDisconnected) }
            }
        }
    }

    fun onThemeConfigChanged(themeConfig: ThemeConfig) {
        viewModelScope.launch { settingsRepository.setThemeConfig(themeConfig) }
    }

    fun onMaxPerBreastChanged(minutes: Int) {
        viewModelScope.launch { settingsRepository.setMaxPerBreastMinutes(minutes) }
    }

    fun onMaxTotalFeedChanged(minutes: Int) {
        viewModelScope.launch { settingsRepository.setMaxTotalFeedMinutes(minutes) }
    }

    fun onSaveBabyProfile(baby: Baby) {
        viewModelScope.launch { saveBabyProfile(baby) }
    }

    fun onAutoUpdateChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoUpdateEnabled(enabled) }
    }

    fun onRichNotificationsToggled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRichNotificationsEnabled(enabled) }
    }

    fun disconnect() {
        viewModelScope.launch {
            settingsRepository.setAppMode(AppMode.NONE)
            settingsRepository.clearShareCode()
            _uiState.update { it.copy(isDisconnected = true) }
        }
    }
}
```

- [ ] **Step 3: Write tests for the new behaviour**

Replace the content of `SettingsViewModelTest.kt` with (keep existing tests, add 3 new ones):

```kotlin
package com.babytracker.ui.settings

import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import com.babytracker.sharing.domain.model.AppMode
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: SettingsViewModel
    private val richNotificationsFlow = MutableStateFlow(true)
    private val appModeFlow = MutableStateFlow(AppMode.NONE)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk()
        val getBabyProfile = mockk<GetBabyProfileUseCase>()
        every { getBabyProfile() } returns flowOf(null)
        every { settingsRepository.getMaxPerBreastMinutes() } returns flowOf(0)
        every { settingsRepository.getMaxTotalFeedMinutes() } returns flowOf(0)
        every { settingsRepository.getThemeConfig() } returns flowOf(ThemeConfig.SYSTEM)
        every { settingsRepository.getAutoUpdateEnabled() } returns flowOf(true)
        every { settingsRepository.getRichNotificationsEnabled() } returns richNotificationsFlow
        every { settingsRepository.getAppMode() } returns appModeFlow

        viewModel = SettingsViewModel(getBabyProfile, settingsRepository, mockk())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has richNotificationsEnabled true`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.richNotificationsEnabled)
    }

    @Test
    fun `onRichNotificationsToggled false calls repository and updates state`() = runTest {
        coEvery { settingsRepository.setRichNotificationsEnabled(false) } coAnswers {
            richNotificationsFlow.value = false
        }

        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onRichNotificationsToggled(false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsRepository.setRichNotificationsEnabled(false) }
        assertFalse(viewModel.uiState.value.richNotificationsEnabled)
    }

    @Test
    fun `appMode is null before combine flow emits`() {
        // ViewModel is created in setup(); before advancing the dispatcher the init
        // coroutine has not yet collected, so appMode stays at its default null.
        assertNull(viewModel.uiState.value.appMode)
    }

    @Test
    fun `appMode emits NONE after combine flow fires`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppMode.NONE, viewModel.uiState.value.appMode)
    }

    @Test
    fun `disconnect calls setAppMode then clearShareCode in order and sets isDisconnected`() = runTest {
        coJustRun { settingsRepository.setAppMode(AppMode.NONE) }
        coJustRun { settingsRepository.clearShareCode() }

        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.disconnect()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerifyOrder {
            settingsRepository.setAppMode(AppMode.NONE)
            settingsRepository.clearShareCode()
        }
        assertTrue(viewModel.uiState.value.isDisconnected)
    }

    @Test
    fun `new combine emission after disconnect preserves isDisconnected flag`() = runTest {
        coJustRun { settingsRepository.setAppMode(AppMode.NONE) }
        coJustRun { settingsRepository.clearShareCode() }

        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.disconnect()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDisconnected)

        // Simulate a new DataStore emission arriving after disconnect completes;
        // without the update { current -> next.copy(isDisconnected = ...) } guard
        // in the collect block, this would reset isDisconnected to false.
        appModeFlow.value = AppMode.NONE
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDisconnected)
    }
}
```

- [ ] **Step 4: Run unit tests**

```
./gradlew :app:test --tests "com.babytracker.ui.settings.SettingsViewModelTest"
```

Expected: BUILD SUCCESSFUL, 6 tests passed.

- [ ] **Step 5: Spec review**

Check against the spec:
- `appMode: AppMode? = null` — ✓ default is null, not `NONE`
- `isDisconnected: Boolean = false` — ✓ present in `SettingsUiState`
- `disconnect()` calls `setAppMode(NONE)` then `clearShareCode()` then sets `isDisconnected = true` — ✓ verified by test ordering check
- All three repo operations happen inside the same `viewModelScope.launch` coroutine — ✓
- `collect` block uses `update { current -> next.copy(isDisconnected = current.isDisconnected) }` so a DataStore emission arriving concurrently cannot reset the flag — ✓

- [ ] **Step 6: Code review**

```
./gradlew ktlintFormat detekt
```

Fix any violations before committing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt \
        app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): add nullable appMode loading guard and disconnect() to SettingsViewModel"
```

---

## Task 2: SettingsRow — actionLabel and actionColor parameters

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt` (only the `SettingsRow` private composable)

- [ ] **Step 1: Add `Color` import**

Add to the import block of `SettingsScreen.kt`:

```kotlin
import androidx.compose.ui.graphics.Color
```

- [ ] **Step 2: Update the `SettingsRow` signature and body**

Replace the existing `SettingsRow` composable (currently at line 406) with:

```kotlin
@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    actionLabel: String = "Edit",
    actionColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) {
            Text(actionLabel, color = actionColor)
        }
    }
}
```

All existing call sites of `SettingsRow` omit `actionLabel` and `actionColor`, so they continue working unchanged via Kotlin's default parameters.

- [ ] **Step 3: Build to verify zero compilation errors**

```
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Spec review**

Check against the spec:
- `actionLabel: String = "Edit"` — ✓ matches spec; all existing rows continue to show "Edit" by default
- `actionColor: Color = MaterialTheme.colorScheme.primary` — ✓ matches spec default

- [ ] **Step 5: Code review**

```
./gradlew ktlintFormat detekt
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): add actionLabel and actionColor parameters to SettingsRow"
```

---

## Task 3: SettingsScreen — PARTNER filtering, Disconnect row, onDisconnect, LaunchedEffect

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add missing imports**

Add to the import block:

```kotlin
import androidx.compose.runtime.LaunchedEffect
```

- [ ] **Step 2: Add `onDisconnect` parameter to `SettingsScreen`**

Change the function signature from:

```kotlin
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDesignSystem: () -> Unit = {},
    onNavigateToManageSharing: () -> Unit = {},
    onNavigateToConnectPartner: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
)
```

To:

```kotlin
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDesignSystem: () -> Unit = {},
    onNavigateToManageSharing: () -> Unit = {},
    onNavigateToConnectPartner: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
)
```

- [ ] **Step 3: Add `LaunchedEffect` for disconnect navigation**

Immediately after:
```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
var activeSheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }
```

Add:

```kotlin
LaunchedEffect(uiState.isDisconnected) {
    if (uiState.isDisconnected) onDisconnect()
}
```

- [ ] **Step 4: Wrap Baby Profile + Feeding Limits sections with PARTNER guard**

Replace the Baby Profile and Feeding Limits sections (currently from `// Baby Profile section` through `HorizontalDivider()` after the Feeding Limits section) with:

```kotlin
if (uiState.appMode != null && uiState.appMode != AppMode.PARTNER) {
    // Baby Profile section
    Text(
        text = "Baby Profile",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )

    SettingsRow(
        label = "Name",
        value = uiState.baby?.name ?: "Not set",
        onClick = { activeSheet = SettingsSheet.NAME }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val birthDateText = uiState.baby?.let { baby ->
        "${baby.birthDate.format(dateFormatter)} (${baby.ageInWeeks}w old)"
    } ?: "Not set"
    SettingsRow(
        label = "Date of birth",
        value = birthDateText,
        onClick = { activeSheet = SettingsSheet.BIRTH_DATE }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

    // Allergies row with chips
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { activeSheet = SettingsSheet.ALLERGIES }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Allergies",
                style = MaterialTheme.typography.bodyLarge,
            )
            val allergies = uiState.baby?.allergies.orEmpty()
            if (allergies.isEmpty()) {
                Text(
                    text = "None",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    allergies.forEach { allergy ->
                        SuggestionChip(
                            onClick = { activeSheet = SettingsSheet.ALLERGIES },
                            label = { Text(allergy.label) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
            }
        }
        TextButton(onClick = { activeSheet = SettingsSheet.ALLERGIES }) {
            Text("Edit")
        }
    }

    HorizontalDivider()

    // Feeding Limits section
    Text(
        text = "Feeding Limits",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )

    SettingsRow(
        label = "Max per breast",
        value = if (uiState.maxPerBreastMinutes > 0) "${uiState.maxPerBreastMinutes} min" else "Disabled",
        onClick = { activeSheet = SettingsSheet.MAX_PER_BREAST }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

    SettingsRow(
        label = "Max total feed",
        value = if (uiState.maxTotalFeedMinutes > 0) "${uiState.maxTotalFeedMinutes} min" else "Disabled",
        onClick = { activeSheet = SettingsSheet.MAX_TOTAL_FEED }
    )

    HorizontalDivider()
}
```

- [ ] **Step 5: Wrap Notifications section with PARTNER guard**

Replace the Notifications section (from `HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))` through `HorizontalDivider()` after the Switch row) with:

```kotlin
if (uiState.appMode != null && uiState.appMode != AppMode.PARTNER) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text(
        text = "NOTIFICATIONS",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Rich notifications", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Show progress bars and quick actions on notifications",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = uiState.richNotificationsEnabled,
            onCheckedChange = { viewModel.onRichNotificationsToggled(it) }
        )
    }

    HorizontalDivider()
}
```

- [ ] **Step 6: Update the `when (uiState.appMode)` block to handle null and PARTNER**

Replace the existing `when (uiState.appMode)` block (currently `AppMode.PARTNER -> Unit`) with:

```kotlin
when (uiState.appMode) {
    AppMode.NONE -> {
        SettingsRow(
            label = "Partner Sharing",
            value = "Set up as primary parent",
            onClick = onNavigateToManageSharing,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        SettingsRow(
            label = "Connect as Partner",
            value = "View a partner's baby data",
            onClick = onNavigateToConnectPartner,
        )
    }
    AppMode.PRIMARY -> SettingsRow(
        label = "Manage Sharing",
        value = "Active",
        onClick = onNavigateToManageSharing,
    )
    AppMode.PARTNER -> SettingsRow(
        label = "Disconnect",
        value = "Stop viewing as partner",
        actionLabel = "Disconnect",
        actionColor = MaterialTheme.colorScheme.error,
        onClick = viewModel::disconnect,
    )
    null -> Unit
}
```

- [ ] **Step 7: Build to verify zero compilation errors**

```
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Spec review**

Check against the spec:
- `onDisconnect: () -> Unit = {}` parameter present — ✓
- `LaunchedEffect(uiState.isDisconnected)` fires `onDisconnect()` only when `isDisconnected == true` — ✓
- Baby Profile and Feeding Limits wrapped with `appMode != null && appMode != AppMode.PARTNER` — ✓
- Notifications section wrapped the same way — ✓
- `AppMode.PARTNER` branch shows Disconnect `SettingsRow` with correct label, value, actionLabel, and error color — ✓
- `null -> Unit` handles the loading state — ✓

- [ ] **Step 9: Code review**

```
./gradlew ktlintFormat detekt
```

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): filter sections for PARTNER mode and add Disconnect row"
```

---

## Task 4: PartnerDashboardScreen — onNavigateToSettings + bottom NavigationBar

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt`

- [ ] **Step 1: Add imports**

Add to the import block:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
```

- [ ] **Step 2: Add `onNavigateToSettings` parameter**

Change the function signature from:

```kotlin
fun PartnerDashboardScreen(
    onDisconnected: () -> Unit = {},
    viewModel: PartnerDashboardViewModel = hiltViewModel(),
)
```

To:

```kotlin
fun PartnerDashboardScreen(
    onDisconnected: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: PartnerDashboardViewModel = hiltViewModel(),
)
```

- [ ] **Step 3: Add `bottomBar` to the `Scaffold`**

Change the `Scaffold(topBar = { ... }) { ... }` call to include `bottomBar`:

```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Baby Tracker") },
            actions = {
                TextButton(
                    onClick = viewModel::refresh,
                    enabled = !uiState.isLoading,
                ) {
                    Text("↻ Refresh")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    },
    bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            NavigationBarItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings") },
                selected = false,
                onClick = onNavigateToSettings,
            )
        }
    },
) { padding ->
    // existing Box content unchanged
```

- [ ] **Step 4: Build to verify zero compilation errors**

```
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Spec review**

Check against the spec:
- `onNavigateToSettings: () -> Unit = {}` parameter — ✓
- `NavigationBar` in `bottomBar` with a single `NavigationBarItem` for Settings — ✓
- `selected = false` (partner dashboard never has Settings as the "active" tab) — ✓
- TopAppBar (title + Refresh button) unchanged — ✓

- [ ] **Step 6: Code review**

```
./gradlew ktlintFormat detekt
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt
git commit -m "feat(partner): add Settings NavigationBar to PartnerDashboardScreen"
```

---

## Task 5: AppNavGraph — wire onNavigateToSettings and onDisconnect

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`

- [ ] **Step 1: Wire `onNavigateToSettings` in the PARTNER_DASHBOARD composable**

Replace the existing `PARTNER_DASHBOARD` block:

```kotlin
composable(Routes.PARTNER_DASHBOARD) {
    PartnerDashboardScreen(
        onDisconnected = {
            val destination = if (isOnboardingComplete) Routes.HOME else Routes.ONBOARDING
            navController.navigate(destination) { popUpTo(0) { inclusive = true } }
        },
    )
}
```

With:

```kotlin
composable(Routes.PARTNER_DASHBOARD) {
    PartnerDashboardScreen(
        onDisconnected = {
            val destination = if (isOnboardingComplete) Routes.HOME else Routes.ONBOARDING
            navController.navigate(destination) { popUpTo(0) { inclusive = true } }
        },
        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
    )
}
```

- [ ] **Step 2: Wire `onDisconnect` in the SETTINGS composable**

Replace the existing `SETTINGS` block:

```kotlin
composable(Routes.SETTINGS) {
    SettingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToDesignSystem = { navController.navigate(Routes.DESIGN_SYSTEM_PREVIEW) },
        onNavigateToManageSharing = { navController.navigate(Routes.MANAGE_SHARING) },
        onNavigateToConnectPartner = { navController.navigate(Routes.CONNECT_PARTNER) },
    )
}
```

With:

```kotlin
composable(Routes.SETTINGS) {
    SettingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToDesignSystem = { navController.navigate(Routes.DESIGN_SYSTEM_PREVIEW) },
        onNavigateToManageSharing = { navController.navigate(Routes.MANAGE_SHARING) },
        onNavigateToConnectPartner = { navController.navigate(Routes.CONNECT_PARTNER) },
        onDisconnect = {
            val destination = if (isOnboardingComplete) Routes.HOME else Routes.ONBOARDING
            navController.navigate(destination) { popUpTo(0) { inclusive = true } }
        },
    )
}
```

- [ ] **Step 3: Build to verify zero compilation errors**

```
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Spec review**

Check against the spec:
- `PARTNER_DASHBOARD` routes to `Routes.SETTINGS` via `onNavigateToSettings` — ✓
- `SETTINGS` clears the full back stack (`popUpTo(0) { inclusive = true }`) on disconnect — ✓
- Disconnect destination is `Routes.HOME` if onboarding is complete, `Routes.ONBOARDING` otherwise — ✓

- [ ] **Step 5: Code review**

```
./gradlew ktlintFormat detekt
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/navigation/AppNavGraph.kt
git commit -m "feat(navigation): wire Settings and Disconnect routes for PartnerDashboardScreen"
```

---

## Task 6: PartnerDashboardScreen UI tests

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt`

> **Note on test strategy:** `PartnerDashboardScreen` accepts `viewModel: PartnerDashboardViewModel = hiltViewModel()` as an overridable parameter. In these tests we construct `PartnerDashboardViewModel` manually with a mocked use case — no Hilt test infrastructure is needed. `createAndroidComposeRule<ComponentActivity>()` provides the `LifecycleOwner` required by `collectAsStateWithLifecycle`.
>
> `mockk` in `testImplementation` only covers JVM unit tests. Instrumentation tests run on an Android device/emulator and require the `mockk-android` artifact which bundles byte-buddy-android.

- [ ] **Step 1: Add `mockk-android` to `libs.versions.toml`**

In `gradle/libs.versions.toml`, add to the `[libraries]` section alongside the existing `mockk` entry:

```toml
mockk-android = { group = "io.mockk", name = "mockk-android", version.ref = "mockk" }
```

- [ ] **Step 2: Add `mockk-android` to `app/build.gradle.kts`**

In the `dependencies` block, add after `androidTestImplementation(libs.compose.ui.test.junit4)`:

```kotlin
androidTestImplementation(libs.mockk.android)
```

- [ ] **Step 3: Sync Gradle**

```
./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL (confirms the new dependency resolves).

- [ ] **Step 4: Create the test file**

Create `app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt`:

```kotlin
package com.babytracker.ui.partner

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PartnerDashboardScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val mockFetchUseCase: FetchPartnerDataUseCase = mockk {
        coEvery { invoke() } throws RuntimeException("offline")
    }

    @Test
    fun settingsNavigationBarItemIsDisplayed() {
        val viewModel = PartnerDashboardViewModel(mockFetchUseCase)
        composeRule.setContent {
            PartnerDashboardScreen(
                onNavigateToSettings = {},
                viewModel = viewModel,
            )
        }
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun tappingSettingsItemTriggersOnNavigateToSettings() {
        var called = false
        val viewModel = PartnerDashboardViewModel(mockFetchUseCase)
        composeRule.setContent {
            PartnerDashboardScreen(
                onNavigateToSettings = { called = true },
                viewModel = viewModel,
            )
        }
        composeRule.onNodeWithText("Settings").performClick()
        assertTrue(called)
    }
}
```

- [ ] **Step 2: Run the UI tests on a connected device/emulator**

```
./gradlew :app:connectedAndroidTest --tests "com.babytracker.ui.partner.PartnerDashboardScreenTest"
```

Expected: BUILD SUCCESSFUL, 2 tests passed.

- [ ] **Step 3: Spec review**

Check against the spec:
- Settings `NavigationBarItem` is displayed — ✓ verified by `assertIsDisplayed()`
- Tapping it triggers `onNavigateToSettings` — ✓ verified by callback flag

- [ ] **Step 4: Code review**

```
./gradlew ktlintFormat detekt
```

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt
git commit -m "test(partner): add UI tests for Settings NavigationBarItem in PartnerDashboardScreen"
```

---

## Task 7: SettingsScreen PARTNER mode UI tests

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt`

> **Note on test strategy:** `SettingsScreen` accepts `viewModel: SettingsViewModel = hiltViewModel()` as an overridable parameter. We construct `SettingsViewModel` manually with mocked repositories that emit `AppMode.PARTNER` — no Hilt test infrastructure needed. `waitForIdle()` ensures the combine flow in `SettingsViewModel.init` has collected and emitted the state before assertions run.

- [ ] **Step 1: Create the test file**

Create `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt`:

```kotlin
package com.babytracker.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasRole
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import com.babytracker.sharing.domain.model.AppMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildPartnerViewModel(): SettingsViewModel {
        val getBabyProfile = mockk<GetBabyProfileUseCase> {
            every { invoke() } returns flowOf(null)
        }
        val settingsRepository = mockk<SettingsRepository> {
            every { getMaxPerBreastMinutes() } returns flowOf(0)
            every { getMaxTotalFeedMinutes() } returns flowOf(0)
            every { getThemeConfig() } returns flowOf(ThemeConfig.SYSTEM)
            every { getAutoUpdateEnabled() } returns flowOf(true)
            every { getRichNotificationsEnabled() } returns flowOf(true)
            every { getAppMode() } returns flowOf(AppMode.PARTNER)
        }
        return SettingsViewModel(getBabyProfile, settingsRepository, mockk<SaveBabyProfileUseCase>())
    }

    @Test
    fun partnerModeHidesBabyProfileSection() {
        val viewModel = buildPartnerViewModel()
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Baby Profile").assertDoesNotExist()
    }

    @Test
    fun partnerModeHidesFeedingLimitsSection() {
        val viewModel = buildPartnerViewModel()
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Feeding Limits").assertDoesNotExist()
    }

    @Test
    fun partnerModeHidesNotificationsSection() {
        val viewModel = buildPartnerViewModel()
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NOTIFICATIONS").assertDoesNotExist()
    }

    @Test
    fun partnerModeShowsDisconnectRow() {
        val viewModel = buildPartnerViewModel()
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = viewModel)
        }
        composeRule.waitForIdle()
        // The Disconnect SettingsRow renders both a label Text("Disconnect")
        // and a button Text("Disconnect") — asserting the first is enough.
        composeRule.onAllNodesWithText("Disconnect")[0].assertIsDisplayed()
    }

    @Test
    fun partnerModeDisconnectRowButtonIsLabelledDisconnect() {
        val viewModel = buildPartnerViewModel()
        composeRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = viewModel)
        }
        composeRule.waitForIdle()
        // TextButton sets role = Role.Button in semantics; the outer Row's clickable does not.
        // This matcher uniquely targets the action button inside the SettingsRow and verifies
        // its label is "Disconnect" — not "Edit". If the actionLabel defaulted to "Edit"
        // this test would fail because no Button-role node with text "Disconnect" would exist.
        composeRule.onNode(hasText("Disconnect") and hasRole(Role.Button))
            .assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the UI tests on a connected device/emulator**

```
./gradlew :app:connectedAndroidTest --tests "com.babytracker.ui.settings.SettingsScreenTest"
```

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 3: Spec review**

Check against the spec:
- Baby Profile section absent in PARTNER mode — ✓ `assertDoesNotExist()`
- Feeding Limits section absent — ✓
- Notifications section absent — ✓
- Disconnect row present — ✓ `assertIsDisplayed()`
- Disconnect button label is "Disconnect" not "Edit" — ✓ `hasRole(Role.Button)` targets the `TextButton` specifically; if `actionLabel` were "Edit" the test would fail

- [ ] **Step 4: Code review**

```
./gradlew ktlintFormat detekt
```

- [ ] **Step 5: Run full test suite to check for regressions**

```
./gradlew :app:test
./gradlew :app:connectedAndroidTest
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt
git commit -m "test(settings): add UI tests for SettingsScreen PARTNER mode filtering"
```
