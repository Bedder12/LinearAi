# ADR-003 — Page events, revisions, and API compatibility

**Status:** Reconciled proposal; API, rollout, and product approval required
**Decision track:** S02-C
**Code baseline:** `86e5ecdd65bbc127f2da49c03c00732f9ca2b124`
**Depends on:** Provisional [ADR-001](ADR-001-document-page-worksession.md) and [ADR-002](ADR-002-local-storage-outbox.md)

## Context and verified current behavior

- The Android request is `{workSessionId, revision, changes[]}`. Its flat event union is `stroke_added`, `stroke_removed`, and session-wide `document_cleared`; there is no document/page identity.
- The client increments revisions for empty add/remove calls even though no event is appended. This can create a missing revision that the server cannot accept.
- The backend requires contiguous revision numbers, orders events by `(revision, position)`, validates retries by exact stored event content, and commits the events and accepted revision atomically before acknowledgment. A database uniqueness constraint covers `(work_session_id, revision, position)`.
- The Android coordinator retries identical request snapshots and has one guarded gap-reconciliation attempt. The ViewModel applies acknowledgment only to the sent snapshot, preserving later edits.
- Existing tests cover event ordering, retries/conflicts, revision gaps, and snapshot acknowledgment. They do not prove page semantics, within-revision completeness, Room atomicity, or mixed-version behavior. PostgreSQL integration tests require a migrated database.

Evidence: [Android request](../../../../app/src/main/java/com/example/myapplication/analysis/transport/AnalysisRequestPayload.kt), [Android changes](../../../../app/src/main/java/com/example/myapplication/analysis/transport/InkChangePayload.kt), [revision rules](../../../../backend/app/services/revision_rules.py), [revision service](../../../../backend/app/services/revision_sync.py), [revision repository](../../../../backend/app/db/revision_repository.py), [database models](../../../../backend/app/db/models.py), [S00 audit](../current-state-audit.md), and [S02 instructions](../../slices/S02.md).

## Provisional contract direction

Keep the accepted MVP model: one document-wide monotonic revision/sync stream per document, shared by ink and structural events. One user operation advances the document revision once and contains an ordered, complete event batch. Event order within a revision is meaningful and persisted as `(revision, position)`. Page-targeted changes name a stable `pageId`; document-level changes name/resolve to the stable document identity. Page revision vectors and concurrent multi-device writes are out of MVP.

An event batch is immutable after local commit. The Room outbox persists the full batch and its event count atomically with document state and retries the same content. V2 requests contain one or more strictly ordered, unique revision batches; each batch has a positive `eventCount`, with the number of submitted events required to match it. Submitted revision batches must be contiguous. The backend validates and commits the whole request in one transaction or commits none of it. Exact replay of the same stored events/count returns the prior acknowledgment; different content or order at an accepted revision conflicts. A missing or malformed batch must never advance the revision. The count detects a payload whose event list is incomplete relative to its committed outbox manifest; it cannot prove that a buggy or malicious producer declared its intended complete event set.

The existing `(workSessionId, revision, exact ordered event contents)` identity is sufficient for retry idempotency; a separate client-generated idempotency key is not recommended for MVP. A canonical digest may be stored as an optimization/audit aid, but is not a completeness or authorization mechanism. The server must persist/derive and compare the accepted event count as well as exact event contents.

The current backend accepts a revision based on the submitted event list and has no independent event-count manifest. Client behavior also permits empty add/remove calls to increment revision without an event; this can make a later real event skip a revision because the no-event call is not sent. Make no-op operations leave the revision unchanged before persisting/depending on the outbox contract.

## Versioning and event alternatives

- **Keep extending v1 with optional page fields — not recommended.** It ambiguously depends on older clients' unknown-field behavior and cannot express page structure safely.
- **Freeze the existing v1 schema and introduce an explicit v2 sync contract — recommended for evaluation.** Preserve `/analysis/check` request/response semantics for old single-page work. Use a separate strict v2 contract with explicit protocol version, document/work-session association, ordered per-revision batches, page identities, and typed errors. This is a migration proposal, not an approved route or schema.
- **Replace v1 in place — rejected for MVP.** It risks breaking installed clients and makes rollout/rollback unsafe.

Candidate v2 operations: `page_created`, `page_reordered`, `page_deleted`, `page_restored`, `page_cleared`, plus page-scoped stroke add/remove. `document_cleared` remains document-wide and is never reinterpreted as a page clear. A deterministic reducer applies events in `(revision, position)` order:

- `page_created` introduces a new, never-before-used `pageId` and an insertion anchor (`afterPageId`, null means first); following events in the same revision may target it.
- `page_reordered` moves an active page after a valid active anchor (null means first); self-anchors, missing anchors, and cross-document IDs reject the transaction.
- `page_deleted` tombstones an active page without reusing its ID; `page_restored` reactivates that same tombstoned identity and retains its strokes/history. A permanent purge/retention policy is separate and unresolved.
- `page_cleared` removes active strokes from one page while preserving page identity; `document_cleared` retains its existing whole-document semantics.
- A page deleted earlier in a batch cannot be targeted by later ink/reorder/clear events until explicitly restored. The document must retain at least one active page; last-page deletion is rejected pending product approval.

These are recommended semantics, not approved product/API behavior. Event names can be simplified before implementation if an equally unambiguous reducer and stable restore semantics are specified.

