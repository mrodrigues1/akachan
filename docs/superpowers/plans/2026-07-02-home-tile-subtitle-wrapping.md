# Home Tile Subtitle Wrapping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make home-tile subtitles wrap completely while preserving ellipsis behavior for non-subtitle labels.

**Architecture:** Change only the existing subtitle composables in `HomeScreen.kt`. The cards already use minimum rather than fixed heights, so Compose will expand each tile to fit the wrapped text without new sizing logic.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric, JUnit 4

---

### Task 1: Allow feeding-prediction subtitles to size their tiles

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt:270-281,459-473`
- Test: `app/src/test/java/com/babytracker/ui/home/HomeTileTitleTest.kt`

- [x] **Step 1: Write the failing regression test**

Add a focused source-contract test using the repository's existing source-inspection pattern:

```kotlin
@Test
fun `feeding prediction subtitle has no line limits`() {
    val source = sequenceOf(
        File("src/main/java/com/babytracker/ui/home/HomeScreen.kt"),
        File("app/src/main/java/com/babytracker/ui/home/HomeScreen.kt"),
    ).first(File::exists).readText()
    val subtitle = source
        .substringAfter("internal fun FeedingPredictionSubtitle(")
        .substringBefore("internal fun PumpingHomeCard(")

    assertFalse(subtitle.contains("maxLines"))
    assertFalse(subtitle.contains("TextOverflow.Ellipsis"))
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.babytracker.ui.home.HomeTileTitleTest"
```

Expected: the new prediction-subtitle test fails because the current two-line/one-line limits report visual overflow at narrow width.

- [x] **Step 3: Remove feeding-prediction subtitle truncation only**

In both `Text` calls inside `FeedingPredictionSubtitle`, remove `maxLines` and `overflow`:

```kotlin
Text(
    text = primaryText,
    style = MaterialTheme.typography.bodyMedium,
    color = primaryColor,
)
```

Keep `HomeTileStatusText` unchanged because the partner dashboard passes an explicit line limit. Also keep ellipsis behavior on the top-bar greeting, badges, provider names, countdowns, and question labels.

- [x] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2. Expected: `HomeTileTitleTest` passes.

- [x] **Step 5: Run repository validation**

Run:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

Expected: both commands exit successfully.

- [x] **Step 6: Commit the fix**

```powershell
git add app/src/main/java/com/babytracker/ui/home/HomeScreen.kt app/src/test/java/com/babytracker/ui/home/HomeTileTitleTest.kt docs/superpowers/plans/2026-07-02-home-tile-subtitle-wrapping.md
git commit -m "fix(home): display complete tile subtitles"
```
