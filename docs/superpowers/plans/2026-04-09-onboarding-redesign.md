# Onboarding Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the onboarding flow with a Hero Banner + Floating Card layout: a warm welcome screen, a merged Baby Info step (name + DOB), and an Allergies step.

**Architecture:** Each `OnboardingStep` composable is self-contained (owns its hero strip + floating card layout). `OnboardingScreen` is reduced to an `AnimatedContent` dispatcher + `BackHandler`. `BoxWithConstraints` drives the hero/card overlap by computing explicit `Surface` heights.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose BOM 2024.12.01, Material 3, Hilt, JUnit 5 + MockK

---

## File Map

| Action | Path |
|--------|------|
| Modify | `app/src/main/java/com/babytracker/ui/onboarding/OnboardingViewModel.kt` |
| Modify | `app/src/test/java/com/babytracker/ui/onboarding/OnboardingViewModelTest.kt` |
| Create | `app/src/main/java/com/babytracker/ui/onboarding/components/OnboardingHeroStrip.kt` |
| Create | `app/src/main/java/com/babytracker/ui/onboarding/components/WelcomeStepContent.kt` |
| Create | `app/src/main/java/com/babytracker/ui/onboarding/components/BabyInfoStepContent.kt` |
| Modify | `app/src/main/java/com/babytracker/ui/onboarding/components/AllergiesStepContent.kt` |
| Modify | `app/src/main/java/com/babytracker/ui/onboarding/OnboardingScreen.kt` |
| Delete | `app/src/main/java/com/babytracker/ui/onboarding/components/StepIndicator.kt` |
| Delete | `app/src/main/java/com/babytracker/ui/onboarding/components/NameStepContent.kt` |
| Delete | `app/src/main/java/com/babytracker/ui/onboarding/components/BirthDateStepContent.kt` |

---

## Task 1: Update ViewModel — new enum and step transitions

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/test/java/com/babytracker/ui/onboarding/OnboardingViewModelTest.kt`

- [ ] **Step 1: Replace the test file with updated tests for the new enum**

Full replacement of `OnboardingViewModelTest.kt`:

```kotlin
package com.babytracker.ui.onboarding

