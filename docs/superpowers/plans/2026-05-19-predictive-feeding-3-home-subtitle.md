# Predictive Feeding — Task 3: Home Feeding card subtitle

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `PredictNextFeedUseCase` into `HomeViewModel`, extend `HomeUiState` with `nextFeedPrediction`, and render the subtitle on the Feeding card with every branch from the spec (active session, null, overdue ≥ 5m, overdue 0–4m, ≤ 30m, > 30m, low confidence) plus accessibility content description.

**Architecture:** New `PredictionCopy` object centralises soft-language strings and shape/state mapping. `HomeViewModel` adds the use case as a Hilt-injected dependency and folds its flow into the existing `combine`. `HomeScreen` renders a leading-icon + text row inside the Feeding card; tap behaviour stays unchanged.

**Tech Stack:** Kotlin · Jetpack Compose Material 3 · Hilt · JUnit 5 · Compose UI Test.

**Branch:** `feat/predictive-feeding-3-home-subtitle`
**Linear issue:** Predictive feeding — Home card subtitle
**Depends on:** Task 2
**Overview:** [`2026-05-19-predictive-feeding-overview.md`](./2026-05-19-predictive-feeding-overview.md)
**Source spec:** [`docs/superpowers/specs/2026-05-19-predictive-feeding-reminders-design.md`](../specs/2026-05-19-predictive-feeding-reminders-design.md)

---

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/breastfeeding/PredictionCopy.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- Modify: `app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt`
- Test: `app/src/test/java/com/babytracker/ui/breastfeeding/PredictionCopyTest.kt`
- Test: `app/src/androidTest/java/com/babytracker/ui/home/HomeScreenPredictionTest.kt`

## Steps

- [ ] **Step 1: Create `PredictionCopy.kt`**

```kotlin
package com.babytracker.ui.breastfeeding

import com.babytracker.domain.model.FeedPrediction
import com.babytracker.util.formatTime
import kotlin.math.abs

object PredictionCopy {

    data class Subtitle(
        val primary: String,
        val secondary: String? = null,
        val lowConfidence: Boolean = false,
        val contentDescription: String,
    )

    private const val OVERDUE_NOW_MIN_MINUTES = 5
    private const val UPCOMING_DETAIL_THRESHOLD_MINUTES = 30
    private const val LOW_CONFIDENCE_SAMPLE_SIZE = 3

    fun forPrediction(prediction: FeedPrediction): Subtitle {
        val timeLabel = prediction.predictedAt.formatTime()
        val lowConfidence = prediction.sampleSize == LOW_CONFIDENCE_SAMPLE_SIZE
        val confidenceCd = if (lowConfidence) ", low confidence" else ""
        val basisCd = ", based on ${prediction.sampleSize} recent feeds"

        return when {
            prediction.isOverdue && abs(prediction.minutesUntil) >= OVERDUE_NOW_MIN_MINUTES -> {
                val agoMinutes = abs(prediction.minutesUntil)
                Subtitle(
                    primary = "Likely hungry now · ~${agoMinutes}m ago",
                    lowConfidence = lowConfidence,
                    contentDescription = "Likely hungry now, about $agoMinutes minutes ago$confidenceCd$basisCd",
                )
            }
            !prediction.isOverdue && prediction.minutesUntil <= UPCOMING_DETAIL_THRESHOLD_MINUTES -> {
                Subtitle(
                    primary = "Likely hungry around $timeLabel",
                    secondary = "in ~${prediction.minutesUntil}m",
                    lowConfidence = lowConfidence,
                    contentDescription = "Likely hungry around $timeLabel, in about ${prediction.minutesUntil} minutes$confidenceCd$basisCd",
                )
            }
            else -> Subtitle(
                primary = "Likely hungry around $timeLabel",
                lowConfidence = lowConfidence,
                contentDescription = "Likely hungry around $timeLabel$confidenceCd$basisCd",
            )
        }
    }
}
```

- [ ] **Step 2: Write the failing `PredictionCopy` test**

