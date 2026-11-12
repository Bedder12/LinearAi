# ADR-006 — Local save, synchronization, and AI-analysis lifecycles

**Status:** Proposed contracts; implementation and API review required
**Decision track:** S02-F
**Code baseline:** `86e5ecdd65bbc127f2da49c03c00732f9ca2b124`
**Reviewed HEAD:** `35e006a81fb6ffc83f0cf95d5d19a7a94bcca4c0`

## Context and verified current behavior

- `StudentWork` and pending changes exist in ViewModel memory only. There is no durable-save state. S00 observed loss after process death.
- `MathWorkspaceViewModel.check()` prevents overlapping requests, snapshots current work, and invokes `SyncCoordinator`; on success it removes changes through the accepted snapshot while preserving later edits.
- `SyncCoordinator` retries identical payloads up to three total attempts with bounded delays and performs guarded revision-gap reconciliation. Cancellation returns UI state to idle but cannot establish whether an in-flight server transaction committed.
- The `/analysis/check` route calls revision synchronization and returns an accepted revision. It does not invoke mathematical analysis. The separate current analysis service/result is also only an accepted-revision/status stub.
- Existing tests cover sync snapshot races, bounded retry, gap/conflict behavior, and backend persisted replay when PostgreSQL is configured. They do not prove local durability, process-death recovery, analysis-result behavior, or cancellation certainty.

Evidence: [StudentWork.kt](../../../../app/src/main/java/com/example/myapplication/domain/StudentWork.kt), [MathWorkspaceViewModel.kt](../../../../app/src/main/java/com/example/myapplication/ui/workspace/MathWorkspaceViewModel.kt), [SyncCoordinator.kt](../../../../app/src/main/java/com/example/myapplication/analysis/sync/SyncCoordinator.kt), [sync state](../../../../app/src/main/java/com/example/myapplication/ui/workspace/SyncState.kt), [analysis route](../../../../backend/app/api/analysis.py), [analysis service](../../../../backend/app/services/analysis_service.py), [analysis result](../../../../backend/app/domain/analysis_result.py), and [S00 audit](../current-state-audit.md).

## Proposed state ownership and lifecycle

Keep three contracts distinct:

### Local save

The Room-backed document repository owns local content/revision/outbox durability. An edit is first `Saving`; only after its state+outbox transaction commits may the UI say `Saved on this device`. A failed local transaction retains the prior durable state, exposes an actionable error, and must not publish the new revision as saved. Before Room exists, call work “in this app session,” not saved locally.

### Synchronization

The local outbox is authoritative for pending delivery. A minimal state model is:

```text
No pending changes / Last known accepted revision
Pending → Uploading → Acknowledged
                 ├→ Retryable / Offline
                 ├→ Outcome uncertain (server may have committed)
                 └→ Conflict / Needs explicit recovery
```

Persist pending entries before upload. Retry the identical committed batches. A lost response, timeout, cancellation, or process death during a request does not imply rollback; preserve the outbox and resolve by exact replay/history reconciliation. Apply acknowledgment transactionally only through the request snapshot; keep later edits. Stop automatic writes on conflict, invalid history, or unresolved gaps. Do not silently overwrite, reset, or use last-write-wins. Server acknowledgment means accepted ink history only, never local durability, backup, or math correctness.

“No pending changes” is not equivalent to a fresh server acknowledgment. Present the last known accepted revision separately from local save status. Foreground sync is the MVP direction; no background-work guarantee is implied.

### AI analysis

Analysis is a separate API/service and state machine, never the existing ink-sync route. An immutable request should identify `documentId`, `pageId` or declared document scope, target document revision, stable request ID, and content/snapshot identity after S02-C settles canonical payloads. A result records request ID, target identity/revision, analysis schema/model version, and a typed outcome.

Candidate states: `NotRequested`, `Queued`, `Running`, `Completed`, `Unavailable`, `Unsupported`, `Uncertain`, `FailedRetryable`, `Cancelled`, and `Stale`. Edits affecting the declared scope make prior results stale but do not delete them. Uncertainty/unsupported input abstains rather than asserting correctness. Canceling the client wait does not claim that remote computation stopped; request-status lookup or idempotent retry is needed to resolve a lost response. Hint/explanation/solution progression remains explicit and revision-bound per S01.

## Ownership boundaries

- Compose/ViewModel: display states and dispatch user intent; no direct network or database construction.
- Local document repository: committed content, revision, and outbox transaction.
- Sync coordinator/repository: delivery, exact retries, gap/conflict handling, and local acknowledgment.
- Backend revision service: authorization gate plus atomic event acceptance.
- Separate analysis repository/service: analysis request lifecycle and immutable results.

This depends on S02-C for stable identity, event/revision and snapshot semantics; S02-E for Android state ownership; and S02-G for authorization and non-destructive recovery. These contracts remain proposed.

## Alternatives and risks

- Continue using `/analysis/check` for both sync and math: rejected because its current behavior is ink-history persistence only and the name already risks implying analysis.
- Collapse save/sync into one “Saved” state: rejected because a local commit and remote acknowledgment are different facts.
- Treat cancelled/timeout sync or analysis requests as failed/uncommitted: unsafe because the server may have committed/completed before the response was lost.
- Automatically resolve conflicts with last-write-wins: rejected because it can destroy offline work.

Risks include false “saved/synced/correct” claims, loss after a missing response, stale feedback presented as current, unbounded retry storms, and treating ViewModel state as the source of truth.

## Acceptance tests and gates

- Inject failures before/after Room commit, upload, server commit, response, and local acknowledgment; verify status and preserved state/outbox.
- Process-kill/reopen after local commit and during sync; exact retry succeeds without duplicate or missing revisions.
- Edit during upload; acknowledgment removes only the sent snapshot and preserves new pending events.
- Cancel/timeout/lost acknowledgment produces uncertain or retryable status, never false rollback; safe replay/history lookup resolves it.
- Conflict/gap/invalid event stops automatic writes and preserves local history for explicit recovery.
- No-pending UI reports last known accepted revision, not a new server acknowledgment.
- Analysis tests bind request/result to a stable document/page/revision and schema version; verify stale, unsupported, uncertain, cancellation, idempotency, and lost-response handling.
- End-to-end tests require a migrated PostgreSQL database and authenticated API when ownership is enabled. S00's skipped PostgreSQL tests are not a pass.

This ADR does not implement Room, sync, authentication, or mathematical verification.
