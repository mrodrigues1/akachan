# Raster Icon Redesign Guide

Use this guide when redesigning app section icons for Akachan. It captures the decisions and workflow from the breastfeeding icon replacement session.

## Goal

Create real raster PNG icons, not vectors or emoji placeholders, that work across:

- Compose UI cards and badges
- History rows
- Feature picker rows
- Partner dashboard rows
- Glance widgets and widget previews
- Custom notification RemoteViews

The icon should feel related to the app launcher icon: soft, rounded, warm, polished, and calm. It should still be readable at small sizes.

## Visual Direction

Do:

- Use a soft rounded app-icon illustration style.
- Keep shapes simple enough to read at `18dp`, `24dp`, `34dp`, and `40dp`.
- Prefer compact silhouettes with strong negative space.
- Use matte or satin surfaces rather than shiny plastic.
- Make the icon more opaque and solid than glossy.
- Include enough light/cream area inside the mark so it still pops on colored cards.
- Match Akachan's warm, calm product tone.
- Preserve semantic domain color logic from `DESIGN.md`.

Do not:

- Use emoji as the final icon.
- Use vector-only output when the request is for a real image.
- Make the icon rely on the same hue as its background.
- Use hot pink as the whole visible mark when it sits on a pink/red background.
- Use tiny facial details that disappear at small sizes.
- Use medical, explicit, or clinical imagery.
- Add text, letters, watermarks, medical crosses, or unrelated props.
- Use hard black outlines or flat vector line-art if the rest of the icon system is soft raster.

## Palette Lessons

For feeding/breastfeeding icons, the UI background is often pink:

- `Carnation Pink`: `#C2185B`
- `Carnation Pink Container`: `#F8BBD0`
- `Deep Carnation`: `#880E4F`
- `Warm Cream`: `#FFFDE7`

The first generated breastfeeding icon failed partly because it was mostly red/pink on a pink card. The improved version used contrast inside the same warm family:

- Warm cream / ivory: `#FFF3DA`
- Deep plum / berry: `#5A1242`
- Muted cranberry: `#9B2D5B`
- Dusty mauve: `#C97893`

This preserved the feeding-domain feel while improving contrast on pink backgrounds.

## Prompt Pattern

Use the existing icon or launcher icon as a style reference when possible.

Example prompt for a revised section icon:

```text
Use case: style-transfer
Asset type: Android app section icon, raster PNG source for density export
Primary request: Revise the current <domain> icon into a clearer, more readable small-size icon for Akachan.
Input images: The current icon is the structure reference. Screenshots show where it appears in the app.
Subject: Keep the same tasteful abstract concept, but simplify the silhouette so it is readable at 18dp-48dp.
Style/medium: Soft rounded app-icon illustration matching the BabyTracker launcher style, but with a matte satin finish rather than glossy plastic. Fewer specular highlights, more opaque solid forms, cleaner edges, stronger silhouette.
Composition/framing: Centered square composition, generous padding, transparent-output source via chroma key. The icon should look good at 18dp, 24dp, 34dp, and 40dp.
Color palette: Choose colors that contrast with the destination card background. Use cream negative space where useful. Avoid relying on the same dominant hue as the background.
Materials/textures: Opaque matte satin shapes, soft ambient occlusion, subtle depth, no mirror shine, no wet/glass/plastic gloss.
Scene/backdrop: Perfectly flat solid #00ff00 chroma-key background for background removal.
Constraints: Preserve the domain concept and compact centered shape. Background must be one uniform #00ff00 color with no shadows, gradients, texture, reflections, floor plane, or lighting variation. Keep subject fully separated from the background with crisp antialiased edges. No text, no letters, no watermark, no emoji.
Avoid: Low contrast against destination background, tiny details, overly glossy highlights, translucent glass look, vector-flat line icon look, busy detail.
```

## Transparent PNG Workflow

Use built-in image generation first.

1. Generate the icon on a flat `#00ff00` chroma-key background.
2. Remove the chroma key locally with the imagegen helper.
3. Validate transparency and edge cleanup.
4. Trim the source to visible bounds and add modest padding.
5. Export density-specific PNGs.

Example commands:

```powershell
$py = 'C:\Users\mathe\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$source = '<generated-image-path>.png'
$outDir = 'C:\Users\mathe\StudioProjects\akachan\tmp\imagegen'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$transparent = Join-Path $outDir 'icon_transparent.png'
& $py "$env:USERPROFILE\.codex\skills\.system\imagegen\scripts\remove_chroma_key.py" `
  --input $source `
  --out $transparent `
  --auto-key border `
  --soft-matte `
  --transparent-threshold 12 `
  --opaque-threshold 220 `
  --despill `
  --edge-contract 1 `
  --force
```

Export densities from one trimmed source:

```powershell
$py = 'C:\Users\mathe\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
@'
from pathlib import Path
from PIL import Image

root = Path(r'C:\Users\mathe\StudioProjects\akachan')
source = root / 'tmp' / 'imagegen' / 'icon_transparent.png'
outputs = {
    'drawable-mdpi': 48,
    'drawable-hdpi': 72,
    'drawable-xhdpi': 96,
    'drawable-xxhdpi': 144,
    'drawable-xxxhdpi': 192,
}

img = Image.open(source).convert('RGBA')
alpha = img.getchannel('A')
bbox = alpha.getbbox()
if bbox is None:
    raise SystemExit('transparent source has no visible pixels')

