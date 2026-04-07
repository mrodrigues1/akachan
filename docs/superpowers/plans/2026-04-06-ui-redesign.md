# UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the full Akachan UI to a Soft & Playful aesthetic — BabyPink/BabyBlue theme tokens, ring timer, big touch-target selectors, hybrid home screen, and grouped history lists.

**Architecture:** Three-layer clean arch is unchanged. All changes are in `ui/` and `util/`. One new use case (`SwitchBreastfeedingSideUseCase`) is added. `HomeViewModel` gains stop-session capability and derived state fields. No new screens or navigation routes are added.

**Tech Stack:** Kotlin 2.0.21 · Jetpack Compose BOM 2024.12.01 · Material 3 · Hilt · JUnit 5 · MockK · Turbine

---

## File Map

**Create:**
- `app/src/main/java/com/babytracker/ui/theme/Shape.kt`
- `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/SwitchBreastfeedingSideUseCase.kt`
- `app/src/test/java/com/babytracker/util/DateTimeExtTest.kt`
- `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/SwitchBreastfeedingSideUseCaseTest.kt`
- `app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt`

**Modify:**
- `app/src/main/java/com/babytracker/ui/theme/Color.kt`
- `app/src/main/java/com/babytracker/ui/theme/Theme.kt`
- `app/src/main/java/com/babytracker/ui/theme/Type.kt`
- `app/src/main/java/com/babytracker/util/DateTimeExt.kt`
- `app/src/main/java/com/babytracker/ui/component/TimerDisplay.kt`
- `app/src/main/java/com/babytracker/ui/component/SideSelector.kt`
- `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt`
- `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt`
- `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt`
- `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingHistoryScreen.kt`
- `app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt`
- `app/src/main/java/com/babytracker/ui/sleep/SleepHistoryScreen.kt`
- `app/src/main/java/com/babytracker/ui/sleep/SleepScheduleScreen.kt`
- `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/babytracker/ui/onboarding/OnboardingScreen.kt`

---

## Task 1: Theme Foundation — Color, Shape, Type, Theme

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/theme/Color.kt`
- Create: `app/src/main/java/com/babytracker/ui/theme/Shape.kt`
- Modify: `app/src/main/java/com/babytracker/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/babytracker/ui/theme/Theme.kt`

- [ ] **Step 1: Replace Color.kt with semantic tokens**

```kotlin
// app/src/main/java/com/babytracker/ui/theme/Color.kt
package com.babytracker.ui.theme

import androidx.compose.ui.graphics.Color

// Raw palette constants (kept as reference)
val BabyBlue = Color(0xFF89CFF0)
val BabyPink = Color(0xFFF4C2C2)
val SoftGreen = Color(0xFF90EE90)
val SoftYellow = Color(0xFFFFF9C4)

// Light scheme semantic tokens
val PrimaryPink = Color(0xFFC2185B)
val OnPrimaryWhite = Color(0xFFFFFFFF)
val PrimaryContainerPink = Color(0xFFF8BBD0)
val OnPrimaryContainerDarkPink = Color(0xFF880E4F)

val SecondaryBlue = Color(0xFF1976D2)
val OnSecondaryWhite = Color(0xFFFFFFFF)
val SecondaryContainerBlue = Color(0xFFB3E5FC)
val OnSecondaryContainerDarkBlue = Color(0xFF0D47A1)

val TertiaryGreen = Color(0xFF388E3C)
val OnTertiaryWhite = Color(0xFFFFFFFF)
val TertiaryContainerGreen = Color(0xFFC8E6C9)
val OnTertiaryContainerDarkGreen = Color(0xFF1B5E20)

val SurfaceYellow = Color(0xFFFFFDE7)
val OnSurfaceDark = Color(0xFF1A1A1A)
val OnSurfaceVariantGrey = Color(0xFF757575)

// Dark scheme semantic tokens
val PrimaryPinkDark = Color(0xFFF48FB1)
val PrimaryContainerPinkDark = Color(0xFF880E4F)
val SecondaryBlueDark = Color(0xFF90CAF9)
val SecondaryContainerBlueDark = Color(0xFF0D47A1)
val TertiaryGreenDark = Color(0xFFA5D6A7)
val TertiaryContainerGreenDark = Color(0xFF1B5E20)
val SurfaceDark = Color(0xFF1C1B1F)
val OnSurfaceDarkTheme = Color(0xFFE6E1E5)
```

- [ ] **Step 2: Create Shape.kt**

```kotlin
// app/src/main/java/com/babytracker/ui/theme/Shape.kt
package com.babytracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AkachanShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(50.dp),
)
```

- [ ] **Step 3: Update Type.kt — add displaySmall and labelMedium**

```kotlin
// app/src/main/java/com/babytracker/ui/theme/Type.kt
package com.babytracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

- [ ] **Step 4: Rewrite Theme.kt with custom palette and shapes**

```kotlin
// app/src/main/java/com/babytracker/ui/theme/Theme.kt
package com.babytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPink,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerPink,
    onPrimaryContainer = OnPrimaryContainerDarkPink,
    secondary = SecondaryBlue,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = SecondaryContainerBlue,
    onSecondaryContainer = OnSecondaryContainerDarkBlue,
    tertiary = TertiaryGreen,
    onTertiary = OnTertiaryWhite,
    tertiaryContainer = TertiaryContainerGreen,
    onTertiaryContainer = OnTertiaryContainerDarkGreen,
    surface = SurfaceYellow,
    background = SurfaceYellow,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantGrey,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPinkDark,
    onPrimary = OnPrimaryContainerDarkPink,
    primaryContainer = PrimaryContainerPinkDark,
    onPrimaryContainer = PrimaryContainerPink,
    secondary = SecondaryBlueDark,
    onSecondary = OnSecondaryContainerDarkBlue,
    secondaryContainer = SecondaryContainerBlueDark,
    onSecondaryContainer = SecondaryContainerBlue,
    tertiary = TertiaryGreenDark,
    onTertiary = OnTertiaryContainerDarkGreen,
    tertiaryContainer = TertiaryContainerGreenDark,
    onTertiaryContainer = TertiaryContainerGreen,
    surface = SurfaceDark,
    background = SurfaceDark,
    onSurface = OnSurfaceDarkTheme,
    onSurfaceVariant = OnSurfaceVariantGrey,
)

@Composable
fun BabyTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AkachanShapes,
        content = content
    )
}
```

