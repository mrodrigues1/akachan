# SPEC-002: Onboarding — Baby Profile & Allergies

**App Name:** BabyTracker
**Platform:** Native Android
**Status:** Draft
**Version:** 1.0
**Date:** 2026-04-04
**Dependencies:** SPEC-001

---

## 1. Overview

The onboarding flow is the first experience a user has with BabyTracker. It collects the minimum required information to personalize the app: the baby's name, date of birth (used to calculate age throughout the app), and any known allergies. The flow runs once on first launch and is skipped on subsequent launches. All collected data is persisted to DataStore.

### 1.1 Goals

- Collect baby name, date of birth, and allergy information before the user reaches the main app.
- Feel fast and lightweight — three steps maximum, completable in under 30 seconds.
- Allow the user to skip the allergy step (no allergies is the common case).
- Store data in DataStore and set the `onboarding_complete` flag so the flow never repeats.
- All inputs are editable later from the Settings screen (SPEC-002 only defines onboarding; Settings editing is part of the Settings feature).

### 1.2 Non-Goals

- No account creation, login, or cloud sync.
- No photo upload for the baby.
- No support for multiple babies (single-baby app for v1).
- No animated onboarding carousel or feature tour.

---

## 2. User Flow

### 2.1 Flow Diagram

```
App Launch
    │
    ▼
┌──────────────────┐
│ Check DataStore:  │
│ onboarding_complete│
└──────┬───────────┘
       │
  ┌────┴────┐
  │  false  │──────────────────────────────────┐
  └─────────┘                                  │
  ┌────┴────┐                                  ▼
  │  true   │──► Home Screen       ┌───────────────────┐
  └─────────┘                      │  Step 1: Welcome  │
                                   │  Baby Name input  │
                                   └────────┬──────────┘
                                            │ [Next]
                                            ▼
                                   ┌───────────────────┐
                                   │  Step 2: Birthday  │
                                   │  Date Picker       │
                                   └────────┬──────────┘
                                            │ [Next]
                                            ▼
                                   ┌───────────────────┐
                                   │  Step 3: Allergies │
                                   │  Optional multi-   │
                                   │  select + custom   │
                                   └────────┬──────────┘
                                            │ [Finish]
                                            ▼
                                   ┌───────────────────┐
                                   │ Save to DataStore  │
                                   │ onboarding_complete│
                                   │ = true             │
                                   └────────┬──────────┘
                                            │
                                            ▼
                                       Home Screen
```

### 2.2 Step Details

#### Step 1 — Welcome & Baby Name

| Aspect | Detail |
|---|---|
| Headline | "What's your baby's name?" |
| Input | Single `OutlinedTextField`, auto-focused, max 50 characters |
| Validation | Non-empty after trimming whitespace |
| Button | "Next" — enabled only when input is valid |
| Back behavior | System back exits the app (first screen) |

#### Step 2 — Date of Birth

| Aspect | Detail |
|---|---|
| Headline | "When was {babyName} born?" |
| Input | MD3 `DatePicker` (modal or inline based on screen size) |
| Constraints | Date must be today or in the past. Maximum 12 months ago (app targets 0–12 months). If older, show a soft warning: "BabyTracker is designed for babies 0–12 months" but still allow proceeding. |
| Default | Today's date pre-selected |
| Button | "Next" — always enabled (date is pre-selected) |
| Back behavior | Returns to Step 1, preserving the entered name |

#### Step 3 — Allergies

| Aspect | Detail |
|---|---|
| Headline | "Does {babyName} have any known allergies?" |
| Layout | List of common allergy chips (multi-select) + "Other" option with free-text field |
| Predefined options | Cow's Milk Protein (CMPA), Soy, Egg, Wheat, Peanut, Tree Nuts |
| "Other" behavior | Tapping "Other" expands a text field below the chips (max 100 characters) |
| Selection | Zero or more — this step is fully optional |
| Button | "Get Started" (not "Next" — signals completion) |
| Back behavior | Returns to Step 2, preserving allergy selections |

### 2.3 Transition to Home

After tapping "Get Started":
1. ViewModel calls `SaveBabyProfileUseCase` with the collected data.
2. Use case writes all values to DataStore in a single transaction, including `onboarding_complete = true`.
3. Navigation replaces the onboarding route with `Home` (no back-stack entry for onboarding — user cannot navigate back to it).

---

## 3. Domain Layer

### 3.1 Domain Model