cropped = img.crop(bbox)
max_side = max(cropped.size)
pad = round(max_side * 0.08)
square_side = max_side + pad * 2
square = Image.new('RGBA', (square_side, square_side), (0, 0, 0, 0))
square.alpha_composite(cropped, ((square_side - cropped.width) // 2, (square_side - cropped.height) // 2))

for folder, size in outputs.items():
    out_dir = root / 'app' / 'src' / 'main' / 'res' / folder
    out_dir.mkdir(parents=True, exist_ok=True)
    resized = square.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(out_dir / '<icon_name>.png', optimize=True)
'@ | & $py -
```

## Small-Size Preview

Before replacing resources, preview the icon over actual destination colors.

For breastfeeding, test at least:

- `#F8BBD0` primary container
- `#C2185B` primary
- `#FFFDE7` warm cream

Preview sizes:

- `18dp` chip/notification leading icon
- `24dp` feature row
- `34dp` history badge
- `40dp` home tile
- `64dp` empty state

Do not judge only the full-size PNG. The icon can look good large and fail in a history badge.

## Android Resource Rules

Use density-specific bitmap resources:

```text
app/src/main/res/drawable-mdpi/<icon_name>.png
app/src/main/res/drawable-hdpi/<icon_name>.png
app/src/main/res/drawable-xhdpi/<icon_name>.png
app/src/main/res/drawable-xxhdpi/<icon_name>.png
app/src/main/res/drawable-xxxhdpi/<icon_name>.png
```

Use a single resource name across densities, for example:

```text
ic_breastfeeding_section.png
```

Keep the generated original under `.codex/generated_images/`. Do not depend on that path from the app. The app must reference only files under `app/src/main/res/`.

## Compose Integration Pattern

Create a small reusable composable for each icon:

```kotlin
@Composable
fun BreastfeedingIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_breastfeeding_section),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}
```

Use it instead of repeated `painterResource` calls when the icon appears across multiple UI surfaces.

Good sizes from the breastfeeding pass:

- Home tile: `40.dp`
- History badge: `34.dp`
- Feature picker row: `24.dp`
- FilterChip leading icon: `18.dp`
- Empty state: `64.dp`
- Partner active card: `42.dp`

## HistoryCard Pattern

`HistoryCard` originally only accepted `badgeEmoji`. To support raster icons without changing every caller, add an optional slot:

```kotlin
badgeContent: (@Composable () -> Unit)? = null
```

Render `badgeContent` when present, otherwise render the existing emoji fallback.

Keep Compose parameter ordering clean:

```kotlin
fun HistoryCard(
    title: String,
    subtitle: String,
    trailing: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
    badgeEmoji: String = "",
    ...
)
```

Do not place a defaulted parameter before a required parameter; detekt Compose rules will fail.

## Widget Pattern

Glance cannot use the normal Compose `Image`. Add an optional drawable resource to widget content models, then render either an `ImageProvider` or fallback emoji.

Pattern:

```kotlin
private data class DomainBlockContent(
    val emoji: String,
    val iconRes: Int? = null,
    ...
)
```

Then:

```kotlin
if (iconRes != null) {
    Image(provider = ImageProvider(iconRes), contentDescription = null)
} else {
    Text(text = emoji)
}
```

Also update static widget preview XML to use an `ImageView` with the same drawable.

## Notification Pattern

Do not restore emoji prefixes in notifications. Use an actual `ImageView` in custom RemoteViews layouts.

Use this for rich custom notification layouts:

```xml
<ImageView
    android:id="@+id/notification_title_icon"
    android:layout_width="18dp"
    android:layout_height="18dp"
    android:layout_marginEnd="4dp"
    android:contentDescription="@null"
    android:src="@drawable/<icon_name>" />
```

For expanded notifications, `24dp` works better:

```xml
android:layout_width="24dp"
android:layout_height="24dp"
android:layout_marginEnd="8dp"
```

Set it in `RemoteViews`:

```kotlin
setImageViewResource(R.id.notification_title_icon, R.drawable.<icon_name>)
```

If a helper is shared by layouts that do not all contain the icon view, make the icon optional:

```kotlin
titleIconRes: Int? = null
titleIconRes?.let { setImageViewResource(R.id.notification_title_icon, it) }
```

Do not call `setImageViewResource` against layouts that do not contain `notification_title_icon`.

## Tests To Update

If layout-contract tests asserted the old emoji or prefix TextView, update them to assert the image slot:

```kotlin
assertTrue(file.contains("notification_title_icon"))
assertTrue(file.contains("@drawable/<icon_name>"))
```

For notification changes, run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.babytracker.util.NotificationHelperTest"
```

## Validation Commands

For icon/resource-only changes:

```powershell
.\gradlew.bat assembleDebug
git diff --check
```

For notification layout changes:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.babytracker.util.NotificationHelperTest"
.\gradlew.bat assembleDebug
git diff --check
```

For broad production/resource changes before commit:

```powershell
.\gradlew.bat build
```

Known caveat from this session: full `build` may fail on unrelated sleep prediction tests with `NoClassDefFoundError: com/babytracker/domain/sleep/eval/ParametricSweepHarnessKt`. Do not patch unrelated sleep code during icon work unless the user asks.

## Checklist For Future Sessions

1. Read `docs/AI_REPO_MAP.md`.
2. Inspect destination screens and current icon usage with `rg`.
3. Inspect the launcher icon and current icon visually.
4. Pick the actual destination backgrounds before choosing a palette.
5. Generate on chroma key.
6. Remove chroma key and validate alpha.
7. Preview at real small sizes on real backgrounds.
8. Trim source, add modest padding, export densities.
9. Replace emoji/vector usage only where the domain icon is intended.
10. Leave bottle-feed or mixed-domain emoji alone unless they are explicitly part of the redesign.
11. Use a reusable Compose icon wrapper.
12. Use `ImageView`/`RemoteViews` for notifications, not emoji prefixes.
13. Update layout-contract tests.
14. Run targeted tests and `assembleDebug`.
15. Clean `tmp/imagegen/` before finishing.