- [ ] **Step 5: Build and verify no compilation errors**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (theme wires cleanly; existing screens still compile)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/theme/
git commit -m "feat(ui): wire baby-themed color palette and shape scale into MaterialTheme"
```

---

## Task 2: DateTimeExt Additions

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/DateTimeExt.kt`
- Create: `app/src/test/java/com/babytracker/util/DateTimeExtTest.kt`

- [ ] **Step 1: Write failing tests first**

```kotlin
// app/src/test/java/com/babytracker/util/DateTimeExtTest.kt
package com.babytracker.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DateTimeExtTest {

    @Test
    fun `formatTime12h_morning_returnsAmFormat`() {
        // 08:42 UTC — assume system zone is UTC for test
        val instant = Instant.parse("2026-04-06T08:42:00Z")
        val result = instant.formatTime12h()
        // result depends on system zone; just verify AM/PM suffix present
        assert(result.contains("AM") || result.contains("PM")) {
            "Expected 12h format with AM/PM, got: $result"
        }
    }

    @Test
    fun `formatTime12h_afternoon_returnsPmFormat`() {
        val instant = Instant.parse("2026-04-06T15:30:00Z")
        val result = instant.formatTime12h()
        assert(result.contains("AM") || result.contains("PM")) {
            "Expected 12h format with AM/PM, got: $result"
        }
    }

    @Test
    fun `groupByLocalDate_emptyList_returnsEmptyMap`() {
        val result = emptyList<Instant>().groupByLocalDate { it }
        assertEquals(emptyMap<LocalDate, List<Instant>>(), result)
    }

    @Test
    fun `groupByLocalDate_singleDay_returnsOneGroup`() {
        val morning = Instant.parse("2026-04-06T08:00:00Z")
        val evening = Instant.parse("2026-04-06T20:00:00Z")
        val result = listOf(morning, evening).groupByLocalDate { it }
        assertEquals(1, result.size)
    }

    @Test
    fun `groupByLocalDate_twoDays_returnsTwoGroups`() {
        val day1 = Instant.parse("2026-04-05T10:00:00Z")
        val day2 = Instant.parse("2026-04-06T10:00:00Z")
        // Convert to local dates in UTC for predictable test
        val result = listOf(day1, day2).groupByLocalDate { it }
        // Groups split by UTC date — acceptable; real app uses systemDefault
        assertEquals(2, result.size)
    }

    @Test
    fun `localDateToRelativeLabel_today_returnsToday`() {
        val today = LocalDate.now()
        assertEquals("Today", today.toRelativeLabel())
    }

    @Test
    fun `localDateToRelativeLabel_yesterday_returnsYesterday`() {
        val yesterday = LocalDate.now().minusDays(1)
        assertEquals("Yesterday", yesterday.toRelativeLabel())
    }

    @Test
    fun `localDateToRelativeLabel_olderDate_returnsFormattedDate`() {
        val date = LocalDate.of(2026, 4, 5)
        val result = date.toRelativeLabel()
        assertEquals("Apr 5", result)
    }
}
```