```kotlin
package com.babytracker.ui.breastfeeding

import com.babytracker.domain.model.FeedPrediction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PredictionCopyTest {

    private val anchor = Instant.parse("2026-05-19T17:40:00Z")

    @Test
    fun `future prediction beyond 30 minutes uses single line`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 45)
        )
        assertTrue(subtitle.primary.startsWith("Likely hungry around "))
        assertNull(subtitle.secondary)
    }

    @Test
    fun `future prediction within 30 minutes shows secondary in-X-m`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 12)
        )
        assertEquals("in ~12m", subtitle.secondary)
    }

    @Test
    fun `overdue 5 minutes or more shows hungry now copy`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 4, isOverdue = true, minutesUntil = -7)
        )
        assertTrue(subtitle.primary.contains("hungry now"))
        assertTrue(subtitle.primary.contains("~7m ago"))
    }

    @Test
    fun `sample size 3 marks low confidence`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 3, isOverdue = false, minutesUntil = 50)
        )
        assertTrue(subtitle.lowConfidence)
    }

    @Test
    fun `sample size 5 is not low confidence`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 50)
        )
        assertEquals(false, subtitle.lowConfidence)
    }
}
```

- [ ] **Step 3: Run tests, verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.breastfeeding.PredictionCopyTest"
```

Expected: PASS.

- [ ] **Step 4: Extend `HomeUiState` and `HomeViewModel`**

In `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`:

Add a field to `HomeUiState`:

```kotlin
val nextFeedPrediction: FeedPrediction? = null,
```

Add `predictNextFeed: PredictNextFeedUseCase` to the `@Inject` constructor parameter list. Fold its `Flow<FeedPrediction?>` into the existing inner `combine`:

```kotlin
val uiState: StateFlow<HomeUiState> = combine(
    combine(
        getBabyProfile(),
        getBreastfeedingHistory(),
        getSleepHistory(),
        settingsRepository.getAppMode(),
        predictNextFeed(),
    ) { baby, feedings, sleepRecords, appMode, prediction ->
        // ...existing body unchanged...
        HomeUiState(
            // ...existing assignments unchanged...
            nextFeedPrediction = if (feedings.any { it.isInProgress }) null else prediction,
        )
    },
    pumpingRepository.getActiveSession(),
    inventoryRepository.getSummary(),
) { partial, pumpingActive, inventorySummary ->
    partial.copy(pumpingActive = pumpingActive, inventorySummary = inventorySummary)
}.stateIn(/* unchanged */)
```

Note: the inner `combine` arity goes from 4 to 5 — make sure the lambda signature is updated.

- [ ] **Step 5: Update `HomeViewModelTest.kt` for the new constructor parameter**

The existing test (`app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt`) instantiates `HomeViewModel` directly via a private `createViewModel()` helper. Adding `PredictNextFeedUseCase` to the constructor would break compilation of the whole test class. Patch in lockstep:

1. Add fields and import:

```kotlin
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
// ...
private lateinit var predictNextFeed: PredictNextFeedUseCase
```

2. In `setUp()`, add:

```kotlin
predictNextFeed = mockk()
every { predictNextFeed() } returns flowOf(null)
```

3. Update `createViewModel()` to pass the new dependency:

```kotlin
private fun createViewModel() = HomeViewModel(
    getBabyProfile,
    getBreastfeedingHistory,
    getSleepHistory,
    syncToFirestore,
    settingsRepository,
    pumpingRepository,
    inventoryRepository,
    predictNextFeed,
)
```

4. Add two new assertions covering the gating rule introduced in Step 4 (active session suppresses the prediction):

```kotlin
@Test
fun nextFeedPrediction_isExposed_whenNoActiveSession() = runTest {
    val prediction = FeedPrediction(
        predictedAt = Instant.parse("2026-05-19T17:40:00Z"),
        averageIntervalMinutes = 180,
        sampleSize = 5,
        isOverdue = false,
        minutesUntil = 45,
    )
    every { getBreastfeedingHistory() } returns flowOf(listOf(completedSession))
    every { predictNextFeed() } returns flowOf(prediction)
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(prediction, viewModel.uiState.value.nextFeedPrediction)
}

