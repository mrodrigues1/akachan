# Local-first: the primary device's Room database is the single source of truth

Status: accepted (decided 2026-04/05 in SPEC-001/SPEC-005 — retired specs, originals in git history; recorded as ADR 2026-07-03)

All tracking data lives in local Room on the primary device. Partner sharing publishes a **derived, disposable snapshot** — regenerated in full from Room on every sync, carried by dedicated snapshot models (Room entities never cross the wire) — to a remote store (Firestore today) under an 8-character share code with anonymous per-device auth; there are no user accounts. The remote store is a cache, never a source of truth.

The backend is deliberately swappable behind one seam: `SharingRepository` is the only abstraction ViewModels/use cases see, `FirestoreSharingService` is the only class touching Firebase APIs, and `AntiPatternTest` blocks `com.google.firebase` imports outside `sharing/`. The 2026-06-17 Firebase→Supabase migration design is tractable precisely because of this — ephemeral snapshots mean no remote data migration is ever required.

## Consequences

- Losing the device loses history except manual backups — the export/backup feature is the mitigation, not cloud sync.
- Anything a partner device needs must be projected into the snapshot; partner writes cannot mutate shared state directly (ADR-0003).
- Adding non-anonymous auth (email/OAuth) is a new design decision, not an incremental change.
