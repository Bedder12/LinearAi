# S03-A — Durable local persistence foundation

**Status:** Implemented as a repository foundation; not integrated into the running workspace
**Branch baseline:** `47e0afabf1d35dcd2d0ca766d5837f087b75dc50` (main after PR #20)
**Scope:** Room schema version 1, local AndroidX Ink codec, and single-page workspace repository. No UI, backend, or sync-coordinator integration.

## Architecture decisions

- **Persistence:** Room 2.8.5 with KSP 2.3.14. Android's current Room setup recommends KSP for Kotlin processors. The Room Gradle plugin exports schema JSON under `app/schemas/`; destructive migration fallback is not configured.
- **Initial schema:** A singleton workspace anchor selects one normalized document. A document has a unique work-session ID and current revision; it owns an ordered page; the page owns ordered strokes. A document-scoped outbox batch owns ordered immutable event payloads. Primary keys, unique indexes, and foreign keys enforce these relationships.
- **Identity:** UUID document, page, work-session, stroke, and batch IDs are generated once and persisted. Reopening never replaces them.
- **Coordinates:** Both document and page store profile metadata. New rows use profile ID `legacy-raw-canvas-v1`, profile version 1, and unit `canvas-px`. Values are retained as recorded by the current identity-transform workspace. They are not converted or relabeled as A4 points. No page dimensions, zoom, pan, or page-aware API behavior are added.
- **Ink encoding:** The local format is independent of transport DTOs. Version 1 wraps AndroidX Ink 1.0.0 `StrokeInputBatch` encoding with stable stroke ID, explicit versioned stock pressure-pen family ID, color, size, epsilon, bounded lengths, and CRC32. Unknown brush families/versions and malformed or mismatched records fail explicitly; there is no default-pen fallback.
- **Outbox:** Each revision has a positive event count and SHA-256 digest over ordered `(position, type, JSON payload)` values. Payload JSON uses the existing v1 `InkChangePayload` discriminator and is stored when the operation is committed; it is not regenerated from current strokes. Multi-stroke add order follows input collection iteration; removals follow persisted document order.
- **Transactions:** Add/remove/clear perform content mutation, compare-and-set revision advancement, batch manifest, and ordered event inserts in one `RoomDatabase.withTransaction` block. A failure rolls back the full operation. Acknowledgment validates the full batch identity, event count/order, and digest before deletion, in one transaction; unrelated later batches remain.
- **No-op semantics:** Empty add/remove and removal of unknown stroke IDs do not advance revision. Clear-on-empty intentionally retains the current domain contract: it advances the revision and appends `document_cleared`, as asserted by `StudentWorkTest.clear_incrementsRevision_addsClearChange_andPreservesWorkSessionId`.

## Schema and migration

| Table | Purpose and constraints |
|---|---|
| `workspace_anchor` | Singleton row (`id = 1`) points to the default document; unique document FK. |
| `documents` | Stable `documentId` PK, unique `workSessionId`, revision, document format version, coordinate profile ID/version/unit. |
| `pages` | Stable `pageId` PK, document FK, position unique per document, page format and coordinate profile metadata. S03-A creates one page only. |
| `strokes` | Stable `strokeId` PK, page FK, unique page/position, codec version, encoded Ink bytes. |
| `outbox_batches` | Composite `(workSessionId, revision)` PK, unique batch ID, positive event count, ordered-payload digest; FK to document stream. |
| `outbox_events` | Composite `(workSessionId, revision, position)` PK, event type and immutable v1 payload JSON; cascading FK to batch. |

Database/schema version is **1**. There is no previously released Room database to migrate. The first exported schema is checked in for future migration tests. Every later schema change must add an explicit `Migration`, retain prior exported schemas, and be tested; no destructive fallback is allowed.

## Validation evidence

The requested Gradle tasks completed successfully in order:

1. `:app:assembleDebug`
2. `:app:testDebugUnitTest` — 29 tests passed; 0 failures, errors, or skips.
3. `:app:assembleDebugAndroidTest` — instrumented-test Kotlin compilation and packaging passed.
4. `:app:pixelApi35DebugAndroidTest` — 26 tests passed on the Pixel API 35 managed device; 0 failures, errors, or skips. This includes all 17 `RoomWorkspaceRepositoryTest` cases and `InkStorageRoundTripSpikeTest.stylusStrokeSurvivesFileRoundTripWithIdentityInputsAndBrush`.
5. `:app:lintDebug`

The repository instrumented tests verify schema export/version 1, stable IDs and stroke reconstruction after database reopen, ordered outbox batches, no-op behavior, rollback, concurrent revisions, immutable payloads, acknowledgment boundaries, and explicit corruption failures. A missing workspace anchor with existing documents fails explicitly instead of creating an empty replacement. This evidence covers the persistence foundation only; it does not demonstrate runtime ViewModel integration or process-death durability of user-facing work.

## Serialization fidelity and limits

The codec preserves all input attributes represented by AndroidX Ink 1.0.0 `StrokeInputBatch` storage encoding: input order, tool type, x/y, elapsed time, pressure, tilt, and orientation. For the supported stock pressure-pen V1 brush, it preserves family identity, color, size, and epsilon. Instrumented tests use tolerances of 0.1 for x/y and 0.05 for pressure, tilt, and orientation; timestamps, tool type, ID, family, and brush fields are checked exactly.

This is not a claim of lossless or pixel-identical rendering. Only the pressure-pen V1 family is registered; other families fail visibly. Visual renderer equivalence, custom brush families, long-term codec compatibility, and physical stylus input remain separate validation work.

The existing S03 spike showed feasibility for one sample, not production serialization or crash recovery. S03-A adds multi-stroke, database-reopen, corruption, and transaction tests but does not integrate the repository into the ViewModel. Therefore the application UI is not yet proven to survive process death.

## Recovery and backup policy

- **Decode/corrupt stroke:** Fail the repository read with a typed codec exception; leave the row, document, revision, and outbox untouched. No empty-document fallback or automatic row deletion is provided.
- **SQLite file corruption:** Do not call destructive migration or replace the database. Surface/open failure remains explicit; an absent workspace anchor alongside existing documents also fails rather than creating a new empty workspace. Quarantine/export and user recovery UX are not implemented in S03-A.
- **Schema migration:** No migration is needed from a prior Room version. Future upgrades must use explicit migrations and exported-schema tests.
- **Auto Backup/device transfer:** Existing manifest settings (`allowBackup=true`) and generic sample rule files are unchanged. Whether local documents and pending outbox participate in cloud backup or device transfer requires product/privacy approval. This PR does not claim that the database is excluded.
- **Restored/stale outbox:** Entries retain exact IDs, payloads, and revision data. S03-A does not send them. Before S03-B enables synchronization, restored data must be reconciled with server history; it must not be blindly replayed or used to overwrite either side.
- **Existing in-memory work:** The repository is not wired into the current ViewModel. A live-session import, if later implemented, can only be best-effort while that process still holds the state. Already-lost in-memory work cannot be recovered retroactively.

## Deferred to S03-B

Integrate repository ownership into the runtime workspace/ViewModel, define truthful local-save state, migrate only a still-live session if chosen, and prove app process-death/reopen behavior. Do not claim user-visible durability until those tests pass. Sync dispatch, acknowledgment UI, backup policy, recovery UX, and account ownership remain separately gated.