```kotlin
// domain/model/Baby.kt
data class Baby(
    val name: String,
    val birthDate: LocalDate,
    val allergies: List<AllergyType>,
    val customAllergyNote: String?,
) {
    val ageInDays: Long
        get() = ChronoUnit.DAYS.between(birthDate, LocalDate.now())

    val ageInWeeks: Int
        get() = (ageInDays / 7).toInt()

    val ageInMonths: Int
        get() = Period.between(birthDate, LocalDate.now()).toTotalMonths().toInt()
}
```

```kotlin
// domain/model/AllergyType.kt
enum class AllergyType(val label: String) {
    CMPA("Cow's Milk Protein"),
    SOY("Soy"),
    EGG("Egg"),
    WHEAT("Wheat"),
    PEANUT("Peanut"),
    TREE_NUTS("Tree Nuts"),
    OTHER("Other"),
}
```

### 3.2 Repository Interface

```kotlin
// domain/repository/BabyRepository.kt
interface BabyRepository {
    fun getBabyProfile(): Flow<Baby?>
    suspend fun saveBabyProfile(baby: Baby)
    fun isOnboardingComplete(): Flow<Boolean>
}
```

### 3.3 Use Cases

#### SaveBabyProfileUseCase

```kotlin
// domain/usecase/baby/SaveBabyProfileUseCase.kt
class SaveBabyProfileUseCase @Inject constructor(
    private val babyRepository: BabyRepository,
) {
    suspend operator fun invoke(baby: Baby) {
        require(baby.name.isNotBlank()) { "Baby name must not be blank" }
        require(!baby.birthDate.isAfter(LocalDate.now())) { "Birth date cannot be in the future" }
        babyRepository.saveBabyProfile(baby)
    }
}
```

#### GetBabyProfileUseCase

```kotlin
// domain/usecase/baby/GetBabyProfileUseCase.kt
class GetBabyProfileUseCase @Inject constructor(
    private val babyRepository: BabyRepository,
) {
    operator fun invoke(): Flow<Baby?> = babyRepository.getBabyProfile()
}
```

---

## 4. Data Layer

### 4.1 DataStore Implementation

```kotlin
// data/repository/BabyRepositoryImpl.kt
class BabyRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : BabyRepository {

    private object Keys {
        val BABY_NAME = stringPreferencesKey("baby_name")
        val BABY_BIRTH_DATE = longPreferencesKey("baby_birth_date")
        val ALLERGIES = stringPreferencesKey("allergies")
        val CUSTOM_ALLERGY_NOTE = stringPreferencesKey("custom_allergy_note")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    override fun getBabyProfile(): Flow<Baby?> = dataStore.data.map { prefs ->
        val name = prefs[Keys.BABY_NAME] ?: return@map null
        val epochDay = prefs[Keys.BABY_BIRTH_DATE] ?: return@map null
        Baby(
            name = name,
            birthDate = LocalDate.ofEpochDay(epochDay),
            allergies = prefs[Keys.ALLERGIES]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { AllergyType.valueOf(it) }
                ?: emptyList(),
            customAllergyNote = prefs[Keys.CUSTOM_ALLERGY_NOTE],
        )
    }

    override suspend fun saveBabyProfile(baby: Baby) {
        dataStore.edit { prefs ->
            prefs[Keys.BABY_NAME] = baby.name.trim()
            prefs[Keys.BABY_BIRTH_DATE] = baby.birthDate.toEpochDay()
            prefs[Keys.ALLERGIES] = baby.allergies.joinToString(",") { it.name }
            if (baby.customAllergyNote != null) {
                prefs[Keys.CUSTOM_ALLERGY_NOTE] = baby.customAllergyNote
            }
            prefs[Keys.ONBOARDING_COMPLETE] = true
        }
    }

    override fun isOnboardingComplete(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETE] ?: false
    }
}
```

### 4.2 Storage Decisions

| Data point | Storage | Rationale |
|---|---|---|
| Baby name | DataStore `String` | Simple key-value, no relations |
| Birth date | DataStore `Long` (epoch day) | Compact, timezone-agnostic (date-only, no time component) |
| Allergies | DataStore `String` (comma-separated enum names) | Small fixed set, no querying needed |
| Custom allergy note | DataStore `String` | Free-text companion to OTHER enum |
| Onboarding flag | DataStore `Boolean` | Checked on every app launch |

---

## 5. UI Layer

### 5.1 UI State

