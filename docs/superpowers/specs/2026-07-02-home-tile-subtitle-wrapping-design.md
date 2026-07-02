# Home Tile Subtitle Wrapping

## Goal

Display complete subtitles in home-screen tiles without ellipses. Tile titles, badges, provider names, countdowns, and other labels remain unchanged.

## Design

- Remove line limits and ellipsis overflow from subtitle composables used by home tiles.
- Keep the existing staggered grid and `heightIn(min = ...)` sizing. Tiles grow vertically when a subtitle wraps.
- Do not introduce fixed heights, new layout abstractions, or typography changes.

## Verification

- Add focused Compose coverage proving a narrow feeding-prediction subtitle has no visual overflow.
- Run the related home UI test class, then the full test and build checks before completion.
