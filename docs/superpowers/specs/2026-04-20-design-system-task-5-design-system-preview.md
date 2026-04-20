# SPEC — Task 5: Design System Preview Composable

**Part of:** [2026-04-20-design-system-alignment-design.md](./2026-04-20-design-system-alignment-design.md)
**Status:** Ready for implementation
**Blocks:** none
**Blocked by:** Tasks 1–4 recommended (preview is most useful once all tokens exist); technically can run independently.

---

## Problem

The design system lives in two places after Tasks 1–4: the theme files (`Color.kt`, `Type.kt`, `Theme.kt`) and the handoff bundle (`design-System-handoff/`). Neither is readable as a single visual artifact inside Android Studio. A developer wanting to see the palette + typography scale has to either open the HTML previews in a browser or compose mental images from code.

A Compose `@Preview` file changes that: open Android Studio → split pane → live visual spec of the whole design system, light and dark, always in sync with the source.

## Scope

One new file. Not hooked into navigation. Not visible to end users. Three previews: raw palette, semantic color chips, typography scale.

## Files to create

- `app/src/main/java/com/babytracker/ui/theme/DesignSystemPreview.kt`

## File contents

```kotlin
package com.babytracker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Living design system spec. Open these Previews inside Android Studio
 * to see the palette + typography scale for the current theme.
 *
 * Source of truth: design-System-handoff/akachan-design-system/project/
 */

// ─── Raw palette ────────────────────────────────────────────────

@Preview(name = "Palette — Pink", showBackground = true)
@Composable
private fun PalettePinkPreview() {
    PaletteRow("Pink", listOf(
        "900" to Pink900, "700" to Pink700, "200" to Pink200, "100" to Pink100,
    ))
}

@Preview(name = "Palette — Blue", showBackground = true)
@Composable
private fun PaletteBluePreview() {
    PaletteRow("Blue", listOf(
        "900" to Blue900, "700" to Blue700, "200" to Blue200, "100" to Blue100,
    ))
}

@Preview(name = "Palette — Green", showBackground = true)
@Composable
private fun PaletteGreenPreview() {
    PaletteRow("Green", listOf(
        "900" to Green900, "700" to Green700, "200" to Green200, "100" to Green100,
    ))
}

@Composable
private fun PaletteRow(label: String, stops: List<Pair<String, Color>>) {
    Column(Modifier.padding(16.dp)) {
        Text(label, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stops.forEach { (name, color) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(color, RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(name, fontSize = 10.sp)
                }
            }
        }
    }
}

// ─── Semantic color scheme ──────────────────────────────────────

@Preview(name = "Scheme — Light", showBackground = true)
@Composable
private fun SchemeLightPreview() {
    BabyTrackerTheme(themeConfig = com.babytracker.domain.model.ThemeConfig.LIGHT) {
        SchemeSwatchGrid()
    }
}

@Preview(name = "Scheme — Dark", showBackground = true)
@Composable
private fun SchemeDarkPreview() {
    BabyTrackerTheme(themeConfig = com.babytracker.domain.model.ThemeConfig.DARK) {
        SchemeSwatchGrid()
    }
}

@Composable
private fun SchemeSwatchGrid() {
    val c = MaterialTheme.colorScheme
    val pairs = listOf(
        "primary" to (c.primary to c.onPrimary),
        "primaryContainer" to (c.primaryContainer to c.onPrimaryContainer),
        "secondary" to (c.secondary to c.onSecondary),
        "secondaryContainer" to (c.secondaryContainer to c.onSecondaryContainer),
        "tertiary" to (c.tertiary to c.onTertiary),
        "tertiaryContainer" to (c.tertiaryContainer to c.onTertiaryContainer),
        "surface" to (c.surface to c.onSurface),
        "surfaceVariant" to (c.surfaceVariant to c.onSurfaceVariant),
        "error" to (c.error to c.onError),
        "errorContainer" to (c.errorContainer to c.onErrorContainer),
    )
    Column(
        Modifier
            .background(c.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        pairs.forEach { (label, colors) ->
            val (bg, fg) = colors
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(bg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(label, color = fg, fontSize = 14.sp)
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .height(24.dp)
                    .width(120.dp)
                    .background(c.outline, RoundedCornerShape(4.dp))
            )
            Text("outline", color = c.onSurface, fontSize = 12.sp)
        }
    }
}

// ─── Typography ─────────────────────────────────────────────────

@Preview(name = "Typography scale", showBackground = true)
@Composable
private fun TypographyPreview() {
    BabyTrackerTheme {
        val t = MaterialTheme.typography
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Display Small — 36sp ExtraBold", style = t.displaySmall)
            Text("Headline Large — 32sp Bold", style = t.headlineLarge)
            Text("Title Large — 22sp SemiBold", style = t.titleLarge)
            Text("Title Medium — 18sp SemiBold", style = t.titleMedium)
            Text("Title Small — 14sp SemiBold", style = t.titleSmall)
            Text("Body Large — 16sp Regular", style = t.bodyLarge)
            Text("Body Medium — 14sp Regular", style = t.bodyMedium)
            Text("Body Small — 12sp Regular", style = t.bodySmall)
            Text("LABEL MEDIUM — 12sp Bold UPPER", style = t.labelMedium)
            Text("Label Small — 11sp Medium", style = t.labelSmall)
        }
    }
}
```

## Design decisions

- **Separate previews per palette color** rather than one mega-preview — Android Studio's preview pane is limited in width, and a single 12-chip row doesn't render cleanly.
- **Light + Dark scheme previews** rendered by forcing `ThemeConfig.LIGHT` / `ThemeConfig.DARK` via `BabyTrackerTheme(themeConfig = …)` — no reliance on emulator dark-mode setting.
- **`private` composables** so none of this leaks into app code. Previews are tooling-only.
- **No dependency on any UI module** — the file lives in `ui/theme/` alongside the tokens it visualises.
- **Typography preview uses `MaterialTheme.typography.*` not the raw `AkachanTypography` constant** — this validates that the typography is correctly wired into `MaterialTheme`, not just that the values compile.

## Acceptance criteria

- [ ] File `app/src/main/java/com/babytracker/ui/theme/DesignSystemPreview.kt` exists.
- [ ] All five `@Preview` composables render in Android Studio preview pane without errors.
- [ ] Palette chips show the expected 12 colors.
- [ ] Light scheme preview shows warm yellow background; dark scheme shows near-black background.
- [ ] Typography preview shows visually distinct sizes from 36sp display down to 11sp label.
- [ ] `./gradlew build` passes.
- [ ] The file does not appear in any app-runtime UI.

## Rollback

`git rm app/src/main/java/com/babytracker/ui/theme/DesignSystemPreview.kt`. The file has zero production consumers.

## Commit message

```
chore(ui): add design system preview composable

New ui/theme/DesignSystemPreview.kt provides @Preview renderings of the
raw palette scales, light + dark semantic color scheme, and typography
scale. Tooling-only — not hooked into navigation.
```
