# ADR-001 — Document, Page, and WorkSession identity

**Status:** Proposed; product and API review required
**Decision track:** S02-A
**Code baseline:** `86e5ecdd65bbc127f2da49c03c00732f9ca2b124`
**Scope:** Domain identity and ownership only. This does not approve an API or database migration.

## Context and verified current behavior

- Android `StudentWork` currently owns one generated `workSessionId`, one `InkDocument`, one revision, and an in-memory list of pending changes. `InkDocument` contains an unscoped list of strokes. There is no durable Document/Page model.
- The client and FastAPI request identify a work session with `workSessionId`. Backend `WorkSession` owns one accepted revision and ordered ink changes. The current events have no `documentId` or `pageId`; `document_cleared` is session-wide.
- Android unit tests cover revision changes, clear, and acknowledgment filtering. Backend tests cover revision rules and persistence/replay where PostgreSQL is available. There are no current page or document lifecycle tests.
- S00 records process-death loss of unsynchronized Android work and incomplete Android–FastAPI–PostgreSQL validation. The analysis route has no observed authentication/authorization dependency. A UUID is not authorization.

Evidence: [StudentWork.kt](../../../../app/src/main/java/com/example/myapplication/domain/StudentWork.kt), [InkDocument.kt](../../../../app/src/main/java/com/example/myapplication/domain/InkDocument.kt), [MathWorkspaceViewModel.kt](../../../../app/src/main/java/com/example/myapplication/ui/workspace/MathWorkspaceViewModel.kt), [analysis.py schema](../../../../backend/app/schemas/analysis.py), [models.py](../../../../backend/app/db/models.py), [revision_sync.py](../../../../backend/app/services/revision_sync.py), [S00 audit](../current-state-audit.md), and [S01 UX specification](../ux-spec.md).

## Proposed model

```text
Authenticated Owner (future; identity is not authorization)
  1 ── 0..* Document
             1 ── 1..* ordered Page
             1 ── 1 WorkSession (MVP proposal: stable sync/revision stream)
                         1 ── 0..* revisioned Change
Page        1 ── 0..* Stroke
Change      0..1 ── Page (page-targeted ink/page operation; metadata may target Document)
```

Give each Document, Page, and WorkSession a distinct stable ID. A WorkSession is a synchronization/revision stream associated with a document, not an app process or editing visit. The MVP proposal is one stable WorkSession per Document; multiple concurrent streams per document remain an open multi-device decision.

Every document has at least one active page. Pages have stable identity and explicit order. A stroke belongs to exactly one page; it is not moved when another page is reordered. Create a document, initial page, and work stream as one logical operation. Rename changes metadata only. Reorder changes order only. Clearing a page preserves page identity and does not clear siblings. Page/document removal and restoration preserve IDs; Trash is a document lifecycle state, not ID replacement. Permanent purge and retention need explicit product/security policy. Whether users may remove the final page is not yet approved.

Illustrative IDs only:

```text
documentId   = 7d4a0f1e-2b36-4c85-9e10-6a1d3f8b5c20
pageId       = 2c8e1b74-6d3f-4a90-b521-8f0c2e7a9d43
workSessionId= 9f3b2c61-8a47-4d05-b216-3e7c1a9f6048
```

## Alternatives considered

1. **Treat `workSessionId` as `documentId`.** Minimizes identifiers and mapping but permanently overloads the legacy session name and complicates page/document lifecycle and API evolution.
2. **Separate Document ID and stable WorkSession ID (proposed).** Makes document identity explicit while retaining the current session-keyed revision stream. Requires a mapping and compatibility/migration plan.
3. **Create a new WorkSession for each app launch/edit visit.** Rejected as the default proposal: it fragments revision history and conflicts with resume and offline continuity.

## Revision and analysis implications

A document-wide monotonic revision is the least disruptive candidate because the existing backend tracks one accepted revision per WorkSession. Page-targeted events would carry stable `pageId`s, while document metadata/structural events could target the document. This remains a candidate for S02-C, not a settled API contract. Analysis must bind to immutable document/page/revision identity. Whether an edit on another page stales page-scoped feedback requires product agreement and S02-F.

## Invariants and acceptance gates

- IDs are stable through rename, reorder, clear, soft-delete, and restore; IDs are never silently reused.
- Every active page belongs to exactly one document; every stroke belongs to exactly one page; ordering is deterministic.
- Clearing or deleting a page cannot mutate a sibling page. A document always has at least one active page unless product explicitly chooses otherwise.
- Every accepted change is attributable to a stable document and revision, and page-targeted changes name an existing page in that document.
- Reopen/resume resolves the same document, page, and work-stream identities; it never silently creates a replacement.
- Authorization is based on an authenticated owner/access policy, never possession or knowledge of an identifier.

Add domain and API tests for these invariants before implementing page operations. S02-C must define revision scope, structural events, old-client behavior, and migration before S10 page operations ship.

## Open approvals and consequences

- Product: confirm stable one-to-one document/work-stream relationship for MVP, final-page deletion, Trash retention/purge, and result staleness scope.
- Security: define owner identity and access policy; no user ownership model is currently evidenced.
- Architecture: decide whether concurrent devices need one or multiple streams per document and how conflicts are represented.
- Until approvals and S02-C are complete, this ADR is guidance for design discussion only. Current runtime remains a single in-memory work session without pages.
