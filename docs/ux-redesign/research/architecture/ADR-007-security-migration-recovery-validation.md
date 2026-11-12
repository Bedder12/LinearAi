# ADR-007 — Authorization, migration, conflict recovery, and validation

**Status:** Proposed security/recovery requirements; ownership policy and implementation approval required
**Decision track:** S02-G
**Code baseline:** `86e5ecdd65bbc127f2da49c03c00732f9ca2b124`
**Reviewed HEAD:** `35e006a81fb6ffc83f0cf95d5d19a7a94bcca4c0`

## Context and verified current behavior

- Android generates a `workSessionId`; backend accepts it as a storage key and can create a session row from that ID.
- The inspected analysis route has a database dependency but no observed authentication/authorization dependency. A session/document UUID is not proof of identity or ownership.
- The backend currently serializes updates with a session-row lock and validates revision order/replay before atomic commit. Android verifies overlap before one bounded revision-gap recovery. Conflict and invalid states stop automatic retries.
- The only backend migration creates `work_sessions` and `ink_changes`; its downgrade drops both tables. There is no existing durable Android database.
- Android manifest sets `allowBackup=true`. Backup/extraction rule files have no active scoped policy in the inspected checkout. A future database/outbox may therefore be included in platform backup/transfer unless policy is defined.
- S00 records unsynced loss, probable but untraced duplicate eraser revision, missing PostgreSQL-backed verification, and unverified physical stylus behavior.

Evidence: [analysis route](../../../../backend/app/api/analysis.py), [request schema](../../../../backend/app/schemas/analysis.py), [revision repository](../../../../backend/app/db/revision_repository.py), [revision service](../../../../backend/app/services/revision_sync.py), [backend migrations](../../../../backend/migrations/versions/0001_initial_storage.py), [Android manifest](../../../../app/src/main/AndroidManifest.xml), [backup rules](../../../../app/src/main/res/xml/backup_rules.xml), [data extraction rules](../../../../app/src/main/res/xml/data_extraction_rules.xml), [S00 audit](../current-state-audit.md), and [ADR-003](ADR-003-page-events-revisions-api-compatibility.md).

This is architecture research, not a penetration test or a claim that authorization exists.

## Proposed authorization and ownership requirements

Before any new private/cloud/multi-user document API is enabled:

- Authenticate a principal on every remote read/write and authorize it against server-side document ownership or an explicitly designed sharing policy.
- Validate the document → WorkSession → Page relationships on the server for every event. Never use UUID possession as access control.
- Start with owner-only access; sharing and concurrent multi-device editing remain deferred until separately designed.
- Decide whether unauthenticated use is local-only and how a local document becomes account-owned. Do not silently claim ownerless legacy sessions for whichever account presents their ID.
- Decide a safe legacy-session linking/claim process with explicit user verification and data-integrity checks. Until then, orphaned remote data remains unclaimed or access is blocked.

Alternatives include a narrowly scoped opaque bearer capability with explicit expiry/revocation/recovery, but it is not equivalent to an ID. A shared anonymous namespace is not an acceptable private-document model. The current unauthenticated v1 route is a pre-existing risk requiring owner/security review before broadening exposure or promising privacy; this ADR does not change it.

## Non-destructive recovery contract

Preserve the accepted ADR-002/006 contract: local durable content/outbox survive network errors, conflicts, process death, backup restore uncertainty, and server unavailability. Retry only exact committed events. Acknowledge only the exact request snapshot.

On conflict, gap, stale restore, authorization failure, or invalid history:

1. Stop automatic writes for the affected stream; do not mutate server history.
2. Keep the local copy and pending journal; do not reset, overwrite, or silently drop either side.
3. Show a distinct actionable state and offer safe retry/status reconciliation, compare/export, or an explicit user-directed copy/fork after the necessary read access exists.
4. Require a reviewed recovery operation with stable IDs and an audit trail; never default to last-write-wins.
5. Treat account removal/revocation as an access-state transition, not implicit local data deletion; local retention/deletion policy needs explicit product direction.

## Migration, backup, and rollback sequence

1. Approve owner/linking, retention, backup/transfer, old-client and recovery policies before production schema changes.
2. Add new tables/columns and constraints additively. Backfill one stable Document and initial Page per legacy WorkSession while preserving session IDs, accepted revisions, event positions, and original payloads. Historical unscoped events project onto the legacy page; historical `document_cleared` remains whole-document clear.
3. Keep data unclaimed until an explicit ownership policy is satisfied. Verify row counts, checksums/event replay, foreign-key associations, and revision watermarks before enabling reads/writes.
4. Deploy a dual-compatible, authenticated server; verify legacy v1 single-page behavior and v2 association checks before client rollout. Never silently downgrade v2 operations.
5. Enable v2 structure changes only after the client has durable storage and understands upgrade-required/recovery states. Any in-memory-only legacy import is best-effort; lost work cannot be reconstructed by migration.
6. Prefer roll-forward or a feature flag to destructive downgrade. After v2 writes, do not deploy an old server that cannot interpret those events or restore an older backup without a reviewed recovery path. The current initial migration's downgrade is destructive and is not an acceptable production rollback plan.

For Android backup/restore, explicitly choose whether local documents/outbox participate in cloud backup and device transfer. Exclude credentials/tokens. If outbox is restored, reconcile it against server history before delivery. Corrupt local data must be quarantined/preserved where possible and reported; never silently replace it with an empty document.

## Validation strategy and acceptance tests

- Authorization API tests: unauthenticated, wrong owner, valid owner, revoked owner, and document/session/page mismatch; denied writes leave all persisted state unchanged. Include explicit legacy ownerless/account-linking cases.
- Recovery tests: lost response followed by exact replay, changed-content conflict, incomplete revision batch, acknowledgment racing later edits, stale backup restore, and a user-directed copy/fork preserving both source histories.
- Migration tests: upgrade historical session/event fixtures, verify IDs/mappings, exact payloads, ordering and watermarks; validate foreign keys and replay both before and after deployment.
- Rollback tests: feature-disabled client, old server receiving v2, database backup restore, and forward recovery after new events have been written.
- Android persistence tests: transaction failure, process kill/reopen, corruption quarantine, backup policy, every schema migration.
- End-to-end tests: migrated PostgreSQL plus Android→FastAPI→PostgreSQL success, exact retry, conflict, authorization denial, and outage. Physical stylus testing is a separate input release gate.

S00 remains partial until the exact eraser event sequence, successful sync/conflict flow, PostgreSQL-backed integration/E2E, and physical stylus behavior have evidence. No security control, backup behavior, or recovery guarantee is claimed as implemented by this ADR.
