# ADR-002 — Durable local state and synchronization outbox

**Status:** Accepted — provisional S02 planning direction; technical validation gates outstanding
**Decision track:** S02-B
**Code baseline:** `86e5ecdd65bbc127f2da49c03c00732f9ca2b124`
**Scope:** Android local persistence and delivery guarantees. No production implementation or dependency change is included.

## Context and verified current behavior

- Android `StudentWork` and its pending changes are immutable values held by `MathWorkspaceViewModel`; there is no Room, DataStore, or other persistence dependency in the inspected Android module. S00 observed loss of unsynchronized work after process death.
- Current wire stroke payloads contain stroke ID, tool type, and point `x`, `y`, `elapsedTimeMillis`. Existing serialization tests cover API event variants, not lossless durable stroke round-trips. The wire projection is not established as sufficient to re-render strokes faithfully.
- `SyncCoordinator` retries the same immutable request with bounded attempts and has guarded revision-gap reconciliation. The backend transactionally validates and persists ordered revisions before acknowledging them. PostgreSQL integration tests skip if no migrated test database is configured.
- The current client increments a revision even for empty add/remove collections, which can create a revision without an event; this must be resolved before persisting a journal that requires contiguous complete revisions.

Evidence: [StudentWork.kt](../../../../app/src/main/java/com/example/myapplication/domain/StudentWork.kt), [MathWorkspaceViewModel.kt](../../../../app/src/main/java/com/example/myapplication/ui/workspace/MathWorkspaceViewModel.kt), [StrokePayloadMapper.kt](../../../../app/src/main/java/com/example/myapplication/analysis/transport/StrokePayloadMapper.kt), [SyncCoordinator.kt](../../../../app/src/main/java/com/example/myapplication/analysis/sync/SyncCoordinator.kt), [revision rules](../../../../backend/app/services/revision_rules.py), [revision storage](../../../../backend/app/db/revision_repository.py), [database docs](../../../DATABASE.md), and [S00 audit](../current-state-audit.md).

## Options considered

| Option | Assessment |
|---|---|
| **Room / SQLite** | Accepted provisional direction for a growing, queryable document/page model and an atomic state-plus-outbox transaction. Adds a runtime dependency and schema-migration responsibility. Verify journal/synchronous settings and actual durability assumptions for the supported Android versions/devices. |
| DataStore aggregate | Could persist a single small aggregate, but is a weaker fit for a growing event journal, queryable pages, and atomic updates across independently stored records. No dependency is present today. |
| Atomic JSON/file snapshot | Avoids a database dependency, but locking, replacement atomicity, corruption recovery, concurrent edits, and keeping state/outbox together become application responsibilities. |
| Direct SQLite | Transactional and viable if dependency constraints preclude Room, at the cost of more hand-written mapping, migration, and testing. |

The user accepted Room/SQLite as the provisional local source-of-truth architecture, with a transactional outbox and a repository boundary so Compose/ViewModels do not depend on Room entities. This is not permission to add the dependency, schema, or production implementation. S02-E/G and implementation planning must validate lifecycle, backup, security, and testing implications first.

## Proposed source-of-truth and transaction boundary

The local durable repository is the source of truth after persistence is implemented. A single database transaction should:

1. Validate the operation and current local revision.
2. Update document/page/stroke state and advance the revision only when there is a real operation.
3. Append the complete immutable ordered event set for that revision to an outbox.
4. Commit before publishing the new durable state as saved or making the events eligible for sync.

No-op add/remove operations must not advance revision. A multi-stroke gesture may append several ordered events at one revision, matching the existing backend's `(revision, position)` event model. Do not introduce page operations or transmit new fields until S02-C defines compatible API behavior.

Persist a versioned explicit local ink DTO, not opaque AndroidX Ink bytes unless their compatibility guarantees are verified for supported versions. It must retain stable stroke identity and all attributes required for faithful rendering/input reconstruction; coordinate units, brush attributes, and transforms are S02-C/D dependencies. Keep local serialization separate from the versioned wire DTO. Store/reconstruct exact immutable wire event payloads for retry identity; do not regenerate an acknowledged or pending event from mutable current state.

## Outbox and acknowledgment proposal

- Only committed outbox entries may be sent. Delivery is at-least-once: a server commit followed by a lost response must replay identical revision/event content.
- Retain pending events on network failure, cancellation, process death, revision gap, or conflict. Never silently reset or overwrite local history.
- Apply a successful acknowledgment transactionally to the exact request snapshot. Mark/remove only entries covered by that accepted snapshot, preserving later local edits; do not infer acceptance of unrelated newer local changes.
- Keep server sync status separate from local durable-save status and mathematical analysis.

## Crash/failure matrix

| Failure point | Required result |
|---|---|
| Before local transaction commits | Prior durable document and outbox remain intact; operation is not reported as saved. |
| After local commit, before send | Edit and complete outbox survive process death and are eligible for later retry. |
| Request not committed by server | Local outbox remains; retry sends the same event payloads. |
| Server commits, response is lost | Replay identical payload; backend retry validation accepts only matching history; retain local events until confirmed. |
| Response received, local acknowledgment transaction fails | Keep outbox and safely replay; never drop based only on an in-memory response. |
| Acknowledgment transaction commits | Remove/mark only events through the acknowledged request snapshot; newer events remain pending. |
| Local data is corrupt/unreadable | Preserve/quarantine recoverable data, expose an explicit recovery state, and never replace it with an empty document silently. |
| Backup restores an older local snapshot | Reconcile through normal revision/history checks; do not blindly replay or overwrite newer server history. |

## Migration and rollout

There is no existing durable Android database or durable local snapshot to migrate. Sessions already lost on process death cannot be reconstructed from the client. A migration cannot guarantee recovery of the old in-memory `StudentWork` across an app update/process kill. If a live in-memory import path is used during a transition, it must be best-effort, idempotent, and complete before switching that live session to repository-backed state; do not represent it as a guaranteed upgrade migration. Define and communicate the boundary for users with pending in-memory changes before rollout.

Test every released local schema migration. Decide whether data/outbox participates in OS backup/device transfer: a restored outbox can be stale and must pass normal server reconciliation. Define corruption recovery and whether sync is foreground-only or uses a durable background scheduler before claiming background delivery.

## Measurable acceptance gates

- Every locally committed edit survives process kill/reopen; no unacknowledged event is dropped.
- State, revision, and outbox either commit together or remain unchanged after injected transaction failure.
- Retries preserve byte/semantic identity and create neither missing nor conflicting revisions.
- An acknowledgment cannot remove changes beyond its request snapshot, including during concurrent edits.
- Import, if supported for a still-live session, is idempotent and preserves the existing session ID/revision/events.
- Test crash boundaries, corruption, stale backup restore, concurrency, exact retry identity, and every schema migration. Run Android repository tests and Android–FastAPI–PostgreSQL integration against a migrated disposable database before implementation exit.

## Remaining validation gates and consequences

- Product/engineering: validate Room/SQLite technical fit and schema migration before implementation. Use foreground synchronization for MVP; background scheduling is deferred until demonstrated need.
- Product/privacy: define device/cloud backup and restore policy.
- Architecture: define canonical local ink attributes/serialization version and coordinate contract with S02-D.
- Release: decide how to communicate old in-memory pending-work loss and whether any live transition checkpoint is feasible.
- Verify atomic document/outbox updates, process-death recovery, migration and backup behavior, exact retry identity, and server acknowledgment semantics before implementation. This ADR does not prove local durability or backend E2E; S00 remains partial.
