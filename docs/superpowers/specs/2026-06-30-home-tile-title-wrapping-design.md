# Home Tile Title Wrapping Design

## Goal

Display every home tile title in full on small screens and in locales with longer labels, especially pt-BR, without splitting a word across lines.

## Design

Change the shared `HomeTrackerTile` title layout only. Titles may wrap at word boundaries onto a second line. The title starts with the existing width-adapted text style; if the measured layout splits a word or still overflows two lines, reduce the font size incrementally to a 14sp floor. Remove title ellipsis. Tile height continues to follow its content in the staggered grid.

No locale-specific strings, per-tile overrides, new dependencies, or grid changes are needed.

## Validation

Add a Compose instrumentation regression test using a narrow tile and pt-BR-length labels. Assert that the complete title fits within two lines and every automatic line break occurs at whitespace. Run the targeted home screen tests, build the app, then validate the pt-BR home screen on a small-screen emulator using layout inspection and screenshots.

