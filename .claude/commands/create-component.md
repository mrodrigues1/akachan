---
description: "Guided Jetpack Compose component creation with Akachan design system patterns"
argument-hint: "[component-name]"
---

# Create Component

Guided workflow for creating new Jetpack Compose UI components following the Akachan design system and project conventions.

## Pre-flight Checks

1. Confirm the project root is `/home/user/akachan` and the source tree is `app/src/main/java/com/babytracker/`.
2. Check `ui/component/` for existing components that could be extended:
   - `TimerDisplay` — elapsed time display
   - `HistoryCard` — session/sleep history card
   - `SideSelector` — breast-side selector
3. Load the project's design system context from `ui/theme/` (Color.kt, Shape.kt, Type.kt, Theme.kt).

## Component Specification

**CRITICAL RULES:**
- Ask ONE question per turn
- Wait for user response before proceeding
- Build a complete specification before generating code

### Q1: Component Name (if not provided)

```
What should this component be called?

Guidelines:
- Use PascalCase (e.g., SleepTypeChip, SessionSummaryCard)
- Screen-level composables end in *Screen; reusable ones don't
- Be descriptive but concise

Enter component name:
```

### Q2: Component Category

```
Where does this component belong?

1. Reusable UI component — goes in ui/component/, shared across features
2. Screen-level composable — goes in the feature package (e.g., ui/breastfeeding/)
3. Sub-composable inside a screen — private fun in the screen file

Enter number:
```

### Q3: State Pattern

```
How should this component handle state?

1. Stateless — receives all data via parameters and reports events via callbacks
   (preferred; state lives in the ViewModel)
2. Locally stateful — owns simple transient state (e.g., expanded/collapsed)
   using remember / rememberSaveable
3. State-hoisted — manages its own state but exposes it to the parent
   (use only when the parent must observe or control the state)

Enter number:
```

### Q4: Design System Tokens

```
Which palette family does this component belong to?

1. Pink — Feeding/Breastfeeding feature (primary)
2. Blue — Sleep feature (secondary)
3. Green — Success/Tertiary states
4. Amber — Warning states (use top-level WarningAmber tokens, NOT MaterialTheme.colorScheme)
5. Neutral — Surface/background colors from MaterialTheme.colorScheme

Enter number:
```

### Q5: Test Location

```
What testing is needed?

1. No tests (pure presentation, covered by @Preview)
2. Unit test in app/src/test/ (for logic in the component or its ViewModel)
3. UI test in app/src/androidTest/ using createComposeRule()
4. Both unit and UI tests

Enter number:
```

## Component Generation

### 1. Determine File Location

| Category | Location |
|----------|----------|
| Reusable | `app/src/main/java/com/babytracker/ui/component/{ComponentName}.kt` |
| Screen-level | `app/src/main/java/com/babytracker/ui/{feature}/{ComponentName}.kt` |
| Sub-composable | Private `fun` inside the relevant `*Screen.kt` file |

### 2. Generate Component Code

```kotlin
// {ComponentName}.kt
package com.babytracker.ui.component  // or appropriate package

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.babytracker.ui.theme.AkachanTheme

@Composable
fun {ComponentName}(
    // required parameters first
    param1: Type1,
    // optional parameters with defaults
    param2: Type2 = defaultValue,
    // modifier always second-to-last
    modifier: Modifier = Modifier,
    // content lambda last (if applicable)
    // content: @Composable () -> Unit = {},
) {
    // implementation using MaterialTheme.colorScheme, AkachanTypography, AkachanShapes
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun {ComponentName}Preview() {
    AkachanTheme {
        {ComponentName}(
            // preview parameters
        )
    }
}
```

**Key rules:**
- `modifier: Modifier = Modifier` is always the last parameter before any content lambda
- Use `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `MaterialTheme.shapes.*`
- For warning colors: import `WarningAmber`, `WarningContainerAmber`, `OnWarningContainerAmber` directly from `com.babytracker.ui.theme` — never via MaterialTheme
- Avoid hardcoded colors or dimensions; use design system tokens

### 3. Generate Unit Test (if requested)

```kotlin
// app/src/test/java/com/babytracker/ui/component/{ComponentName}Test.kt
package com.babytracker.ui.component

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.test.runTest

class {ComponentName}Test {

    @BeforeEach
    fun setup() {
        // initialize dependencies
    }

    @Test
    fun `{description of behavior}`() = runTest {
        // arrange
        // act
        // assert
    }
}
```

### 4. Generate UI Test (if requested)

```kotlin
// app/src/androidTest/java/com/babytracker/ui/component/{ComponentName}Test.kt
package com.babytracker.ui.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class {ComponentName}Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders correctly with valid input`() {
        composeTestRule.setContent {
            AkachanTheme {
                {ComponentName}(/* test params */)
            }
        }
        composeTestRule.onNodeWithText("expected text").assertIsDisplayed()
    }
}
```

## User Review

After generating files:

```
I've created the {ComponentName} component:

Files created:
- {path}/{ComponentName}.kt
- {test path (if applicable)}

Next steps:
1. Run @Preview in Android Studio to verify the visual design
2. If this is a reusable component, add it to ui/theme/DesignSystemPreviewScreen.kt
3. Run ./gradlew ktlintFormat && ./gradlew detekt before committing

Would you like to:
1. Add more variants or states
2. Adjust the design system token usage
3. Add more @Preview scenarios
4. Done
```

## Error Handling

- If component name conflicts with an existing file: show the existing file path and ask whether to extend or create a new variant
- If the user requests Storybook, TypeScript, or CSS: clarify this is an Android Kotlin/Compose project and redirect
- If the requested token doesn't exist in the design system: suggest the nearest existing token