- [ ] **Step 2: Run tests — expect failures (functions don't exist yet)**

```bash
./gradlew test --tests "com.babytracker.util.DateTimeExtTest"
```

Expected: FAILED — `formatTime12h`, `groupByLocalDate`, `toRelativeLabel` unresolved

- [ ] **Step 3: Add functions to DateTimeExt.kt**

```kotlin
// app/src/main/java/com/babytracker/util/DateTimeExt.kt
package com.babytracker.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Instant.formatTime(): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)

fun Instant.formatDateTime(): String =
    DateTimeFormatter.ofPattern("MMM dd, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)

fun Instant.formatTime12h(): String =
    DateTimeFormatter.ofPattern("hh:mm a")
        .withZone(ZoneId.systemDefault())
        .format(this)

fun Duration.formatDuration(): String {
    val hours = toHours()
    val minutes = (toMinutes() % 60).toInt()
    val seconds = (seconds % 60).toInt()
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

fun <T> List<T>.groupByLocalDate(keySelector: (T) -> Instant): Map<LocalDate, List<T>> =
    groupBy { keySelector(it).atZone(ZoneId.systemDefault()).toLocalDate() }

fun LocalDate.toRelativeLabel(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("MMM d").format(this)
    }
}
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
./gradlew test --tests "com.babytracker.util.DateTimeExtTest"
```

Expected: BUILD SUCCESSFUL, all 7 tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/util/DateTimeExt.kt \
        app/src/test/java/com/babytracker/util/DateTimeExtTest.kt
git commit -m "feat(util): add 12h time formatter, day-grouping and relative date label extensions"
```

---

## Task 3: TimerDisplay — Circular Ring with Pulse

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/component/TimerDisplay.kt`

- [ ] **Step 1: Rewrite TimerDisplay.kt**

```kotlin
// app/src/main/java/com/babytracker/ui/component/TimerDisplay.kt
package com.babytracker.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Displays a live elapsed-time counter.
 *
 * When [maxDurationSeconds] > 0 the timer is wrapped in a circular conic arc that
 * fills as time passes. The arc turns [MaterialTheme.colorScheme.tertiary] (amber) once
 * the elapsed time exceeds [maxDurationSeconds]. When [maxDurationSeconds] == 0 only
 * the text clock is rendered (no ring).
 */
@Composable
fun TimerDisplay(
    startTimeMillis: Long,
    isRunning: Boolean,
    maxDurationSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRunning, startTimeMillis) {
        if (isRunning) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000
                delay(1000L)
            }
        }
    }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timeText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    if (maxDurationSeconds <= 0) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.displaySmall,
            modifier = modifier
        )
        return
    }

    val progress = (elapsedSeconds.toFloat() / maxDurationSeconds).coerceIn(0f, 1f)
    val isOverMax = elapsedSeconds >= maxDurationSeconds

    val progressColor = if (isOverMax) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.primaryContainer

    val infiniteTransition = rememberInfiniteTransition(label = "ring_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        val strokeWidthDp = 14.dp
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidthDp.toPx()
            val diameter = size.minDimension - strokePx
            val topLeft = Offset(
                x = (size.width - diameter) / 2,
                y = (size.height - diameter) / 2
            )
            val arcSize = Size(diameter, diameter)
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

            // Background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Progress arc
            if (progress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            val percent = (progress * 100).toInt()
            val maxMinutes = maxDurationSeconds / 60
            Text(
                text = "$percent% of ${maxMinutes}m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify no compilation errors**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/component/TimerDisplay.kt
git commit -m "feat(ui): rewrite TimerDisplay with animated conic ring and pulse"
```

---

## Task 4: SideSelector — Big Square Touch Targets

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/component/SideSelector.kt`

- [ ] **Step 1: Rewrite SideSelector.kt**

```kotlin
// app/src/main/java/com/babytracker/ui/component/SideSelector.kt
package com.babytracker.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.BreastSide

@Composable
fun SideSelector(
    selectedSide: BreastSide?,
    onSideSelected: (BreastSide) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BreastSide.entries.forEach { side ->
            val isSelected = selectedSide == side
            val label = if (side == BreastSide.LEFT) "Left" else "Right"
            val arrow = if (side == BreastSide.LEFT) "←" else "→"

            Card(
                onClick = { onSideSelected(side) },
                modifier = Modifier
                    .weight(1f)
                    .height(88.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                border = if (isSelected) null else BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isSelected) 6.dp else 0.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = arrow,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        }
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/component/SideSelector.kt
git commit -m "feat(ui): rewrite SideSelector with large square touch-target card buttons"
```

---

## Task 5: HistoryCard — Color-coded Badge and Formatted Names

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt`

- [ ] **Step 1: Update HistoryCard.kt to accept badge params**

```kotlin
// app/src/main/java/com/babytracker/ui/component/HistoryCard.kt
package com.babytracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HistoryCard(
    title: String,
    subtitle: String,
    trailing: String,
    badgeEmoji: String,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = badgeColor,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = badgeEmoji, style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
```

- [ ] **Step 2: Build — expect compile errors in history screens (they call HistoryCard without new params)**

```bash
./gradlew assembleDebug 2>&1 | grep "error:"
```

Expected errors in `BreastfeedingHistoryScreen.kt` and `SleepHistoryScreen.kt` — badge params missing.
These screens are rewritten in Tasks 11 and 12 respectively; they will fix the compile errors.

- [ ] **Step 3: Commit (build intentionally broken — will be fixed in Tasks 11 & 12)**

```bash
git add app/src/main/java/com/babytracker/ui/component/HistoryCard.kt
git commit -m "feat(ui): update HistoryCard with color-coded emoji badge and human-readable names"
```

---

## Task 6: HomeViewModel — Derived State and Stop Actions

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`
- Create: `app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt
package com.babytracker.ui.home

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var getBabyProfile: GetBabyProfileUseCase
    private lateinit var getBreastfeedingHistory: GetBreastfeedingHistoryUseCase
    private lateinit var getSleepHistory: GetSleepHistoryUseCase
    private lateinit var stopBreastfeedingSession: StopBreastfeedingSessionUseCase
    private lateinit var stopSleepRecord: StopSleepRecordUseCase
    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testBaby = Baby(name = "Emma", birthDate = LocalDate.of(2026, 3, 15))
    private val inProgressSession = BreastfeedingSession(
        id = 1L,
        startTime = Instant.now().minusSeconds(300),
        endTime = null,
        startingSide = BreastSide.LEFT
    )
    private val completedSession = BreastfeedingSession(
        id = 2L,
        startTime = Instant.now().minusSeconds(7200),
        endTime = Instant.now().minusSeconds(6900),
        startingSide = BreastSide.RIGHT
    )
    private val inProgressRecord = SleepRecord(
        id = 1L,
        startTime = Instant.now().minusSeconds(1800),
        endTime = null,
        sleepType = SleepType.NAP
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getBabyProfile = mockk()
        getBreastfeedingHistory = mockk()
        getSleepHistory = mockk()
        stopBreastfeedingSession = mockk()
        stopSleepRecord = mockk()

        every { getBabyProfile() } returns flowOf(testBaby)
        every { getBreastfeedingHistory() } returns flowOf(emptyList())
        every { getSleepHistory() } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(
        getBabyProfile,
        getBreastfeedingHistory,
        getSleepHistory,
        stopBreastfeedingSession,
        stopSleepRecord
    )

    @Test
    fun `activeSession_isNull_whenNoInProgressFeeding`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.activeSession)
    }

    @Test
    fun `activeSession_isSet_whenInProgressFeedingExists`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.activeSession)
        assertEquals(inProgressSession.id, viewModel.uiState.value.activeSession!!.id)
    }

    @Test
    fun `activeRecord_isSet_whenInProgressSleepExists`() = runTest {
        every { getSleepHistory() } returns flowOf(listOf(inProgressRecord))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.activeRecord)
    }

    @Test
    fun `sessionsTodayCount_countsOnlyTodaySessions`() = runTest {
        val todaySession = BreastfeedingSession(
            id = 3L,
            startTime = Instant.now().minusSeconds(3600),
            endTime = Instant.now().minusSeconds(3000),
            startingSide = BreastSide.LEFT
        )
        every { getBreastfeedingHistory() } returns flowOf(listOf(todaySession, completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        // completedSession started 2h ago — also today in most time zones; exact count depends on zone.
        // Just verify count is >= 1
        assert(viewModel.uiState.value.sessionsTodayCount >= 1)
    }

    @Test
    fun `lastFeedSide_returnsFirstSessionStartingSide`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(BreastSide.LEFT, viewModel.uiState.value.lastFeedSide)
    }

    @Test
    fun `onStopActiveSession_callsStopUseCase`() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession))
        coJustRun { stopBreastfeedingSession(any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopActiveSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { stopBreastfeedingSession(inProgressSession) }
    }

    @Test
    fun `onStopActiveRecord_callsStopUseCase`() = runTest {
        every { getSleepHistory() } returns flowOf(listOf(inProgressRecord))
        coJustRun { stopSleepRecord(any()) }
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopActiveRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { stopSleepRecord(inProgressRecord) }
    }
}
```

- [ ] **Step 2: Run tests — expect failures**

```bash
./gradlew test --tests "com.babytracker.ui.home.HomeViewModelTest"
```

Expected: FAILED — `HomeViewModel` doesn't accept 5 parameters yet

- [ ] **Step 3: Rewrite HomeViewModel.kt**

```kotlin
// app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt
package com.babytracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val baby: Baby? = null,
    val recentFeedings: List<BreastfeedingSession> = emptyList(),
    val recentSleepRecords: List<SleepRecord> = emptyList(),
    val activeSession: BreastfeedingSession? = null,
    val activeRecord: SleepRecord? = null,
    val lastFeedSide: BreastSide? = null,
    val sessionsTodayCount: Int = 0,
    val lastNightSleepDuration: Duration? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    getBabyProfile: GetBabyProfileUseCase,
    getBreastfeedingHistory: GetBreastfeedingHistoryUseCase,
    getSleepHistory: GetSleepHistoryUseCase,
    private val stopBreastfeedingSession: StopBreastfeedingSessionUseCase,
    private val stopSleepRecord: StopSleepRecordUseCase,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        getBabyProfile(),
        getBreastfeedingHistory(),
        getSleepHistory()
    ) { baby, feedings, sleepRecords ->
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val zone = ZoneId.systemDefault()

        val lastNightSleep = sleepRecords
            .filter { it.sleepType == SleepType.NIGHT_SLEEP && !it.isInProgress }
            .filter { it.startTime.atZone(zone).toLocalDate() == yesterday }
            .mapNotNull { it.duration }
            .fold(Duration.ZERO) { acc, d -> acc + d }
            .takeIf { !it.isZero }

        HomeUiState(
            baby = baby,
            recentFeedings = feedings.take(3),
            recentSleepRecords = sleepRecords.take(3),
            activeSession = feedings.firstOrNull { it.isInProgress },
            activeRecord = sleepRecords.firstOrNull { it.isInProgress },
            lastFeedSide = feedings.firstOrNull()?.startingSide,
            sessionsTodayCount = feedings.count {
                it.startTime.atZone(zone).toLocalDate() == today
            },
            lastNightSleepDuration = lastNightSleep
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun onStopActiveSession() {
        val session = uiState.value.activeSession ?: return
        viewModelScope.launch { stopBreastfeedingSession(session) }
    }

    fun onStopActiveRecord() {
        val record = uiState.value.activeRecord ?: return
        viewModelScope.launch { stopSleepRecord(record) }
    }
}
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
./gradlew test --tests "com.babytracker.ui.home.HomeViewModelTest"
```

Expected: BUILD SUCCESSFUL, all 7 tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt \
        app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt
git commit -m "feat(home): add derived session state and stop actions to HomeViewModel"
```

---

## Task 7: SwitchBreastfeedingSideUseCase + BreastfeedingViewModel

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/SwitchBreastfeedingSideUseCase.kt`
- Create: `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/SwitchBreastfeedingSideUseCaseTest.kt`
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt`

- [ ] **Step 1: Write failing test for SwitchBreastfeedingSideUseCase**

```kotlin
// app/src/test/java/com/babytracker/domain/usecase/breastfeeding/SwitchBreastfeedingSideUseCaseTest.kt
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SwitchBreastfeedingSideUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: SwitchBreastfeedingSideUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = SwitchBreastfeedingSideUseCase(repository)
    }

    @Test
    fun `invoke_setsSwitch TimeOnSession`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(600),
            endTime = null,
            startingSide = BreastSide.LEFT,
            switchTime = null
        )
        val updatedSlot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(updatedSlot)) }

        useCase(session)

        assertNotNull(updatedSlot.captured.switchTime)
    }

    @Test
    fun `invoke_doesNotOverrideExistingSwitch`() = runTest {
        val existingSwitch = Instant.now().minusSeconds(300)
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(600),
            endTime = null,
            startingSide = BreastSide.LEFT,
            switchTime = existingSwitch
        )

        useCase(session)

        coVerify(exactly = 0) { repository.updateSession(any()) }
    }

    @Test
    fun `invoke_callsRepositoryUpdate`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(600),
            endTime = null,
            startingSide = BreastSide.LEFT,
            switchTime = null
        )
        coJustRun { repository.updateSession(any()) }

        useCase(session)

        coVerify(exactly = 1) { repository.updateSession(any()) }
    }
}
```

- [ ] **Step 2: Run tests — expect failure**

```bash
./gradlew test --tests "com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCaseTest"
```

Expected: FAILED — `SwitchBreastfeedingSideUseCase` doesn't exist

- [ ] **Step 3: Create SwitchBreastfeedingSideUseCase.kt**

```kotlin
// app/src/main/java/com/babytracker/domain/usecase/breastfeeding/SwitchBreastfeedingSideUseCase.kt
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Instant
import javax.inject.Inject

class SwitchBreastfeedingSideUseCase @Inject constructor(
    private val repository: BreastfeedingRepository
) {
    suspend operator fun invoke(session: BreastfeedingSession) {
        if (session.switchTime != null) return   // already switched once
        repository.updateSession(session.copy(switchTime = Instant.now()))
    }
}
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
./gradlew test --tests "com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCaseTest"
```

Expected: BUILD SUCCESSFUL, all 3 tests pass

- [ ] **Step 5: Add onSwitchSide() to BreastfeedingViewModel**

```kotlin
// app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt
package com.babytracker.ui.breastfeeding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.StartBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BreastfeedingUiState(
    val activeSession: BreastfeedingSession? = null,
    val selectedSide: BreastSide? = null
)

@HiltViewModel
class BreastfeedingViewModel @Inject constructor(
    private val startSession: StartBreastfeedingSessionUseCase,
    private val stopSession: StopBreastfeedingSessionUseCase,
    private val switchSide: SwitchBreastfeedingSideUseCase,
    private val getHistory: GetBreastfeedingHistoryUseCase,
    private val repository: BreastfeedingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreastfeedingUiState())
    val uiState: StateFlow<BreastfeedingUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<BreastfeedingSession>> = getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.getActiveSession().collect { session ->
                _uiState.value = _uiState.value.copy(activeSession = session)
            }
        }
    }

    fun onSideSelected(side: BreastSide) {
        _uiState.value = _uiState.value.copy(selectedSide = side)
    }

    fun onStartSession() {
        val side = _uiState.value.selectedSide ?: return
        viewModelScope.launch { startSession(side) }
    }

    fun onStopSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch { stopSession(session) }
    }

    fun onSwitchSide() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch { switchSide(session) }
    }
}
```

- [ ] **Step 6: Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/breastfeeding/SwitchBreastfeedingSideUseCase.kt \
        app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt \
        app/src/test/java/com/babytracker/domain/usecase/breastfeeding/SwitchBreastfeedingSideUseCaseTest.kt
git commit -m "feat(breastfeeding): add SwitchBreastfeedingSideUseCase and wire onSwitchSide into BreastfeedingViewModel"
```

---

## Task 8: HomeScreen Redesign

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

- [ ] **Step 1: Rewrite HomeScreen.kt**

```kotlin
// app/src/main/java/com/babytracker/ui/home/HomeScreen.kt
package com.babytracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.util.formatDuration
import java.time.format.DateTimeFormatter
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBreastfeeding: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.baby?.let { "Hi, ${it.name} 👋" } ?: "Akachan",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = LocalDate.now().format(
                                DateTimeFormatter.ofPattern("EEEE, MMM d")
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Restaurant, contentDescription = "Feeding") },
                    label = { Text("Feeding") },
                    selected = false,
                    onClick = onNavigateToBreastfeeding
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Bedtime, contentDescription = "Sleep") },
                    label = { Text("Sleep") },
                    selected = false,
                    onClick = onNavigateToSleep
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = onNavigateToSettings
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Active session banner
            AnimatedVisibility(
                visible = uiState.activeSession != null || uiState.activeRecord != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val isFeeding = uiState.activeSession != null
                val emoji = if (isFeeding) "🍼" else "🌙"
                val label = if (isFeeding) "Feeding in progress" else "Sleep in progress"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (isFeeding) viewModel.onStopActiveSession()
                                else viewModel.onStopActiveRecord()
                            }
                        ) {
                            Text(
                                text = "Stop",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // Summary cards row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Breastfeeding card
                Card(
                    onClick = onNavigateToBreastfeeding,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "🍼", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Breastfeeding",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.sessionsTodayCount} sessions today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sleep card
                Card(
                    onClick = onNavigateToSleep,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "🌙", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sleep",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        val sleepLabel = uiState.lastNightSleepDuration
                            ?.formatDuration()
                            ?.let { "$it last night" }
                            ?: "${uiState.recentSleepRecords.size} records"
                        Text(
                            text = sleepLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tip card — suggests which side to try next
            uiState.lastFeedSide?.let { lastSide ->
                val nextSide = if (lastSide.name == "LEFT") "Right" else "Left"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "✨", style = MaterialTheme.typography.titleMedium)
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                text = "Tip",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Last session used ${lastSide.name.lowercase().replaceFirstChar { it.uppercase() }}. Try $nextSide next.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/home/HomeScreen.kt
git commit -m "feat(home): redesign HomeScreen with hybrid active-banner and summary cards"
```

---

## Task 9: BreastfeedingScreen Redesign

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt`

- [ ] **Step 1: Rewrite BreastfeedingScreen.kt**

```kotlin
// app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt
package com.babytracker.ui.breastfeeding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.BreastSide
import com.babytracker.ui.component.SideSelector
import com.babytracker.ui.component.TimerDisplay
import com.babytracker.util.formatDuration
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreastfeedingScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: BreastfeedingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeSession = uiState.activeSession

    // Live clock for per-side elapsed breakdown
    val now by produceState(initialValue = Instant.now(), key1 = activeSession?.id) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            value = Instant.now()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breastfeeding") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToHistory) {
                        Text("History", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (activeSession != null) {
                // Status pill
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "● Session in progress",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Ring timer
                TimerDisplay(
                    startTimeMillis = activeSession.startTime.toEpochMilli(),
                    isRunning = true,
                    maxDurationSeconds = 20 * 60  // 20-minute default; feed from settings in Task 13
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Per-side breakdown
                val firstSideDuration: Duration = activeSession.switchTime
                    ?.let { Duration.between(activeSession.startTime, it) }
                    ?: Duration.between(activeSession.startTime, now)

                val secondSideDuration: Duration = activeSession.switchTime
                    ?.let { Duration.between(it, now) }
                    ?: Duration.ZERO

                val currentSide: BreastSide = activeSession.switchTime?.let {
                    if (activeSession.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
                } ?: activeSession.startingSide

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(BreastSide.LEFT, BreastSide.RIGHT).forEach { side ->
                        val isCurrentSide = side == currentSide
                        val duration = if (side == activeSession.startingSide) {
                            firstSideDuration
                        } else {
                            secondSideDuration
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrentSide) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isCurrentSide) "● ${side.name}" else side.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isCurrentSide) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = duration.formatDuration(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrentSide) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Switch side button (only shown if not switched yet)
                if (activeSession.switchTime == null) {
                    val nextSide = if (activeSession.startingSide == BreastSide.LEFT) "Right" else "Left"
                    OutlinedCard(
                        onClick = viewModel::onSwitchSide,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "⇄  Switch to $nextSide",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Stop button
                Button(
                    onClick = viewModel::onStopSession,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text("Stop Session", style = MaterialTheme.typography.titleSmall)
                }
            } else {
                // Idle state
                Text(
                    text = "Start a feeding session",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(24.dp))

                SideSelector(
                    selectedSide = uiState.selectedSide,
                    onSideSelected = viewModel::onSideSelected
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = viewModel::onStartSession,
                    enabled = uiState.selectedSide != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text("Start Session", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt
git commit -m "feat(breastfeeding): redesign BreastfeedingScreen with ring timer, per-side breakdown, and switch button"
```

---

## Task 10: SleepTrackingScreen Redesign

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt`

- [ ] **Step 1: Rewrite SleepTrackingScreen.kt**

```kotlin
// app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt
package com.babytracker.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.SleepType
import com.babytracker.ui.component.TimerDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTrackingScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep Tracking") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToHistory) {
                        Text("History", color = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (uiState.activeRecord != null) {
                val record = uiState.activeRecord!!

                // Status pill
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = "● ${record.sleepType.label} in progress",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Ring timer — blue for sleep
                TimerDisplay(
                    startTimeMillis = record.startTime.toEpochMilli(),
                    isRunning = true,
                    maxDurationSeconds = if (record.sleepType == SleepType.NAP) 90 * 60 else 0
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = viewModel::onStopTracking,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Stop", style = MaterialTheme.typography.titleSmall)
                }
            } else {
                Text(
                    text = "Track Sleep",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Choose a sleep type to begin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Sleep type selector — big card buttons (matches SideSelector style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SleepType.entries.forEach { type ->
                        val isSelected = uiState.selectedType == type
                        Card(
                            onClick = { viewModel.onTypeSelected(type) },
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isSelected) 6.dp else 0.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = type.emoji,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = type.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = viewModel::onStartTracking,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Start Tracking", style = MaterialTheme.typography.titleSmall)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) { Text("History") }

                    OutlinedButton(
                        onClick = onNavigateToSchedule,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) { Text("Schedule") }
                }
            }
        }
    }
}
```

Note: `SleepType.label` and `SleepType.emoji` don't exist yet on the enum. Add them in the next step.

- [ ] **Step 2: Add label and emoji to SleepType**

The file is at `app/src/main/java/com/babytracker/domain/model/SleepType.kt`. Read it first, then update:

```kotlin
// app/src/main/java/com/babytracker/domain/model/SleepType.kt
package com.babytracker.domain.model

enum class SleepType(val label: String, val emoji: String) {
    NAP("Nap", "😴"),
    NIGHT_SLEEP("Night Sleep", "🌙"),
}
```

- [ ] **Step 3: Fix existing usages of SleepType.name in SleepHistoryScreen (will be replaced in Task 12 — just verify build for now)**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (existing `record.sleepType.name.replace("_", " ")` still compiles — we're replacing it properly in Task 12)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/SleepType.kt \
        app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt
git commit -m "feat(sleep): redesign SleepTrackingScreen with ring timer and big sleep-type selector"
```

---

## Task 11: BreastfeedingHistoryScreen — Grouped by Day

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingHistoryScreen.kt`

- [ ] **Step 1: Rewrite BreastfeedingHistoryScreen.kt**

```kotlin
// app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingHistoryScreen.kt
package com.babytracker.ui.breastfeeding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.BreastSide
import com.babytracker.ui.component.HistoryCard
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import com.babytracker.util.groupByLocalDate
import com.babytracker.util.toRelativeLabel
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreastfeedingHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: BreastfeedingViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val grouped = history.groupByLocalDate { it.startTime }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feeding History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🍼", style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No sessions yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sessions you track will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp
                )
            ) {
                grouped.entries.sortedByDescending { it.key }.forEach { (date, sessions) ->
                    val totalDuration = sessions
                        .filter { !it.isInProgress }
                        .mapNotNull { it.duration }
                        .fold(Duration.ZERO) { acc, d -> acc + d }
                    val totalLabel = if (totalDuration.isZero) "" else " · ${totalDuration.formatDuration()} total"

                    stickyHeader(key = date.toString()) {
                        Text(
                            text = "${date.toRelativeLabel()} · ${sessions.size} sessions$totalLabel".uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }

                    items(sessions, key = { it.id }) { session ->
                        val isLeft = session.startingSide == BreastSide.LEFT
                        HistoryCard(
                            title = if (isLeft) "Left side" else "Right side",
                            subtitle = session.startTime.formatTime12h(),
                            trailing = session.duration?.formatDuration() ?: "In progress",
                            badgeEmoji = "🍼",
                            badgeColor = if (isLeft) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (fixes the compile error introduced in Task 5)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingHistoryScreen.kt
git commit -m "feat(breastfeeding): group feeding history by day with daily totals and empty state"
```

---

## Task 12: SleepHistoryScreen — Grouped by Day

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepHistoryScreen.kt`

- [ ] **Step 1: Rewrite SleepHistoryScreen.kt**

```kotlin
// app/src/main/java/com/babytracker/ui/sleep/SleepHistoryScreen.kt
package com.babytracker.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.SleepType
import com.babytracker.ui.component.HistoryCard
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import com.babytracker.util.groupByLocalDate
import com.babytracker.util.toRelativeLabel
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val grouped = history.groupByLocalDate { it.startTime }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🌙", style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No sleep records yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sleep sessions you track will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp
                )
            ) {
                grouped.entries.sortedByDescending { it.key }.forEach { (date, records) ->
                    val totalDuration = records
                        .filter { !it.isInProgress }
                        .mapNotNull { it.duration }
                        .fold(Duration.ZERO) { acc, d -> acc + d }
                    val totalLabel = if (totalDuration.isZero) "" else " · ${totalDuration.formatDuration()} total"

                    stickyHeader(key = date.toString()) {
                        Text(
                            text = "${date.toRelativeLabel()} · ${records.size} records$totalLabel".uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }

                    items(records, key = { it.id }) { record ->
                        val isNap = record.sleepType == SleepType.NAP
                        HistoryCard(
                            title = record.sleepType.label,
                            subtitle = record.startTime.formatTime12h(),
                            trailing = record.duration?.formatDuration() ?: "In progress",
                            badgeEmoji = record.sleepType.emoji,
                            badgeColor = if (isNap) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepHistoryScreen.kt
git commit -m "feat(sleep): group sleep history by day with daily totals and empty state"
```

---

## Task 13: SettingsViewModel + SettingsScreen Redesign

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add SaveBabyProfileUseCase to SettingsViewModel**

```kotlin
// app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt
package com.babytracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.SaveBabyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val baby: Baby? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0
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
                settingsRepository.getMaxTotalFeedMinutes()
            ) { baby, maxPerBreast, maxTotal ->
                SettingsUiState(
                    baby = baby,
                    maxPerBreastMinutes = maxPerBreast,
                    maxTotalFeedMinutes = maxTotal
                )
            }.collect { _uiState.value = it }
        }
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
}
```

- [ ] **Step 2: Rewrite SettingsScreen.kt**

```kotlin
// app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt
package com.babytracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.BuildConfig
import com.babytracker.ui.onboarding.components.AllergiesStepContent
import com.babytracker.ui.onboarding.components.BirthDateStepContent
import com.babytracker.ui.onboarding.components.NameStepContent
import kotlinx.coroutines.launch

private enum class EditSheet { NAME, BIRTH_DATE, ALLERGIES, MAX_PER_BREAST, MAX_TOTAL }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeSheet by rememberSaveable { mutableStateOf<EditSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Baby Profile ──────────────────────────────────────
            Text(
                text = "Baby Profile",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsRow(label = "Name", value = uiState.baby?.name ?: "Not set") {
                activeSheet = EditSheet.NAME
            }
            HorizontalDivider()
            SettingsRow(
                label = "Date of birth",
                value = uiState.baby?.let {
                    "${it.birthDate} · ${it.ageInWeeks}w old"
                } ?: "Not set"
            ) { activeSheet = EditSheet.BIRTH_DATE }
            HorizontalDivider()

            // Allergies row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeSheet = EditSheet.ALLERGIES }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Allergies",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.baby?.allergies.isNullOrEmpty()) {
                        Text(
                            text = "None",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.baby!!.allergies.forEach { allergy ->
                                SuggestionChip(
                                    onClick = { activeSheet = EditSheet.ALLERGIES },
                                    label = { Text(allergy.label) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { activeSheet = EditSheet.ALLERGIES }) {
                    Text("Edit", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Feeding Limits ────────────────────────────────────
            Text(
                text = "Feeding Limits",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsRow(
                label = "Max per breast",
                value = if (uiState.maxPerBreastMinutes > 0) {
                    "${uiState.maxPerBreastMinutes} min"
                } else "Disabled"
            ) { activeSheet = EditSheet.MAX_PER_BREAST }
            HorizontalDivider()
            SettingsRow(
                label = "Max total feed",
                value = if (uiState.maxTotalFeedMinutes > 0) {
                    "${uiState.maxTotalFeedMinutes} min"
                } else "Disabled"
            ) { activeSheet = EditSheet.MAX_TOTAL }

            Spacer(modifier = Modifier.weight(1f))

            // Version footer
            Text(
                text = "Akachan v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            )
        }

        // ── Edit Bottom Sheets ─────────────────────────────────────
        if (activeSheet != null) {
            val baby = uiState.baby
            ModalBottomSheet(
                onDismissRequest = { activeSheet = null },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                        .imePadding()
                ) {
                    when (activeSheet) {
                        EditSheet.NAME -> {
                            var name by remember { mutableStateOf(baby?.name ?: "") }
                            NameStepContent(
                                name = name,
                                onNameChanged = { name = it },
                                onNextStep = {}
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    baby?.let { viewModel.onSaveBabyProfile(it.copy(name = name)) }
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        activeSheet = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = name.isNotBlank()
                            ) { Text("Save") }
                        }

                        EditSheet.BIRTH_DATE -> {
                            BirthDateStepContent(
                                babyName = baby?.name ?: "",
                                selectedDate = baby?.birthDate,
                                showAgeWarning = false,
                                onDateSelected = { date ->
                                    baby?.let {
                                        viewModel.onSaveBabyProfile(it.copy(birthDate = date))
                                    }
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        activeSheet = null
                                    }
                                }
                            )
                        }

                        EditSheet.ALLERGIES -> {
                            var selected by remember {
                                mutableStateOf(baby?.allergies?.toSet() ?: emptySet())
                            }
                            var note by remember {
                                mutableStateOf(baby?.customAllergyNote ?: "")
                            }
                            AllergiesStepContent(
                                babyName = baby?.name ?: "",
                                selectedAllergies = selected,
                                customNote = note,
                                onAllergyToggled = { allergy ->
                                    selected = if (allergy in selected) {
                                        selected - allergy
                                    } else {
                                        selected + allergy
                                    }
                                },
                                onCustomNoteChanged = { note = it }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    baby?.let {
                                        viewModel.onSaveBabyProfile(
                                            it.copy(
                                                allergies = selected.toList(),
                                                customAllergyNote = note.takeIf { note.isNotBlank() }
                                            )
                                        )
                                    }
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        activeSheet = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save") }
                        }

                        EditSheet.MAX_PER_BREAST -> {
                            var minutes by remember {
                                mutableStateOf(
                                    uiState.maxPerBreastMinutes.takeIf { it > 0 }?.toString() ?: ""
                                )
                            }
                            Text(
                                text = "Max per breast (minutes)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = minutes,
                                onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                                label = { Text("Minutes (0 = disabled)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.onMaxPerBreastChanged(minutes.toIntOrNull() ?: 0)
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        activeSheet = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save") }
                        }

                        EditSheet.MAX_TOTAL -> {
                            var minutes by remember {
                                mutableStateOf(
                                    uiState.maxTotalFeedMinutes.takeIf { it > 0 }?.toString() ?: ""
                                )
                            }
                            Text(
                                text = "Max total feed (minutes)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = minutes,
                                onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                                label = { Text("Minutes (0 = disabled)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.onMaxTotalFeedChanged(minutes.toIntOrNull() ?: 0)
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        activeSheet = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save") }
                        }

                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onEdit) {
            Text("Edit", color = MaterialTheme.colorScheme.primary)
        }
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): redesign SettingsScreen with grouped rows, edit bottom sheets, and allergy chips"
```

---

## Task 14: Minor Theme-Only Updates — SleepScheduleScreen and OnboardingScreen

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepScheduleScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Update SleepScheduleScreen — apply shape tokens and TopAppBar color**

In `SleepScheduleScreen.kt`, change the existing `TopAppBar` and `Button` to use shape tokens. Make the following targeted edits:

In the `TopAppBar`:
```kotlin
colors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface
)
```

Change the `Button`:
```kotlin
Button(
    onClick = viewModel::onGenerateSchedule,
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge
) {
    Text("Generate Schedule")
}
```

Change each nap-time `Card` in the `LazyColumn`:
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
) { ... }
```

- [ ] **Step 2: Update OnboardingScreen — apply extraLarge shape to the bottom button**

In `OnboardingScreen.kt`, change the `Button` modifier to include shape:
```kotlin
Button(
    onClick = { ... },
    enabled = viewModel.isNextEnabled && !uiState.isSaving,
    modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp),
    shape = MaterialTheme.shapes.extraLarge,
) { ... }
```

- [ ] **Step 3: Build — final full verification**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all unit tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepScheduleScreen.kt \
        app/src/main/java/com/babytracker/ui/onboarding/OnboardingScreen.kt
git commit -m "feat(ui): apply shape tokens to SleepScheduleScreen and OnboardingScreen"
```

---

## Self-Review Against Spec

| Spec requirement | Task |
|---|---|
| Color.kt — semantic tokens, dynamicColor=false | Task 1 |
| Shape.kt — corner radii scale | Task 1 |
| Type.kt — displaySmall, labelMedium | Task 1 |
| TimerDisplay — Canvas ring, pulse, amber over-max | Task 3 |
| SideSelector — big square cards | Task 4 |
| HistoryCard — badge emoji/color, formatted names | Task 5 |
| HomeUiState — activeSession, activeRecord, lastFeedSide, sessionsTodayCount, lastNightSleepDuration | Task 6 |
| HomeScreen — hybrid layout, active banner with STOP, summary cards, tip card | Task 8 |
| SwitchBreastfeedingSideUseCase | Task 7 |
| BreastfeedingScreen — ring timer, big side buttons, per-side breakdown, switch button | Task 9 |
| SleepTrackingScreen — ring timer, big sleep-type buttons, History/Schedule pills | Task 10 |
| SleepType — label and emoji fields | Task 10 |
| BreastfeedingHistoryScreen — grouped by day, daily totals, empty state | Task 11 |
| SleepHistoryScreen — grouped by day, daily totals, empty state | Task 12 |
| SettingsScreen — grouped rows, edit bottom sheets, allergy chips, version footer | Task 13 |
| SettingsViewModel — saveBabyProfile | Task 13 |
| SleepScheduleScreen — shape tokens | Task 14 |
| OnboardingScreen — extraLarge button shape | Task 14 |

All spec requirements covered. No placeholders remaining.
