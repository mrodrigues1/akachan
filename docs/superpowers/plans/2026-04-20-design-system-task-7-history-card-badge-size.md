# Design System Task 7: HistoryCard Badge Size Alignment — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the `HistoryCard` emoji badge size with the design-system handoff spec — growing it from 36dp to 44dp and bumping the emoji font size from 16sp to 20sp.

**Architecture:** Single-file, single-component internal change. No public API changes — all call sites (`BreastfeedingScreen`, `BreastfeedingHistoryScreen`, `SleepTrackingScreen`, `SleepHistoryScreen`) pass the same parameters as before.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3

---

### Task 1: Apply badge size fix in `HistoryCard.kt`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt:46-55`

- [ ] **Step 1: Add the `sp` import**

In `HistoryCard.kt`, add after the existing `import androidx.compose.ui.unit.dp` line:

```kotlin
import androidx.compose.ui.unit.sp
```

- [ ] **Step 2: Update the badge Box**

Replace lines 46–56 (the badge `Box`):

```kotlin
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
```

with:

```kotlin
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = badgeColor,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = badgeEmoji, fontSize = 20.sp)
            }
```

The complete resulting file should be:

```kotlin
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
import androidx.compose.ui.unit.sp

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
                    .size(44.dp)
                    .background(
                        color = badgeColor,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = badgeEmoji, fontSize = 20.sp)
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

- [ ] **Step 3: Build to confirm no compilation errors**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`

If it fails with `Unresolved reference: sp` → verify the `import androidx.compose.ui.unit.sp` line was added.

- [ ] **Step 4: Run unit tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` — no test changes needed; this is a pure internal layout tweak with no business logic.

- [ ] **Step 5: Manual spot-check in emulator**

Launch a debug build and visit:
1. **Breastfeeding History screen** — badges should be visibly larger (~44dp square), emoji centered and readable at 20sp.
2. **Sleep History screen** — same badge appearance.
3. **Active Breastfeeding screen** (last-feeding summary cards) — same.
4. **Home screen** — no `HistoryCard` used here; appearance unchanged.

If the list feels too loose after the size increase, the spec calls out revisiting `vertical = 12.dp` padding down to `10.dp` as a follow-up — that is out of scope for this task.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/component/HistoryCard.kt
git commit -m "feat(ui): align HistoryCard badge size with design system (36dp → 44dp)

Matches the 44x44 badge specified in design-System-handoff's Android UI
kit (Screens.jsx line 135). Emoji font size bumped to 20sp to match.
Affects breastfeeding history, sleep history, and active sleep/feeding
session lists — no call-site changes."
```

---

## Acceptance Checklist

- [ ] `HistoryCard.kt` badge `Box` uses `.size(44.dp)`
- [ ] Emoji `Text` uses `fontSize = 20.sp` (literal, not a typography role)
- [ ] `import androidx.compose.ui.unit.sp` present in `HistoryCard.kt`
- [ ] `./gradlew build` passes
- [ ] `./gradlew test` passes
- [ ] Manual spot-check: breastfeeding history badges visibly larger and centered
- [ ] Manual spot-check: sleep history badges visibly larger and centered
- [ ] No call-site regressions — all four screens still compile and render correctly