@Test
fun nextFeedPrediction_isSuppressed_whenActiveSessionExists() = runTest {
    val prediction = FeedPrediction(
        predictedAt = Instant.parse("2026-05-19T17:40:00Z"),
        averageIntervalMinutes = 180,
        sampleSize = 5,
        isOverdue = false,
        minutesUntil = 45,
    )
    every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession, completedSession))
    every { predictNextFeed() } returns flowOf(prediction)
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()
    assertNull(viewModel.uiState.value.nextFeedPrediction)
}
```

Verify:

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.home.HomeViewModelTest"
```

Expected: PASS (all pre-existing tests still green + both new assertions pass).

- [ ] **Step 6: Render the subtitle on the Feeding card in `HomeScreen.kt`**

The Feeding card sits in a half-width column on the home grid, so the longest spec state (`Likely hungry around 17:40` + `in ~12m` + `· low confidence`) does not fit on one line. Use a leading icon + weighted column; primary copy on line 1 with `maxLines = 1` + ellipsis, secondary/low-confidence on a second wrapping line.

Inside the Feeding card content column, below the existing primary content, add:

```kotlin
val prediction = uiState.nextFeedPrediction
if (uiState.activeSession == null && prediction != null) {
    val subtitle = remember(prediction) { PredictionCopy.forPrediction(prediction) }
    val primaryColor = if (prediction.isOverdue && prediction.minutesUntil <= -5)
        MaterialTheme.colorScheme.tertiary
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = subtitle.contentDescription
            },
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subtitle.primary,
                style = MaterialTheme.typography.bodyMedium,
                color = primaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = buildString {
                subtitle.secondary?.let { append(it) }
                if (subtitle.lowConfidence) {
                    if (isNotEmpty()) append(" · ")
                    append("low confidence")
                }
            }
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
```

Add the imports for `androidx.compose.foundation.layout.Column`, `androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.material.icons.Icons`, `Icons.Outlined.Schedule`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.text.style.TextOverflow`, and `com.babytracker.ui.breastfeeding.PredictionCopy`.

- [ ] **Step 7: Write the instrumentation test**

Create `app/src/androidTest/java/com/babytracker/ui/home/HomeScreenPredictionTest.kt`. Mirror whichever stateless-content / VM-stub pattern existing Home tests use. Cover:

```kotlin
@Test
fun subtitleIsAbsentWhenPredictionIsNull() {
    // Assert that no node has content description starting with "Likely hungry".
}

@Test
fun subtitleRendersFutureLikelyHungryAround() {
    // State with nextFeedPrediction (minutesUntil=45, sampleSize=5).
    // Assert node with text "Likely hungry around <time>" exists.
}

@Test
fun subtitleRendersOverdueHungryNow() {
    // minutesUntil=-7, isOverdue=true. Assert "hungry now" + "~7m ago".
}

@Test
fun lowConfidenceSuffixAppearsWhenSampleSizeThree() {
    // Assert node with text containing "low confidence" exists on the detail line.
}

@Test
fun subtitleDoesNotOverflowAtSmallWidth() {
    // Render Feeding card constrained to 180.dp width with the longest state
    // (minutesUntil=12, sampleSize=3). Assert primary text node is displayed
    // and detail line "in ~12m · low confidence" is displayed (no clipping
    // assertion via `assertIsDisplayed()` on both nodes).
}
```

- [ ] **Step 8: Run instrumentation tests**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.babytracker.ui.home.HomeScreenPredictionTest"
```

Expected: PASS.

- [ ] **Step 9: Lint + detekt**

```bash
./gradlew ktlintFormat detekt
```

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/breastfeeding/PredictionCopy.kt \
        app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt \
        app/src/main/java/com/babytracker/ui/home/HomeScreen.kt \
        app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt \
        app/src/test/java/com/babytracker/ui/breastfeeding/PredictionCopyTest.kt \
        app/src/androidTest/java/com/babytracker/ui/home/HomeScreenPredictionTest.kt
git commit -m "feat(home): render predictive feeding subtitle on Feeding card"
```

- [ ] **Step 11: Push branch and open PR**

```bash
git push -u origin feat/predictive-feeding-3-home-subtitle
gh pr create --title "feat(home): predictive feeding subtitle on Feeding card" \
  --body "Wires PredictNextFeedUseCase into HomeViewModel and renders the subtitle on the Feeding card with all spec states. Task 3 of 6."
```
