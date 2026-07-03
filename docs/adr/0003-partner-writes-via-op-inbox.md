# Partner writes go through an op-inbox applied by the primary

Status: accepted (approved 2026-06-12 as SPEC-007 — retired spec, original in git history; since reused for sleep ops — `sleepOps` in `firestore.rules`)

Because the primary's Room database is the sole source of truth (ADR-0002), the partner device never mutates shared state directly. It writes **operation documents** (create/update/delete, targeting entries by `clientId`) to a per-share inbox (`shares/{code}/feedOps`, `sleepOps`); the primary applies ops into Room, re-publishes the snapshot, then deletes the applied ops. Conflicts are last-write-wins by apply order on the primary. Ops are idempotent, so a crash between snapshot push and op delete re-applies harmlessly.

Two enforcement details are deliberate:

- **Ownership is enforced at apply time on the primary** — remote security rules can verify who wrote an op but cannot see Room state, so they cannot know whether an op targets an OWNER entry. Rules and apply-time checks are complementary layers; both are blocking requirements.
- **Attribution is an `author` enum (OWNER/PARTNER), not the auth uid** — anonymous uids change on reinstall, which would strand the partner's old entries as uneditable.

## Considered options

- **Shared per-entry collection** (both devices read/write `shares/{code}/feeds/{id}`) — rejected: requires a two-way Room↔remote mirror on the primary (echo suppression, dedupe) and leaves two parallel sync systems running.
- **Partner writes the snapshot document directly** — rejected: the primary's full-snapshot push races the partner's merge; whole-document last-write-wins guarantees lost updates.

## Consequences

- Every new partner write path reuses this inbox pattern (sleep ops already did) instead of inventing a sync mechanism.
- Partner UX is optimistic: history = snapshot merged with the partner's own pending ops.
- The pattern is backend-agnostic — the Supabase migration design maps it onto a Room outbox + Realtime without changing its shape.
- The rules gating the inbox are version-controlled (`firestore.rules`, emulator tests under `firebase/`) and deployed manually (`firebase deploy --only firestore:rules`) as a required release step — console-only rules became unacceptable once a partner write path existed.
