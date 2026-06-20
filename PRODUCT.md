# Product

## Register

product

## Users

Parents of infants (0-12 months), typically two partners sharing tracking duties across one or both devices. Primary use context: one parent holding a baby while the other glances at shared data, or a single parent using one hand at 3am with minimal ambient light. Both partners need to read and interpret data quickly, without onboarding or explanation each time they pick up the phone. The user base spans English and Brazilian Portuguese speakers; many households are bilingual.

## Product Purpose

Akachan is a baby tracker covering the full daily care routine: breastfeeding sessions, pumping, bottle feeding, sleep records, diaper changes, an inventory of supplies, and allergy notes. Parents choose which trackers appear, so each household sees only the tools it uses. All tracking data is stored locally; an optional partner-sharing feature syncs a read-only snapshot to a partner's device via Firebase. Success is a parent who completes any tracking action in under five seconds, one-handed, half-asleep, and trusts the data is accurate.

## Brand Personality

Warm, calm, reassuring. A trusted companion, not a product. The app speaks through clear information and dependable behavior, not through decoration or noise. It celebrates the small wins of new parenthood (a long sleep stretch, a full feeding) without being saccharine or condescending.

## Anti-references

- Clinical and medical apps: cold whites, data tables, sterile layout. Akachan is a family tool, not a hospital form.
- Loud baby product brands: oversaturated cartoon palettes, bubble fonts, and primary-color noise. The parents using this app are adults exhausted by exactly that aesthetic.
- Generic fitness trackers: dark mode dashboards, concentric ring metrics, stat-heavy grids. Akachan tracks care, not performance.

## Design Principles

1. **One-hand trust** — every critical action (start, stop, switch side) is reachable with a single thumb. Two-hand gestures are never required for primary flows.
2. **Calm over clever** — no visual flourishes that compete with the information. Warmth comes from restraint, not decoration.
3. **3am readable** — typography, contrast, and layout survive low ambient light, exhaustion, and fractional attention. Dark mode is a first-class citizen.
4. **Partnership clarity** — data is shared between two people; every screen must be interpretable at a glance without prior context.
5. **Reassuring signals** — progress, completion, and alert states feel supportive. No design choice should make a sleep-deprived parent feel they've done something wrong.
6. **Only what you use** — parents toggle which trackers appear. The interface adapts to the household, never overwhelming a single-tracker user with features they ignore.
7. **Same warmth in any language** — English and Brazilian Portuguese are equal first-class experiences. Layouts absorb longer translated strings without breaking hierarchy, touch targets, or one-handed reach.

## Accessibility & Inclusion

- WCAG AA as baseline for contrast and touch targets (minimum 48dp tap targets).
- One-handed operation required for all primary flows.
- Reduced cognitive load: flat navigation, no information overload, affordances legible at a glance. Feature selection lets users hide unused trackers to cut clutter.
- Dark mode must be exceptional: not an afterthought, tuned specifically for low-light, low-attention conditions.
- Full localization in English and Brazilian Portuguese (pt-BR). No hardcoded user-facing strings; layouts must tolerate the length variance between the two languages.
