---
description: "Extend or audit the Akachan design system (Color.kt, Shape.kt, Type.kt)"
argument-hint: "[audit|add-color|add-shape]"
---

# Design System Setup

The Akachan design system already exists in `ui/theme/`. This command helps you audit it, extend it, or regenerate its documentation. It does **not** initialize a new system from scratch.

## Action Selection

```
What would you like to do?

1. audit    — Review Color.kt, Shape.kt, Type.kt for consistency and document gaps
2. add-color — Add a new color constant to the palette and wire it into Theme.kt
3. add-shape — Add a new corner radius variant to Shape.kt

Enter number or keyword:
```

---

## Action 1: Audit

Read `ui/theme/Color.kt`, `ui/theme/Shape.kt`, `ui/theme/Type.kt`, and `ui/theme/Theme.kt`.

Check for:
- **Color**: every primitive constant is used in at least one semantic token; no hardcoded hex values exist elsewhere in the codebase (grep for `Color(0x` or `Color.` in non-theme files)
- **Warning tokens**: `WarningAmber`, `WarningContainerAmber`, `OnWarningContainerAmber` (and dark variants) are top-level `val`s in `Color.kt` — verify they are NOT added to `lightColorScheme` / `darkColorScheme`
- **Shape**: all 5 slots (`extraSmall` through `extraLarge`) are defined in `AkachanShapes`
- **Typography**: all 13 M3 slots are defined in `AkachanTypography`
- **CLAUDE.md sync**: compare the Theme & UI Tokens section in `CLAUDE.md` against the actual source — report any discrepancies

Output a short report listing findings and any recommended fixes.

---

## Action 2: Add Color

### Q1: Color family

```
Which palette family does this color belong to?

1. Pink   (Feeding/Primary)
2. Blue   (Sleep/Secondary)
3. Green  (Success/Tertiary)
4. Amber  (Warning)
5. New family

Enter number:
```

### Q2: Stop value

```
Which scale stop?

100 (softest tone) | 200 (container/background) | 700 (primary action) | 900 (on-container text)

Or enter a custom stop name (e.g., 800):
```

### Q3: Hex value

```
Enter the hex color value (e.g., #FF5252):
```

### Output

Generate a Kotlin snippet to add to `ui/theme/Color.kt`:

```kotlin
// Add to the appropriate family block in Color.kt
val {Family}{Stop} = Color(0xFF{RRGGBB})
```

If the color should be wired as a semantic token in `lightColorScheme` or `darkColorScheme` in `Theme.kt`, also generate that snippet.

If it is a warning-family color, remind: "Add as a top-level `val` in Color.kt only — do NOT add to `lightColorScheme` or `darkColorScheme`."

After generating, remind the developer to:
1. Update the palette table in `CLAUDE.md`'s "Theme & UI Tokens" section
2. Add a swatch to `DesignSystemPreviewScreen.kt` if the color is user-visible

---

## Action 3: Add Shape

### Q1: Slot name and radius

```
What slot name and corner radius?

Existing slots: extraSmall (4dp), small (8dp), medium (16dp), large (24dp), extraLarge (50dp)

Enter slot name and radius (e.g., "extraExtraLarge 64dp"), or type "new" for a custom name:
```

### Output

Generate a Kotlin snippet for `ui/theme/Shape.kt`:

```kotlin
val AkachanShapes = Shapes(
    // existing slots ...
    // new slot:
    {slotName} = RoundedCornerShape({radius}.dp),
)
```

After generating, remind the developer to:
1. Update the Shapes table in `CLAUDE.md`'s "Theme & UI Tokens" section
2. Add the slot to `DesignSystemPreviewScreen.kt`

---

## Notes

- All design tokens are Kotlin `val` constants — there is no JSON, CSS, or TypeScript token format in this project.
- The live component catalog is `ui/theme/DesignSystemPreviewScreen.kt`, accessible from Settings → Developer in debug builds.
- After any change, run `./gradlew ktlintFormat && ./gradlew detekt` before committing.