The backend currently detects revision gaps but cannot independently know that a client intended more events within an otherwise valid revision. The proposed event count is a consistency check against the count committed with the local outbox batch, not proof of intent or a security control. Its value depends on the client computing and persisting it in the same transaction as the event batch. A digest is optional and does not resolve that trust boundary. Finalize the contract and assumptions in review before implementation.

## Illustrative request and acknowledgment

Field names and route are examples, not an approved wire contract. The redundant request-level `revision` field was removed. Batches are ordered, unique, and contiguous; each batch is complete and is never split across requests:

```http
POST /v2/documents/{documentId}/sync
Content-Type: application/json
```

```json
{
  "workSessionId": "9f3b2c61-8a47-4d05-b216-3e7c1a9f6048",
  "protocolVersion": 2,
  "batches": [{
    "revision": 8,
    "eventCount": 2,
    "events": [
      {
        "type": "page_created",
        "pageId": "2c8e1b74-6d3f-4a90-b521-8f0c2e7a9d43",
        "afterPageId": null
      },
      {
        "type": "stroke_added",
        "pageId": "2c8e1b74-6d3f-4a90-b521-8f0c2e7a9d43",
        "stroke": {
          "id": "stroke-1",
          "toolType": "pen",
          "points": [{"x": 12.25, "y": 8.5, "elapsedTimeMillis": 0}]
        }
      }
    ]
  }]
}
```

Within one request, a subsequent batch may have revision 9, 10, and so on. If a request includes an overlap for gap reconciliation, every overlapping revision must exactly match stored history before any new contiguous suffix is accepted; the server transaction remains all-or-nothing. The top-level `batches` array cannot be empty.

```json
{
  "documentId": "7d4a0f1e-2b36-4c85-9e10-6a1d3f8b5c20",
  "workSessionId": "9f3b2c61-8a47-4d05-b216-3e7c1a9f6048",
  "acceptedRevision": 8,
  "status": "accepted",
  "protocolVersion": 2
}
```

The v2 service must authenticate/authorize access independently of these identifiers before cloud/multi-user use; the current endpoint has no observed access-control dependency.

## Mixed-client rollout and migration proposal

1. Define authorization/ownership and a client upgrade/recovery path before exposing page writes remotely. Add backend document/page identity and v2 storage/validation additively. Backfill one stable document and initial page per legacy work session while preserving work-session IDs, accepted revisions, and historical event payloads.
2. Provide a legacy-page projection for historical unscoped v1 ink. Preserve the semantics of historical `document_cleared`.
3. Deploy a backend that supports both v1 and v2 and validates document/work-session/page associations before releasing a v2 client. Test rollback and deployment ordering. A v2 client must not fall back to v1 when the server does not support v2; it retains the outbox and reports upgrade-required/server-too-old status.
4. Permit v1 writes only before any v2 structural operation for a document. Once a v2 page-structure event is accepted, return a typed upgrade-required problem for v1 writes without changing server state, even if a later page deletion leaves one active page. A current v1 client maps unknown errors generically, so do not enable structural writes until a preparatory client has durable storage and an understandable upgrade/recovery message.
5. On all errors, gaps, or conflicts, retain the Room outbox and present safe retry/recovery. Never silently downgrade page-aware operations to v1.

There is no durable legacy Android snapshot. Any import of a still-live in-memory state is best-effort only and cannot guarantee survival across app update/process death.

## Executable acceptance gates

- Backend: page creation followed by page-targeted ink in one revision persists/replays in order; identical batch retry is idempotent; different order/content conflicts; missing revision, incomplete batch, invalid page, or wrong document fails atomically without advancing the watermark.
- Compatibility: v1 wire shape and behavior remain unchanged for a single-page legacy session; v1 write after multi-page transition returns typed upgrade error with no state change; historical v1 retry compares to unchanged history.
- Reducer: deterministic page create/reorder/delete/restore/clear, including tombstone identity retention, no ID reuse, valid anchors, at-least-one-active-page invariant, and historical whole-document clear projection.
- Rollout: v2 client to v1-only server fails safely with retained outbox and no v1 fallback; v1 client to structurally upgraded document is rejected without mutation; rollback after v2 writes does not destructively downgrade.
- Android: empty add/remove does not advance revision; retries preserve batch bytes/semantic content; acknowledgment cannot remove outbox entries beyond the request snapshot, including concurrent local edits.
- Migration: upgrade a migrated PostgreSQL fixture with historical v1 rows, verify stable document/page mapping, accepted revision, and historical replay; then exercise v1 and v2 HTTP flows.
- Integration: run Android unit tests and backend unit/API tests, plus PostgreSQL migrations/integration against a disposable configured database before implementation exit.

## Open decisions and implementation gates

- Approve v2 route/schema, event-count contract and persistence, structural event set, page ordering/recovery semantics, and final-page deletion behavior. A digest is optional, not a substitute for the event-count/outbox invariant.
- Decide the v1 write cutoff/upgrade policy and whether preparatory clients may continue single-page sync after a document has seen a v2 structure event.
- Prove old-server/new-client and new-server/old-client rollout/rollback behavior and the actionable blocked-write UX before enabling v2 writes.
- Fix the empty-operation revision invariant before durable outbox rollout. Establish backend database integration and end-to-end coverage; S00 remains partial.
- This ADR is a design proposal only. No API/event/schema/production change is authorized by it.
