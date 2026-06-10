# Volume Unit Preference (ml/oz) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-109

**Goal:** Add a global ml/oz volume-unit preference (DataStore-backed), a formatting utility, a Settings toggle, and backfill existing pumping/inventory volume displays to honour it.

**Architecture:** Volume is always **stored** in millilitres everywhere (Room, DataStore, backup). The unit preference is a **presentation** concern: a `VolumeUnit` enum persisted via the existing `SettingsRepository`/DataStore, read by UI layers, and applied through a single `formatVolume(volumeMl, unit)` extension. The Settings screen gets a segmented ml/oz selector. Pumping and inventory screens swap their hard-coded "mL" strings for `formatVolume`.

**Tech Stack:** Kotlin, DataStore Preferences, Hilt, Compose Material 3 (`SingleChoiceSegmentedButtonRow`), JUnit 5 + MockK + Turbine, Compose UI test.

**Dependencies:** None (independent of plan 01; can merge in parallel). Plans 04/05/06/07 consume `formatVolume` + `VolumeUnit`.

**Suggested implementation branch:** `feat/volume-unit-preference`

---

## File Structure

- Create `app/src/main/java/com/babytracker/domain/model/VolumeUnit.kt` — enum `ML`, `OZ`.
- Create `app/src/main/java/com/babytracker/util/VolumeExt.kt` — `formatVolume`, `mlToOz`.
- Modify `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt` — add `getVolumeUnit()` / `setVolumeUnit()`.
- Modify `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt` — DataStore key + impl.
- Modify `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt` — expose `volumeUnit`, add `onVolumeUnitChanged`.
- Modify `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt` — segmented ml/oz selector. **[impeccable]**
- Modify `app/src/main/java/com/babytracker/ui/pumping/PumpingViewModel.kt` + `PumpingScreen.kt` — backfill volume display.
- Modify `app/src/main/java/com/babytracker/ui/inventory/InventoryViewModel.kt` + `InventoryScreen.kt` + `AddBagSheet.kt` label — backfill volume display.
- Tests: `app/src/test/java/com/babytracker/util/VolumeExtTest.kt`, extend `SettingsRepositoryImpl`/`SettingsViewModel` tests, `app/src/androidTest/.../SettingsScreenTest` (toggle).

> Note on storage: this plan changes **display only**. The volume input fields in `AddBagSheet`/pumping continue to accept and store millilitres. (If oz *input* is later desired, that is a separate scoped change — out of scope here per YAGNI.)

---

## Task 1: VolumeUnit enum

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/VolumeUnit.kt`

- [ ] **Step 1: Create the enum**

```kotlin
package com.babytracker.domain.model

