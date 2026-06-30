# Home Tile Title Wrapping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show every home tile title without ellipsis, wrapping only between words and shrinking only when a word would otherwise split.

**Architecture:** Keep the behavior inside the shared `HomeTrackerTile`. A small private title composable uses the existing width-based style, measures each rendered result, and steps down to 14sp only while the text overflows two lines or a line ends inside a word.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose UI instrumentation tests, Android CLI.

---

### Task 1: Add the failing narrow-title regression test

**Files:**
- Create: `app/src/test/java/com/babytracker/ui/home/HomeTileTitleTest.kt`

- [ ] **Step 1: Add a host-side Robolectric Compose test**

Use `createComposeRule()` with `RobolectricTestRunner` and SDK 34. Add a text-layout assertion helper and two focused tests with imports for `width`, `Color`, `SemanticsActions`, `TextLayoutResult`, `TextStyle`, and `sp`:

```kotlin
private fun layoutResultFor(text: String): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    val action = composeRule
        .onNodeWithText(text, useUnmergedTree = true)
        .fetchSemanticsNode()
        .config[SemanticsActions.GetTextLayoutResult]
        .action
    check(action?.invoke(results) == true)
    return results.single()
}

private fun assertBreaksOnlyAtWhitespace(text: String, result: TextLayoutResult) {
    repeat(result.lineCount - 1) { line ->
        val end = result.getLineEnd(line)
        assertTrue(text[end - 1].isWhitespace() || text[end].isWhitespace())
    }
}
```

Add tests that render the real shared tile:

```kotlin
@Test
fun homeTrackerTile_wrapsLongTitle_withoutEllipsis() {
    val title = "Histórico de mamadas"
    composeRule.setContent {
        BabyTrackerTheme {
            HomeTrackerTile(
                title = title,
                contentDescription = title,
                containerColor = Color.White,
                contentColor = Color.Black,
                onClick = {},
                icon = {},
                modifier = Modifier.width(150.dp),
            ) {}
        }
    }

    val result = layoutResultFor(title)
    assertTrue(result.lineCount <= 2)
    assertTrue(!result.hasVisualOverflow)
    assertBreaksOnlyAtWhitespace(title, result)
}

@Test
fun wordBoundaryDetection_distinguishesWrappingFromSplitting() {
    assertTrue("Amamentação".isWordSplitAt(5))
    assertFalse("Histórico de mamadas".isWordSplitAt(10))
}
```

- [ ] **Step 2: Run the tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.babytracker.ui.home.HomeTileTitleTest"
```

Expected: the new wrapping test fails because `HomeTrackerTile` still uses one line and `TextOverflow.Ellipsis`.

### Task 2: Implement word-safe adaptive titles

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt:124-195`
- Test: `app/src/test/java/com/babytracker/ui/home/HomeTileTitleTest.kt`

- [ ] **Step 1: Add the minimal layout-result predicate**

Add the `TextLayoutResult` import and this helper beside `fitHomeTileTitle`:

```kotlin
private fun TextLayoutResult.splitsWord(text: String): Boolean =
    (0 until lineCount - 1).any { line ->
        val end = getLineEnd(line)
        end in 1 until text.length &&
            !text[end - 1].isWhitespace() &&
            !text[end].isWhitespace()
    }
```

- [ ] **Step 2: Extract the shared adaptive title composable**

Add `drawWithContent` and `LineBreak` imports, then add:

```kotlin
@Composable
private fun HomeTileTitle(
    title: String,
    style: TextStyle,
    color: Color,
    width: Dp,
) {
    val initialStyle = style.fitHomeTileTitle(width).copy(lineBreak = LineBreak.Heading)
    var fittedStyle by remember(title, initialStyle, width) { mutableStateOf(initialStyle) }
    var ready by remember(title, initialStyle, width) { mutableStateOf(false) }

    Text(
        text = title,
        modifier = Modifier.drawWithContent { if (ready) drawContent() },
        style = fittedStyle,
        color = color,
        maxLines = 2,
        onTextLayout = { result ->
            val needsSmallerText = result.hasVisualOverflow || result.splitsWord(title)
            if (needsSmallerText && fittedStyle.fontSize > 14.sp) {
                fittedStyle = fittedStyle.copy(fontSize = fittedStyle.fontSize.value.minus(1f).sp)
            } else {
                ready = true
            }
        },
    )
}
```

- [ ] **Step 3: Route the shared tile title through the new composable**

Replace the existing title `Text` inside `BoxWithConstraints` with:

```kotlin
HomeTileTitle(
    title = title,
    style = titleStyle,
    color = contentColor,
    width = maxWidth,
)
```

- [ ] **Step 4: Run the focused test and verify GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.babytracker.ui.home.HomeTileTitleTest"
```

Expected: both `HomeTileTitleTest` tests pass.

### Task 3: Validate the feature and commit it

**Files:**
- Verify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- Verify: `app/src/test/java/com/babytracker/ui/home/HomeTileTitleTest.kt`

- [ ] **Step 1: Run the full local unit suite**

```powershell
.\gradlew.bat test
```

Expected: build succeeds with zero failed tests.

- [ ] **Step 2: Run production build validation**

```powershell
.\gradlew.bat build
```

Expected: build succeeds.

- [ ] **Step 3: Install the debug APK and switch the app to pt-BR**

```powershell
android run --debug --apks=app/build/outputs/apk/debug/app-debug.apk --activity=com.babytracker.MainActivity
adb shell cmd locale set-app-locales com.babytracker --user 0 pt-BR
adb shell am force-stop com.babytracker
adb shell monkey -p com.babytracker 1
```

Expected: BabyTracker opens in pt-BR on the 360dp-wide display.

- [ ] **Step 4: Inspect the layout and screenshot**

```powershell
New-Item -ItemType Directory -Force build/validation
android layout -p -o build/validation/home-ptbr-small-layout.json
android screen capture -o build/validation/home-ptbr-small.png
```

Visually inspect the PNG immediately. Confirm `Amamentação`, `Histórico de mamadas`, and `Consultas médicas` are complete, contain no ellipsis, and wrap only between words.

- [ ] **Step 5: Review and commit only feature files**

```powershell
git diff --check
git status --short
git add app/src/main/java/com/babytracker/ui/home/HomeScreen.kt app/src/test/java/com/babytracker/ui/home/HomeTileTitleTest.kt docs/superpowers/plans/2026-06-30-home-tile-title-wrapping.md
git commit -m "fix(home): display complete tile titles"
```

Expected: the commit succeeds; `.air/` remains untracked.