```kotlin
// ui/onboarding/OnboardingUiState.kt
data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.NAME,
    val babyName: String = "",
    val birthDate: LocalDate = LocalDate.now(),
    val selectedAllergies: Set<AllergyType> = emptySet(),
    val customAllergyNote: String = "",
    val showAgeWarning: Boolean = false,
    val isSaving: Boolean = false,
)

enum class OnboardingStep { NAME, BIRTH_DATE, ALLERGIES }
```

### 5.2 ViewModel

```kotlin
// ui/onboarding/OnboardingViewModel.kt
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saveBabyProfile: SaveBabyProfileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        if (name.length <= 50) {
            _uiState.update { it.copy(babyName = name) }
        }
    }

    fun onBirthDateSelected(date: LocalDate) {
        val monthsAgo = Period.between(date, LocalDate.now()).toTotalMonths()
        _uiState.update {
            it.copy(
                birthDate = date,
                showAgeWarning = monthsAgo > 12,
            )
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
                OnboardingStep.NAME -> state.copy(currentStep = OnboardingStep.BIRTH_DATE)
                OnboardingStep.BIRTH_DATE -> state.copy(currentStep = OnboardingStep.ALLERGIES)
                OnboardingStep.ALLERGIES -> state // handled by onFinish
            }
        }
    }

    fun onPreviousStep() {
        _uiState.update { state ->
            when (state.currentStep) {
                OnboardingStep.NAME -> state
                OnboardingStep.BIRTH_DATE -> state.copy(currentStep = OnboardingStep.NAME)
                OnboardingStep.ALLERGIES -> state.copy(currentStep = OnboardingStep.BIRTH_DATE)
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
            saveBabyProfile(baby)
            onComplete()
        }
    }

    val isNextEnabled: Boolean
        get() = when (_uiState.value.currentStep) {
            OnboardingStep.NAME -> _uiState.value.babyName.isNotBlank()
            OnboardingStep.BIRTH_DATE -> true
            OnboardingStep.ALLERGIES -> true
        }
}
```

### 5.3 Screen Composable (Structure)

The `OnboardingScreen` is a single Composable that switches content based on `currentStep`. No separate routes per step — the ViewModel manages internal step navigation, keeping the navigation graph simple.

```kotlin
// ui/onboarding/OnboardingScreen.kt
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onOnboardingComplete: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // BackHandler for internal step navigation
    BackHandler(enabled = uiState.currentStep != OnboardingStep.NAME) {
        viewModel.onPreviousStep()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            // Step indicator (3 dots)
            StepIndicator(
                currentStep = uiState.currentStep.ordinal,
                totalSteps = OnboardingStep.entries.size,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Step content
            AnimatedContent(targetState = uiState.currentStep) { step ->
                when (step) {
                    OnboardingStep.NAME -> NameStepContent(
                        name = uiState.babyName,
                        onNameChanged = viewModel::onNameChanged,
                    )
                    OnboardingStep.BIRTH_DATE -> BirthDateStepContent(
                        selectedDate = uiState.birthDate,
                        showAgeWarning = uiState.showAgeWarning,
                        onDateSelected = viewModel::onBirthDateSelected,
                    )
                    OnboardingStep.ALLERGIES -> AllergiesStepContent(
                        selectedAllergies = uiState.selectedAllergies,
                        customNote = uiState.customAllergyNote,
                        onAllergyToggled = viewModel::onAllergyToggled,
                        onCustomNoteChanged = viewModel::onCustomAllergyNoteChanged,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom button
            Button(
                onClick = {
                    if (uiState.currentStep == OnboardingStep.ALLERGIES) {
                        viewModel.onFinish(onComplete = onOnboardingComplete)
                    } else {
                        viewModel.onNextStep()
                    }
                },
                enabled = viewModel.isNextEnabled && !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = if (uiState.currentStep == OnboardingStep.ALLERGIES)
                            "Get Started" else "Next"
                    )
                }
            }
        }
    }
}
```

### 5.4 UI Component Details

#### StepIndicator

A row of 3 small circles. The current step is filled with `MaterialTheme.colorScheme.primary`; completed steps use `primary` at reduced opacity; future steps use `outline`.

#### NameStepContent

- `OutlinedTextField` with `keyboardOptions = KeyboardOptions(capitalization = Words, imeAction = Next)`.
- `keyboardActions = KeyboardActions(onNext = { onNextStep() })` for keyboard-driven navigation.
- Character counter shown when name exceeds 40 characters: "42/50".

#### BirthDateStepContent