enum class VolumeUnit {
    ML,
    OZ,
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/VolumeUnit.kt
git commit -m "feat(settings): add VolumeUnit enum"
```

---

## Task 2: formatVolume / mlToOz utility (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/util/VolumeExt.kt`
- Test: `app/src/test/java/com/babytracker/util/VolumeExtTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.util

import com.babytracker.domain.model.VolumeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VolumeExtTest {

    @Test
    fun `formatVolume in ML shows whole millilitres`() {
        assertEquals("120 ml", formatVolume(120, VolumeUnit.ML))
    }

    @Test
    fun `formatVolume in OZ converts and rounds to one decimal`() {
        // 120 ml / 29.5735 = 4.057 -> 4.1 oz
        assertEquals("4.1 oz", formatVolume(120, VolumeUnit.OZ))
    }

    @Test
    fun `formatVolume in OZ drops trailing zero decimal`() {
        // 295.735 ml ~ 10 oz; 296 ml -> 10.0 -> "10 oz"
        assertEquals("10 oz", formatVolume(296, VolumeUnit.OZ))
    }

    @Test
    fun `mlToOz converts using US fluid ounce`() {
        // 30 ml / 29.5735 = 1.0144 oz
        assertEquals(1.0144, mlToOz(30), 0.001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.util.VolumeExtTest"`
Expected: FAIL — `formatVolume` unresolved.

- [ ] **Step 3: Write the utility**

```kotlin
package com.babytracker.util

import com.babytracker.domain.model.VolumeUnit
import java.util.Locale
import kotlin.math.roundToInt

private const val ML_PER_US_FL_OZ = 29.5735

fun mlToOz(volumeMl: Int): Double = volumeMl / ML_PER_US_FL_OZ

/**
 * Formats a millilitre volume for display in the user's chosen [unit].
 * ML -> "120 ml". OZ -> one decimal, trailing ".0" dropped ("4.1 oz", "10 oz").
 */
fun formatVolume(volumeMl: Int, unit: VolumeUnit): String = when (unit) {
    VolumeUnit.ML -> "$volumeMl ml"
    VolumeUnit.OZ -> {
        val oz = mlToOz(volumeMl)
        val rounded = (oz * 10).roundToInt() / 10.0
        val text = if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
        "$text oz"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.babytracker.util.VolumeExtTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/util/VolumeExt.kt app/src/test/java/com/babytracker/util/VolumeExtTest.kt
git commit -m "feat(util): add volume formatting for ml/oz"
```

---

## Task 3: Persist VolumeUnit in SettingsRepository (TDD)

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt`
- Test: extend `app/src/test/java/com/babytracker/data/repository/SettingsRepositoryImplTest.kt` (create if absent following the existing repository-test pattern with an in-memory/test DataStore)

- [ ] **Step 1: Add interface methods** (in `SettingsRepository`, near `getThemeConfig`)

```kotlin
fun getVolumeUnit(): kotlinx.coroutines.flow.Flow<com.babytracker.domain.model.VolumeUnit>
suspend fun setVolumeUnit(unit: com.babytracker.domain.model.VolumeUnit)
```

(Prefer top-of-file imports `import com.babytracker.domain.model.VolumeUnit` and `Flow` already present — use the short names.)

- [ ] **Step 2: Write the failing test** (add to the settings repository test class)

```kotlin
@Test
fun `volume unit defaults to ML and round-trips`() = runTest {
    assertEquals(VolumeUnit.ML, repository.getVolumeUnit().first())

    repository.setVolumeUnit(VolumeUnit.OZ)

    assertEquals(VolumeUnit.OZ, repository.getVolumeUnit().first())
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew test --tests "com.babytracker.data.repository.SettingsRepositoryImplTest"`
Expected: FAIL — method unresolved.

- [ ] **Step 4: Implement in `SettingsRepositoryImpl`**

Add the DataStore key alongside the other keys in its companion:
```kotlin
val VOLUME_UNIT = stringPreferencesKey("volume_unit")
```
Add the methods:
```kotlin
override fun getVolumeUnit(): Flow<VolumeUnit> =
    dataStore.data.map { prefs ->
        prefs[VOLUME_UNIT]?.let { runCatching { VolumeUnit.valueOf(it) }.getOrNull() } ?: VolumeUnit.ML
    }

override suspend fun setVolumeUnit(unit: VolumeUnit) {
    dataStore.edit { it[VOLUME_UNIT] = unit.name }
}
```
Add imports: `import com.babytracker.domain.model.VolumeUnit` and (if missing) `import androidx.datastore.preferences.core.stringPreferencesKey`.

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew test --tests "com.babytracker.data.repository.SettingsRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/repository/SettingsRepository.kt app/src/main/java/com/babytracker/data/repository/SettingsRepositoryImpl.kt app/src/test/java/com/babytracker/data/repository/SettingsRepositoryImplTest.kt
git commit -m "feat(settings): persist volume unit preference"
```

---

## Task 4: Expose volume unit in SettingsViewModel (TDD)

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt`
- Test: extend `app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt` (if present) or add focused test

The VM already `combine`s 17 flows via the vararg form. Add the volume-unit flow as `values[17]`.

- [ ] **Step 1: Add to `SettingsUiState`**

```kotlin
val volumeUnit: VolumeUnit = VolumeUnit.ML,
```
Import `com.babytracker.domain.model.VolumeUnit`.

- [ ] **Step 2: Add the flow to the `combine` block**

Append `settingsRepository.getVolumeUnit(),` as the last flow argument. In the lambda add:
```kotlin
val volumeUnit = values[17] as VolumeUnit
```
and pass `volumeUnit = volumeUnit,` into the `SettingsUiState(...)` constructor.

- [ ] **Step 3: Add the handler**

```kotlin
fun onVolumeUnitChanged(unit: VolumeUnit) {
    viewModelScope.launch { settingsRepository.setVolumeUnit(unit) }
}
```

- [ ] **Step 4: Write/extend the test**

```kotlin
@Test
fun `onVolumeUnitChanged persists selection`() = runTest {
    coEvery { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
    // ... build VM with mocked repository as the existing tests do ...

    viewModel.onVolumeUnitChanged(VolumeUnit.OZ)

    coVerify { settingsRepository.setVolumeUnit(VolumeUnit.OZ) }
}
```

> If `SettingsViewModelTest` stubs each repository flow individually, add `coEvery { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.ML)` to its setup so the combine completes.

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests "com.babytracker.ui.settings.SettingsViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt app/src/test/java/com/babytracker/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): expose volume unit in SettingsViewModel"
```

---

## Task 5: Settings ml/oz selector UI

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: REQUIRED SUB-SKILL — invoke `impeccable` (craft mode)** to design the unit selector so it matches the Settings screen's existing row/section styling (label + supporting text + control), spacing, and the Baby palette. Build the composable under that guidance.

- [ ] **Step 2: Add a segmented selector row** (place in the preferences section near the theme selector)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeUnitRow(
    selected: VolumeUnit,
    onSelect: (VolumeUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_volume_unit_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(VolumeUnit.ML to "mL", VolumeUnit.OZ to "oz")
            options.forEachIndexed { index, (unit, label) ->
                SegmentedButton(
                    selected = selected == unit,
                    onClick = { onSelect(unit) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label) }
            }
        }
    }
}
```

- [ ] **Step 3: Wire it into the screen** — read `uiState.volumeUnit` and call `viewModel.onVolumeUnitChanged(it)`. Add the string resource to `app/src/main/res/values/strings.xml`:
```xml
<string name="settings_volume_unit_title">Volume unit</string>
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(settings): add ml/oz volume unit selector"
```

---

## Task 6: Backfill pumping volume display

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/pumping/PumpingViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/pumping/PumpingScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/pumping/PumpingHistoryViewModel.kt` (if it renders volumes)

- [ ] **Step 1: Expose the unit** — inject `SettingsRepository` (already injected in these VMs in most cases; if not, add it) and add `volumeUnit` to the relevant `*UiState`, sourced from `settingsRepository.getVolumeUnit()`.

- [ ] **Step 2: Replace hard-coded volume strings** — find each place a volume is rendered (search `mL` / `volumeMl` in `PumpingScreen.kt` and `PumpingHistoryScreen.kt`) and replace with `formatVolume(volumeMl, uiState.volumeUnit)`.

Run: `rg "mL|volumeMl" app/src/main/java/com/babytracker/ui/pumping`

- [ ] **Step 3: Build + run pumping tests**

Run: `./gradlew test --tests "com.babytracker.ui.pumping.*"`
Expected: PASS (update any test asserting a literal "mL" string to compute via `formatVolume`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/pumping
git commit -m "feat(pumping): display volumes using unit preference"
```

---

## Task 7: Backfill inventory volume display

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/inventory/InventoryViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/inventory/InventoryScreen.kt`

- [ ] **Step 1: Expose the unit** — add `volumeUnit` to `InventoryUiState` from `settingsRepository.getVolumeUnit()` (inject if needed).

- [ ] **Step 2: Replace volume rendering** with `formatVolume(...)` everywhere the bag volume or summary total is shown.

Run: `rg "mL|volumeMl|totalMl" app/src/main/java/com/babytracker/ui/inventory`

> The `AddBagSheet` **input** field label stays "Volume (mL)" — input is always ml in this plan. Only read-only displays change.

- [ ] **Step 3: Build + run inventory tests**

Run: `./gradlew test --tests "com.babytracker.ui.inventory.*"`
Expected: PASS (update literal-string assertions to use `formatVolume`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/inventory
git commit -m "feat(inventory): display volumes using unit preference"
```

---

## Task 8: Settings toggle UI test

**Files:**
- Modify/Create: `app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt`

- [ ] **Step 1: Add a test** asserting the selector renders both options and selecting "oz" invokes the callback (follow existing SettingsScreen test harness with a fake/seeded VM state).

- [ ] **Step 2: Run (device/CI)**

Run: `./gradlew connectedAndroidTest --tests "com.babytracker.ui.settings.SettingsScreenTest"`
Expected: PASS or defer to CI.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/ui/settings/SettingsScreenTest.kt
git commit -m "test(settings): cover volume unit selector"
```

---

## Acceptance Criteria

- `VolumeUnit {ML, OZ}` persisted via `SettingsRepository` (DataStore key `volume_unit`, default `ML`).
- `formatVolume(volumeMl, unit)` is the single formatting entry point; ML shows whole ml, OZ shows one decimal with trailing `.0` dropped.
- Settings screen has a working ml/oz segmented selector; choice persists across app restarts.
- Pumping and inventory read-only volume displays honour the preference. Input fields remain ml.
- All storage (Room, DataStore, backup) stays in millilitres — no schema change.
- `./gradlew test` passes; literal-"mL" assertions updated to use `formatVolume`.

## Self-Review Notes

- Display-only change; storage stays ml → no migration, no backup impact. Avoids touching plan 01/07.
- US fluid ounce (29.5735 ml) chosen and documented in the util; locale-safe `String.format(Locale.US, ...)`.
- `combine` 18th-arg addition uses the existing vararg form already in `SettingsViewModel`; index cast documented. One new public handler keeps the VM under the detekt TooManyFunctions ceiling (20).
- oz **input** explicitly out of scope (YAGNI) — only display converts.
