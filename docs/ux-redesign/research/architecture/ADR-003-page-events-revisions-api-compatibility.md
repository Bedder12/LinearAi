# ADR-003 — Page events, revisions, and API compatibility

**Status:** Proposed; API, rollout, and product approval required
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

An event batch is immutable after local commit. The Room outbox persists the full batch atomically with document state and retries the same content. Server acknowledgment applies only to the exact sent snapshot. Identical replay returns the prior acknowledgment; a different event sequence at an accepted revision conflicts. A missing or malformed batch must never advance the revision.

## Versioning and event alternatives

- **Keep extending v1 with optional page fields — not recommended.** It ambiguously depends on older clients' unknown-field behavior and cannot express page structure safely.
- **Freeze the existing v1 schema and introduce an explicit v2 sync contract — recommended for evaluation.** Preserve `/analysis/check` request/response semantics for old single-page work. Use a separate strict v2 contract with explicit protocol version, document/work-session association, ordered per-revision batches, page identities, and typed errors. This is a migration proposal, not an approved route or schema.
- **Replace v1 in place — rejected for MVP.** It risks breaking installed clients and makes rollout/rollback unsafe.

Candidate v2 operations to assess: `page_created`, `page_moved`, `page_deleted`, `page_cleared`, plus page-scoped stroke add/remove. `document_cleared` remains document-wide and is never reinterpreted as a page clear. Page create must precede same-batch operations targeting that page; delete must not precede any later operation targeting the deleted page. Deleting the final page is deferred pending product decision.

The backend currently detects revision gaps but cannot independently know that a client intended more events within an otherwise valid revision. A v2 batch therefore needs a completeness mechanism (for example an event count plus canonical digest/manifest) and strict atomic validation. Count-only manifests detect truncation/count mismatch but do not prove that a buggy client declared the intended complete batch; the exact mechanism and threat/failure assumptions remain open for ADR-007/security review.

## Illustrative request and acknowledgment

Field names and route are examples, not an approved wire contract:

```http
POST /v2/documents/{documentId}/sync
Content-Type: application/json
```

```json
{
  "workSessionId": "9f3b2c61-8a47-4d05-b216-3e7c1a9f6048",
  "protocolVersion": 2,
  "revision": 8,
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

1. Add backend document/page identity and v2 storage/validation additively. Backfill one stable document and initial page per legacy work session while preserving work-session IDs, accepted revisions, and historical event payloads.
2. Provide a legacy-page projection for historical unscoped v1 ink. Preserve the semantics of historical `document_cleared`.
3. Deploy a backend that supports both v1 and v2 and validates document/work-session/page associations before releasing a v2 client. Test rollback and deployment ordering.
4. Permit v1 writes only while a document remains single-page. Once a v2 structural event makes it multi-page, return a typed upgrade-required problem for v1 writes without changing server state. The old client currently maps unknown errors generically; an actionable user path needs a compatible app release.
5. On all errors, gaps, or conflicts, retain the Room outbox and present safe retry/recovery. Never silently downgrade page-aware operations to v1.

There is no durable legacy Android snapshot. Any import of a still-live in-memory state is best-effort only and cannot guarantee survival across app update/process death.

## Executable acceptance gates

- Backend: page creation followed by page-targeted ink in one revision persists/replays in order; identical batch retry is idempotent; different order/content conflicts; missing revision, incomplete batch, invalid page, or wrong document fails atomically without advancing the watermark.
- Compatibility: v1 wire shape and behavior remain unchanged for a single-page legacy session; v1 write after multi-page transition returns typed upgrade error with no state change; historical v1 retry compares to unchanged history.
- Android: empty add/remove does not advance revision; retries preserve batch bytes/semantic content; acknowledgment cannot remove outbox entries beyond the request snapshot, including concurrent local edits.
- Migration: upgrade a migrated PostgreSQL fixture with historical v1 rows, verify stable document/page mapping, accepted revision, and historical replay; then exercise v1 and v2 HTTP flows.
- Integration: run Android unit tests and backend unit/API tests, plus PostgreSQL migrations/integration against a disposable configured database before implementation exit.

## Open decisions and implementation gates

- Approve v2 route/schema, batch completeness mechanism, structural event set, page ordering operation semantics, and final-page deletion behavior.
- Decide the v1 write cutoff/upgrade policy and whether existing clients can continue single-page sync after a document becomes multi-page.
- Prove old-server/new-client and new-server/old-client rollout/rollback behavior before enabling v2 writes.
- Fix the empty-operation revision invariant before durable outbox rollout. Establish backend database integration and end-to-end coverage; S00 remains partial.
- This ADR is a design proposal only. No API/event/schema/production change is authorized by it.
