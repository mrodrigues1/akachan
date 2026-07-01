# Context: Akachan (BabyTracker)

Native Android app for parents of infants (0–12 months). Tracks feeding, sleep, growth, and health data locally, with an optional read-only partner-sharing sync via Firebase Firestore.

Read this before exploring the codebase or naming new concepts. See `docs/agents/domain.md` for how skills should consume this file.

## Glossary

Terms below are canonical. Prefer them over synonyms when naming classes, issues, or tests.

| Term | Meaning |
|------|---------|
| **Session** | A timed activity with a start and optional end (`BreastfeedingSession`, `PumpingSession`). Supports pause/resume. |
| **Record** | A logged time interval that is not a live "session" concept in the UI (`SleepRecord`). |
| **Change** | A single point-in-time diaper event (`DiaperChange`). |
| **Entry** | A unifying read model across multiple logging types for history/timelines (`FeedEntry` merges bottle + breastfeeding). |
| **Snapshot** | A payload synced or exported for external consumption — partner sharing (`ShareSnapshot`) or backup/export (`BackupData`, `DoctorVisit.snapshotLabel`). Distinct from the live local record it was derived from. |
| **Baby** | The tracked child's profile: name, birth date, sex, allergies, derived age. |
| **BabyEvent** | A generic timestamped occurrence with a type + intensity, used for allergy/symptom-like tracking (`BabyEventType`). |
| **Milk bag** | A unit of stored/expressed breastmilk (volume, collection date, optional link back to the `PumpingSession` that produced it, and to the `BottleFeed` that consumed it). |
| **clientId** | A cross-device UUID assigned to sync-relevant records (`SleepRecord`, `BottleFeed`) so partner devices can reconcile the same logical entry. Distinct from the local Room `id: Long`, which never leaves the device. |
| **Author / startedBy** | Which device/person (self vs partner) created or is driving a shared record — relevant only to partner-sharing-eligible models. |
| **Op (FeedOp / SleepOp)** | A single change destined for partner sync, reconciled against the remote snapshot (`ReconcilePendingOps`). |
| **Vaccine lifecycle** | A `VaccineRecord` moves through `VaccineStatus`: to-schedule → scheduled → administered. |
| **Doctor visit** | A logged appointment (`DoctorVisit`) with prep questions (`VisitQuestion`) and derived summaries (`DoctorVisitSummary`, `VaccineSummary`) for partner sharing. |
| **Milestone** | A free-form parent-logged moment (title, date, optional photo/note) — not a clinical measurement. |
| **Growth measurement** | A clinical body metric (weight, length, head circumference) stored in canonical units (grams, mm); unit conversion (lb/oz, in) happens only at the UI edge. |

## Feature areas

Breastfeeding · Pumping & milk inventory · Bottle feeding · Sleep · Diaper · Growth measurements · Milestones · Vaccines · Doctor visits · Allergies · Partner sharing · Notifications · Backup/export · Theming & widgets.

## Where things live

- Domain models: `app/src/main/java/com/babytracker/domain/model/`
- Export-specific models: `app/src/main/java/com/babytracker/export/domain/model/`
- Partner-sharing sync models: `app/src/main/java/com/babytracker/sharing/domain/model/`
- Feature specs: `specs/SPEC-*.md`

## Naming conventions to preserve

- Don't call a timed activity with pause/resume a "Record" — reserve that suffix for simple logged intervals like sleep.
- Don't call a sync/export payload anything but "Snapshot" — it signals "derived, not the source of truth."
- Storage always uses canonical units (grams, mm, ml); never store a locale-dependent unit (lb, oz, in) in a domain model or Room entity.
- `id: Long` (local Room key) and `clientId: UUID` (cross-device identity) are different concepts — don't conflate them when adding sync support to a new model.

## Open gaps

No ADRs exist yet (`docs/adr/`). If a decision recorded here needs deeper rationale, capture it via `/domain-modeling` rather than expanding this file inline.
