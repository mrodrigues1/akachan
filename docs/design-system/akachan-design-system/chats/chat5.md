# Notification display options

_Started 2026-04-25 19:47 UTC_

---

## User

Expanded notifications only have the grouped version, add individual ones

## Assistant

_[tool: read_file]_

_[tool: str_replace_edit]_

I need to add individual expanded notifications — standalone expanded cards (no group header, no sub-items stacking) — as a new section. Let me add those to both columns.

_[tool: str_replace_edit]_

I need to also add the expanded body CSS and the dark theme column. Let me fix both:

_[tool: str_replace_edit]_

Still need to add the individual expanded section to the dark theme column:

_[tool: done]_

_[tool: fork_verifier_agent]_

It looks like you might be sending empty messages accidentally — perhaps pressing Enter without typing? 

The individual expanded notifications have been added to both light and dark columns. Each shows:
- The collapsed header row (icon, title, timestamp, up chevron)
- An expanded body with timer (where relevant), progress bar, and action buttons

Let me know if you'd like any adjustments!