- MD3 `DatePickerDialog` triggered by tapping a read-only `OutlinedTextField` that displays the formatted date.
- `selectableDates` constrains to past dates only.
- Age computed and displayed below the field: "3 months, 12 days old".
- If age > 12 months, show an `AssistChip` with warning icon and text.

#### AllergiesStepContent

- `FlowRow` of `FilterChip` composables, one per `AllergyType`.
- Selected chips show checkmark icon and use `secondaryContainer` color.
- When `OTHER` is selected, an `OutlinedTextField` appears below with a slide-in animation.
- If no allergies selected, a subtle helper text reads: "You can always update this later in Settings."

---

## 6. Navigation Integration

### 6.1 Conditional Start Destination

```kotlin
// navigation/AppNavGraph.kt
@Composable
fun AppNavGraph(
    navController: NavHostController,
    isOnboardingComplete: Boolean,
) {
    NavHost(
        navController = navController,
        startDestination = if (isOnboardingComplete) Routes.HOME else Routes.ONBOARDING,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) { HomeScreen(/* ... */) }
        // ... other routes
    }
}
```

### 6.2 Resolving Start Destination in MainActivity

The `onboarding_complete` flag must be read before the NavHost renders to avoid a flash of the wrong screen. Use a splash-screen-like loading state:

```kotlin
// MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val babyRepository: BabyRepository = // injected or via EntryPoint
            val isOnboardingComplete by babyRepository
                .isOnboardingComplete()
                .collectAsStateWithLifecycle(initialValue = null)

            BabyTrackerTheme {
                when (isOnboardingComplete) {
                    null -> {
                        // Brief loading state while DataStore reads
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        val navController = rememberNavController()
                        AppNavGraph(
                            navController = navController,
                            isOnboardingComplete = isOnboardingComplete!!,
                        )
                    }
                }
            }
        }
    }
}
```

---

## 7. Edge Cases & Error Handling

| Scenario | Behavior |
|---|---|
| User enters only whitespace for name | "Next" button stays disabled (`isNotBlank()` check) |
| User selects a future birth date | DatePicker constrains to today or earlier; impossible to select |
| User selects birth date > 12 months ago | Soft warning displayed, user can still proceed |
| User toggles OTHER allergy but leaves note blank | `customAllergyNote` is stored as `null`; OTHER enum is still saved |
| DataStore write fails (disk full, etc.) | `onFinish` catch block shows a Snackbar: "Could not save. Please try again." and resets `isSaving` |
| App killed mid-onboarding | Data is not yet persisted — `onboarding_complete` is still `false`, so onboarding restarts on next launch. Entered data is lost (acceptable tradeoff for a 30-second flow). |
| User rotates device mid-step | ViewModel survives configuration change. UI state (step, name, date, allergies) is preserved. |

---

## 8. Accessibility

| Requirement | Implementation |
|---|---|
| Screen reader | All inputs have `contentDescription`. Step indicator announces "Step 1 of 3, Baby Name". |
| Touch targets | All chips and buttons meet 48dp minimum touch target. |
| Keyboard navigation | `OutlinedTextField` IME action advances to next field/step. |
| Color contrast | MD3 theme tokens ensure WCAG AA contrast ratios by default. |
| Age warning | Warning chip uses `error` color role and includes an icon for non-color distinction. |

---

## 9. Testing

### 9.1 Unit Tests — SaveBabyProfileUseCase

| Test | Input | Expected |
|---|---|---|
| `invoke_validBaby_savesToRepository` | Baby("Luna", today, [CMPA], null) | `babyRepository.saveBabyProfile()` called once |
| `invoke_blankName_throwsIllegalArgument` | Baby("  ", today, [], null) | throws `IllegalArgumentException` |
| `invoke_futureBirthDate_throwsIllegalArgument` | Baby("Luna", tomorrow, [], null) | throws `IllegalArgumentException` |
| `invoke_noAllergies_savesEmptyList` | Baby("Luna", today, [], null) | saved with empty allergies list |
| `invoke_otherAllergyWithNote_savesNote` | Baby("Luna", today, [OTHER], "Shellfish") | saved with customAllergyNote = "Shellfish" |

### 9.2 Unit Tests — OnboardingViewModel