import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private lateinit var saveBabyProfile: SaveBabyProfileUseCase
    private lateinit var viewModel: OnboardingViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        saveBabyProfile = mockk()
        viewModel = OnboardingViewModel(saveBabyProfile)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial_state_isWelcomeStep`() {
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.WELCOME, state.currentStep)
        assertEquals("", state.babyName)
    }

    @Test
    fun `onNextStep_fromWelcome_movesToBabyInfo`() {
        viewModel.onNextStep()
        assertEquals(OnboardingStep.BABY_INFO, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep_fromBabyInfo_movesToAllergies`() {
        viewModel.onNextStep() // WELCOME -> BABY_INFO
        viewModel.onNextStep() // BABY_INFO -> ALLERGIES
        assertEquals(OnboardingStep.ALLERGIES, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNextStep_fromAllergies_staysOnAllergies`() {
        viewModel.onNextStep()
        viewModel.onNextStep()
        viewModel.onNextStep()
        assertEquals(OnboardingStep.ALLERGIES, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep_fromWelcome_staysOnWelcome`() {
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep_fromBabyInfo_movesToWelcome`() {
        viewModel.onNextStep()
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPreviousStep_fromAllergies_movesToBabyInfo`() {
        viewModel.onNextStep()
        viewModel.onNextStep()
        viewModel.onPreviousStep()
        assertEquals(OnboardingStep.BABY_INFO, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onNameChanged_updatesName`() {
        viewModel.onNameChanged("Luna")
        assertEquals("Luna", viewModel.uiState.value.babyName)
    }

    @Test
    fun `onNameChanged_exceeds50Chars_ignored`() {
        viewModel.onNameChanged("A".repeat(51))
        assertEquals("", viewModel.uiState.value.babyName)
    }

    @Test
    fun `isNextEnabled_onWelcome_alwaysTrue`() {
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled_onBabyInfo_blankName_false`() {
        viewModel.onNextStep() // move to BABY_INFO
        assertFalse(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled_onBabyInfo_validName_true`() {
        viewModel.onNextStep()
        viewModel.onNameChanged("Luna")
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `isNextEnabled_onAllergies_alwaysTrue`() {
        viewModel.onNextStep()
        viewModel.onNextStep()
        assertTrue(viewModel.isNextEnabled)
    }

    @Test
    fun `onBirthDateSelected_over12Months_showsWarning`() {
        val fourteenMonthsAgo = LocalDate.now().minusMonths(14)
        viewModel.onBirthDateSelected(fourteenMonthsAgo)
        assertTrue(viewModel.uiState.value.showAgeWarning)
    }

    @Test
    fun `onBirthDateSelected_under12Months_noWarning`() {
        val threeMonthsAgo = LocalDate.now().minusMonths(3)
        viewModel.onBirthDateSelected(threeMonthsAgo)
        assertFalse(viewModel.uiState.value.showAgeWarning)
    }

    @Test
    fun `onAllergyToggled_addsAndRemoves`() {
        viewModel.onAllergyToggled(AllergyType.CMPA)
        assertTrue(AllergyType.CMPA in viewModel.uiState.value.selectedAllergies)
        viewModel.onAllergyToggled(AllergyType.CMPA)
        assertFalse(AllergyType.CMPA in viewModel.uiState.value.selectedAllergies)
    }

    @Test
    fun `onFinish_savesProfile_callsOnComplete`() = runTest {
        val babySlot = slot<Baby>()
        coJustRun { saveBabyProfile(capture(babySlot)) }

        viewModel.onNameChanged("Luna")
        var completeCalled = false
        viewModel.onFinish { completeCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { saveBabyProfile(any()) }
        assertEquals("Luna", babySlot.captured.name)
        assertTrue(completeCalled)
    }
}
```

- [ ] **Step 2: Run the tests — confirm they fail**

```bash
./gradlew :app:test --tests "com.babytracker.ui.onboarding.OnboardingViewModelTest" 2>&1 | tail -20
```

Expected: compilation error — `OnboardingStep.WELCOME` and `OnboardingStep.BABY_INFO` do not exist yet.

- [ ] **Step 3: Update `OnboardingViewModel.kt`**

Replace the file with:

```kotlin
package com.babytracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

enum class OnboardingStep { WELCOME, BABY_INFO, ALLERGIES }

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val babyName: String = "",
    val birthDate: LocalDate = LocalDate.now(),
    val selectedAllergies: Set<AllergyType> = emptySet(),
    val customAllergyNote: String = "",
    val showAgeWarning: Boolean = false,
    val isSaving: Boolean = false,
    val savingError: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saveBabyProfile: SaveBabyProfileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    val isNextEnabled: Boolean
        get() = when (_uiState.value.currentStep) {
            OnboardingStep.WELCOME -> true
            OnboardingStep.BABY_INFO -> _uiState.value.babyName.isNotBlank()
            OnboardingStep.ALLERGIES -> true
        }

    fun onNameChanged(name: String) {
        if (name.length <= 50) {
            _uiState.update { it.copy(babyName = name) }
        }
    }

    fun onBirthDateSelected(date: LocalDate) {
        val monthsAgo = Period.between(date, LocalDate.now()).toTotalMonths()
        _uiState.update {
            it.copy(birthDate = date, showAgeWarning = monthsAgo > 12)
        }
    }

    fun onAllergyToggled(allergy: AllergyType) {
        _uiState.update { state ->
            val updated = state.selectedAllergies.toMutableSet()
            if (allergy in updated) updated.remove(allergy) else updated.add(allergy)
            state.copy(selectedAllergies = updated)
        }
    }

    fun onCustomAllergyNoteChanged(note: String) {
        if (note.length <= 100) {
            _uiState.update { it.copy(customAllergyNote = note) }
        }
    }

    fun onNextStep() {
        _uiState.update { state ->
            when (state.currentStep) {
                OnboardingStep.WELCOME -> state.copy(currentStep = OnboardingStep.BABY_INFO)
                OnboardingStep.BABY_INFO -> state.copy(currentStep = OnboardingStep.ALLERGIES)
                OnboardingStep.ALLERGIES -> state
            }
        }
    }

    fun onPreviousStep() {
        _uiState.update { state ->
            when (state.currentStep) {
                OnboardingStep.WELCOME -> state
                OnboardingStep.BABY_INFO -> state.copy(currentStep = OnboardingStep.WELCOME)
                OnboardingStep.ALLERGIES -> state.copy(currentStep = OnboardingStep.BABY_INFO)
            }
        }
    }

    fun onFinish(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val state = _uiState.value
            val baby = Baby(
                name = state.babyName.trim(),
                birthDate = state.birthDate,
                allergies = state.selectedAllergies.toList(),
                customAllergyNote = state.customAllergyNote
                    .takeIf { AllergyType.OTHER in state.selectedAllergies && it.isNotBlank() },
            )
            try {
                saveBabyProfile(baby)
                onComplete()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, savingError = true) }
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests — confirm they pass**

```bash
./gradlew :app:test --tests "com.babytracker.ui.onboarding.OnboardingViewModelTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with all 17 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/onboarding/OnboardingViewModel.kt \
        app/src/test/java/com/babytracker/ui/onboarding/OnboardingViewModelTest.kt
git commit -m "refactor(onboarding): replace NAME/BIRTH_DATE steps with WELCOME/BABY_INFO enum"
```

---

## Task 2: Create `OnboardingHeroStrip`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/onboarding/components/OnboardingHeroStrip.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.babytracker.ui.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingHeroStrip(
    title: String,
    gradientColors: List<Color>,
    labelColor: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(Brush.linearGradient(gradientColors)),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
```

- [ ] **Step 2: Build to confirm it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/onboarding/components/OnboardingHeroStrip.kt
git commit -m "feat(onboarding): add OnboardingHeroStrip component"
```

---

## Task 3: Create `WelcomeStepContent`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/onboarding/components/WelcomeStepContent.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.babytracker.ui.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeStepContent(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroHeight = maxHeight * 0.45f
        val cardHeight = maxHeight - heroHeight + 16.dp

        // Hero: pink-to-cream gradient with centered baby bottle emoji
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🍼", fontSize = 80.sp)
        }

        // Card: overlaps hero by 16.dp, fills remainder of screen
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 32.dp),
            ) {
                Text(
                    text = "Welcome to Baby Tracker",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your companion for the first months of parenthood.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Get started")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/onboarding/components/WelcomeStepContent.kt
git commit -m "feat(onboarding): add WelcomeStepContent with hero-banner layout"
```

---

## Task 4: Create `BabyInfoStepContent`

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/onboarding/components/BabyInfoStepContent.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.babytracker.ui.onboarding.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyInfoStepContent(
    name: String,
    selectedDate: LocalDate,
    showAgeWarning: Boolean,
    isNextEnabled: Boolean,
    onNameChanged: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM d, yyyy") }
    val ageText = remember(selectedDate) {
        val period = Period.between(selectedDate, LocalDate.now())
        buildString {
            if (period.months > 0) append("${period.months} month${if (period.months != 1) "s" else ""}, ")
            append("${period.days} day${if (period.days != 1) "s" else ""} old")
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val stripHeight = 88.dp
        val cardHeight = maxHeight - stripHeight + 16.dp

        // Pink hero strip with back arrow
        OnboardingHeroStrip(
            title = "BABY INFO",
            gradientColors = listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.surface,
            ),
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Floating card overlapping strip by 16.dp
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 32.dp),
            ) {
                LinearProgressIndicator(
                    progress = { 0.5f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Scrollable content area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Tell us about your baby",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChanged,
                        label = { Text("Baby's name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { if (name.isNotBlank()) onNext() },
                        ),
                        supportingText = if (name.length > 40) {
                            { Text("${name.length}/50") }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = selectedDate.format(formatter),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date of birth") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ageText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showDatePicker = true }) {
                        Text("Change date")
                    }
                    if (showAgeWarning) {
                        AssistChip(
                            onClick = {},
                            label = { Text("BabyTracker is designed for babies 0–12 months") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNext,
                    enabled = isNextEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Next")
                }
            }
        }
    }

    if (showDatePicker) {
        val todayMillis = LocalDate.now()
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        val initialMillis = selectedDate
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            onDateSelected(date)
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
```

- [ ] **Step 2: Build to confirm it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/onboarding/components/BabyInfoStepContent.kt
git commit -m "feat(onboarding): add BabyInfoStepContent merging name and date-of-birth fields"
```

---

## Task 5: Rewrite `AllergiesStepContent`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/onboarding/components/AllergiesStepContent.kt`

- [ ] **Step 1: Replace the file**

The component gains `isSaving`, `onBack`, `onFinish` params, a blue hero strip, and an internal progress bar + button. The allergy chip logic is unchanged.

```kotlin
package com.babytracker.ui.onboarding.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.AllergyType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergiesStepContent(
    babyName: String,
    selectedAllergies: Set<AllergyType>,
    customNote: String,
    isSaving: Boolean,
    onAllergyToggled: (AllergyType) -> Unit,
    onCustomNoteChanged: (String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val stripHeight = 88.dp
        val cardHeight = maxHeight - stripHeight + 16.dp

        // Blue hero strip — secondary color distinguishes this step from Baby Info
        OnboardingHeroStrip(
            title = "ALLERGIES",
            gradientColors = listOf(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.surface,
            ),
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 32.dp),
            ) {
                LinearProgressIndicator(
                    progress = { 1.0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Does $babyName have any known allergies?",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        AllergyType.entries.forEach { allergy ->
                            val selected = allergy in selectedAllergies
                            FilterChip(
                                selected = selected,
                                onClick = { onAllergyToggled(allergy) },
                                label = { Text(allergy.label) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = AllergyType.OTHER in selectedAllergies,
                        enter = slideInVertically(),
                        exit = slideOutVertically(),
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customNote,
                                onValueChange = onCustomNoteChanged,
                                label = { Text("Describe the allergy") },
                                singleLine = false,
                                maxLines = 3,
                                supportingText = { Text("${customNote.length}/100") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (selectedAllergies.isEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "You can always update this later in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onFinish,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Get Started")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/onboarding/components/AllergiesStepContent.kt
git commit -m "refactor(onboarding): make AllergiesStepContent self-contained with hero strip and button"
```

---

## Task 6: Rewrite `OnboardingScreen`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Replace the file**

The screen is now just a dispatcher: `BackHandler` + `Scaffold` + `AnimatedContent`. All layout concerns live in the step composables.

```kotlin
package com.babytracker.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.ui.onboarding.components.AllergiesStepContent
import com.babytracker.ui.onboarding.components.BabyInfoStepContent
import com.babytracker.ui.onboarding.components.WelcomeStepContent

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.savingError) {
        if (uiState.savingError) {
            snackbarHostState.showSnackbar("Could not save. Please try again.")
        }
    }

    BackHandler(enabled = uiState.currentStep != OnboardingStep.WELCOME) {
        viewModel.onPreviousStep()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        AnimatedContent(
            targetState = uiState.currentStep,
            label = "onboarding_step",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStepContent(
                    onGetStarted = viewModel::onNextStep,
                )
                OnboardingStep.BABY_INFO -> BabyInfoStepContent(
                    name = uiState.babyName,
                    selectedDate = uiState.birthDate,
                    showAgeWarning = uiState.showAgeWarning,
                    isNextEnabled = viewModel.isNextEnabled,
                    onNameChanged = viewModel::onNameChanged,
                    onDateSelected = viewModel::onBirthDateSelected,
                    onBack = viewModel::onPreviousStep,
                    onNext = viewModel::onNextStep,
                )
                OnboardingStep.ALLERGIES -> AllergiesStepContent(
                    babyName = uiState.babyName,
                    selectedAllergies = uiState.selectedAllergies,
                    customNote = uiState.customAllergyNote,
                    isSaving = uiState.isSaving,
                    onAllergyToggled = viewModel::onAllergyToggled,
                    onCustomNoteChanged = viewModel::onCustomAllergyNoteChanged,
                    onBack = viewModel::onPreviousStep,
                    onFinish = { viewModel.onFinish(onOnboardingComplete) },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build the full debug APK to confirm no remaining compile errors**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/onboarding/OnboardingScreen.kt
git commit -m "refactor(onboarding): simplify OnboardingScreen to AnimatedContent dispatcher"
```

---

## Task 7: Delete obsolete files and run full test suite

**Files:**
- Delete: `app/src/main/java/com/babytracker/ui/onboarding/components/StepIndicator.kt`
- Delete: `app/src/main/java/com/babytracker/ui/onboarding/components/NameStepContent.kt`
- Delete: `app/src/main/java/com/babytracker/ui/onboarding/components/BirthDateStepContent.kt`

- [ ] **Step 1: Delete the three obsolete files**

```bash
rm app/src/main/java/com/babytracker/ui/onboarding/components/StepIndicator.kt
rm app/src/main/java/com/babytracker/ui/onboarding/components/NameStepContent.kt
rm app/src/main/java/com/babytracker/ui/onboarding/components/BirthDateStepContent.kt
```

- [ ] **Step 2: Build to confirm nothing references the deleted files**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run the full unit test suite**

```bash
./gradlew :app:test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore(onboarding): delete StepIndicator, NameStepContent, BirthDateStepContent"
```