| Test | Action | Expected State |
|---|---|---|
| `initial_state_isNameStep` | — | currentStep = NAME, babyName = "" |
| `onNameChanged_updatesName` | onNameChanged("Luna") | babyName = "Luna" |
| `onNameChanged_exceeds50Chars_ignored` | onNameChanged("A".repeat(51)) | babyName unchanged |
| `onNextStep_fromName_movesToBirthDate` | onNextStep() when name = "Luna" | currentStep = BIRTH_DATE |
| `onPreviousStep_fromBirthDate_movesToName` | onPreviousStep() from BIRTH_DATE | currentStep = NAME, name preserved |
| `onBirthDateSelected_over12Months_showsWarning` | select date 14 months ago | showAgeWarning = true |
| `onAllergyToggled_addsAndRemoves` | toggle CMPA twice | first: {CMPA}, second: {} |
| `onFinish_savesProfile_callsOnComplete` | onFinish(callback) | isSaving = true → callback invoked |
| `isNextEnabled_blankName_false` | babyName = "  " | isNextEnabled = false |

### 9.3 Integration Tests — BabyRepositoryImpl

| Test | Action | Expected |
|---|---|---|
| `saveThenGet_roundTrips` | save Baby, then collect getBabyProfile() | emitted Baby matches saved Baby |
| `isOnboardingComplete_afterSave_true` | save Baby, collect isOnboardingComplete() | emits true |
| `isOnboardingComplete_beforeSave_false` | collect without saving | emits false |
| `saveTwice_overwritesPrevious` | save Baby A, then Baby B, collect | emits Baby B |
| `getAllergies_multipleSelected_roundTrips` | save with [CMPA, SOY, OTHER] | retrieved list matches |

### 9.4 UI Tests — OnboardingScreen

| Test | Steps | Assertion |
|---|---|---|
| `fullFlow_completesOnboarding` | Type name → Next → Pick date → Next → Select allergy → Get Started | navigates to Home, DataStore has onboarding_complete = true |
| `emptyName_nextDisabled` | Launch screen, don't type | Next button is not enabled |
| `backFromBirthDate_preservesName` | Type "Luna" → Next → Back | Name field shows "Luna" |
| `noAllergies_canFinish` | Complete name + date steps → tap "Get Started" without selecting allergies | Completes successfully |

---

## 10. Files Created / Modified by This Spec

### New Files

| File | Layer |
|---|---|
| `domain/model/Baby.kt` | Domain |
| `domain/model/AllergyType.kt` | Domain |
| `domain/repository/BabyRepository.kt` | Domain |
| `domain/usecase/baby/SaveBabyProfileUseCase.kt` | Domain |
| `domain/usecase/baby/GetBabyProfileUseCase.kt` | Domain |
| `data/repository/BabyRepositoryImpl.kt` | Data |
| `ui/onboarding/OnboardingUiState.kt` | UI |
| `ui/onboarding/OnboardingViewModel.kt` | UI |
| `ui/onboarding/OnboardingScreen.kt` | UI |
| `ui/onboarding/components/StepIndicator.kt` | UI |
| `ui/onboarding/components/NameStepContent.kt` | UI |
| `ui/onboarding/components/BirthDateStepContent.kt` | UI |
| `ui/onboarding/components/AllergiesStepContent.kt` | UI |

### Modified Files

| File | Change |
|---|---|
| `navigation/AppNavGraph.kt` | Add onboarding route and conditional start destination |
| `MainActivity.kt` | Read `isOnboardingComplete` before rendering NavHost |
| `di/RepositoryModule.kt` | Bind `BabyRepositoryImpl` → `BabyRepository` |

---

## 11. Acceptance Criteria

- [ ] First app launch shows the onboarding flow starting at the Name step.
- [ ] User can enter a baby name (max 50 chars) and advance with "Next".
- [ ] "Next" is disabled when the name field is blank or whitespace-only.
- [ ] User can select a birth date via MD3 DatePicker; future dates are not selectable.
- [ ] Selecting a birth date older than 12 months shows a non-blocking warning.
- [ ] Baby's calculated age displays correctly below the date field.
- [ ] User can select zero or more allergy chips from the predefined list.
- [ ] Selecting "Other" reveals a free-text input (max 100 chars).
- [ ] Tapping "Get Started" persists all data to DataStore and navigates to Home.
- [ ] Subsequent app launches skip onboarding and go directly to Home.
- [ ] Back navigation between steps preserves all previously entered data.
- [ ] Device rotation preserves the current step and all input state.
- [ ] All unit tests for `SaveBabyProfileUseCase` pass.
- [ ] All unit tests for `OnboardingViewModel` pass.
- [ ] All integration tests for `BabyRepositoryImpl` pass.
- [ ] UI test for complete onboarding flow passes.
