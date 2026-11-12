# S02 — Architecture completion and S03+ implementation handoff

**Status:** Architecture handoff; not production implementation approval
**S02 source baseline:** `86e5ecdd65bbc127f2da49c03c00732f9ca2b124`
**Reviewed checkout HEAD at orchestration start:** `35e006a81fb6ffc83f0cf95d5d19a7a94bcca4c0`
**Scope:** Integrates the seven architecture tracks and gives a dependency-ordered implementation plan. Decisions below retain their individual approval status in each ADR.

## Source of truth and evidence boundary

The S01 specification and S00 audit were read alongside S02 instructions, progress, and ADR-001–004. Tracked Android/backend implementation inspected by the read-only investigations was unchanged from the S02 source baseline through the reviewed HEAD. The checkout already contained the untracked zero-byte `backend/app/services/ink_state`; it was preserved and excluded from this work.

All S02-A through S02-G investigations and C/D reviews were read-only. Source/test claims are linked in the ADRs. Tests were inspected, not rerun for this documentation handoff. No S00 limitation is marked passed: PostgreSQL-backed verification, Android–FastAPI–PostgreSQL E2E, exact eraser event tracing, and physical stylus behavior remain open.

## Consolidated ADR status

| ADR | Status | Integrated direction | Decision still required / implementation gate |
|---|---|---|---|
| [ADR-001 — identity](ADR-001-document-page-worksession.md) | Accepted provisionally for S02 planning | Separate stable DocumentId, PageId, and WorkSessionId; one WorkSession per document for MVP; document-wide revision candidate; no multi-device concurrency. | Final-page deletion, page/document retention, analysis staleness scope, owner/linking policy, and API migration. No schema/API approval. |
| [ADR-002 — local persistence](ADR-002-local-storage-outbox.md) | Accepted provisionally for S02 planning | Room/SQLite local source of truth behind repositories; content, revision, and immutable pending events/outbox commit atomically; foreground sync first. | Prove complete lossless AndroidX Ink serialization, transaction/retry/ack behavior, migrations, backup/restore and corruption recovery. No dependency/schema approval. |
| [ADR-003 — sync API](ADR-003-page-events-revisions-api-compatibility.md) | Reconciled proposal | Preserve v1; evaluate strict additive v2, document-wide ordered revisions, complete immutable batches, explicit page lifecycle, exact-replay idempotency, and no silent downgrade. | Approve exact v2 route/schema/manifest, reducer semantics, client upgrade UX, v1 cutoff, backfill, and rollout/rollback. Current backend has no page model or independent batch-count check. |
| [ADR-004 — geometry](ADR-004-page-coordinate-contract.md) | Reconciled proposal; A4 initial profile candidate | Page-local logical points; A4 portrait candidate; shared event-to-world/render transforms; page-scoped eraser hit testing; viewport independent of content/revision. | Approve profile and eraser size; prove transform mapping/alignment and lossless serialization. AndroidX Ink 1.0.0 exposes mapping APIs, but app integration is untested. |
| [ADR-005 — Compose state/navigation](ADR-005-compose-state-navigation-adaptive-shell.md) | Proposed | Stable document-ID routes; document-scoped ViewModel; repository-backed durable state; UI-only panel/tool/viewport state; available-width adaptive layout. | Choose navigation dependency/route owner and saved viewport policy; test lifecycle, focus, insets, and thresholds. |
| [ADR-006 — save/sync/analysis](ADR-006-save-sync-and-analysis-lifecycles.md) | Proposed | Separate local commit, upload/acknowledgment, uncertain/conflict recovery, and revision-bound math analysis. | Finalize error/result states and analysis idempotency/status contract after C and authorization. Current endpoint is sync only; analysis implementation is a stub. |
| [ADR-007 — security/recovery/migration](ADR-007-security-migration-recovery-validation.md) | Proposed security/recovery requirements | Owner-based authorization before private/cloud APIs; non-destructive conflicts; additive migration and roll-forward; explicit backup/corruption policy. | Select auth/account-linking, legacy ownerless data policy, backup/retention, and recovery UX. Current route is unauthenticated; no security control is implemented here. |

### Contradictions reconciled

- **Document identity vs. sync stream:** keep distinct IDs with a stable 1:1 mapping for MVP; WorkSession is a sync/revision stream, not an app session.
- **Page delete/restore vs. stable identity:** v2 proposal uses tombstone/restore events retaining PageId and history; permanent purge is separate. This refines, but does not approve, ADR-001's lifecycle.
- **Revision batches:** keep document-wide contiguous revisions and exact event order. Add a persisted per-revision event count to the outbox/API proposal. The count verifies submission against the locally committed manifest; it cannot prove that a buggy producer originally declared every intended event. No separate idempotency key is needed if stream/revision and exact immutable batch content are persisted and compared.
- **Batch request shape:** v2 proposal uses ordered, unique, contiguous per-revision batches; the whole submitted request commits or rolls back atomically. Remove the redundant top-level revision in the example. Whether to cap request range/size is an implementation limit to define, not a semantic change.
- **Mixed clients:** keep v1 semantics for legacy single-page data. After any accepted v2 structural event, reject v1 writes without mutation, even if the document later returns to one active page. A v2 client never falls back to v1 when the server is old. Do not enable structural writes until the client has durable outbox and an actionable upgrade/recovery state.
- **Ink transforms:** pinned AndroidX Ink exposes `pointerEventToWorldTransform`, `strokeToWorldTransform`, and renderer `strokeToScreenTransform`; this proves API capability, not correct app integration. Page-space points, canvas-pixel transforms, page filtering, insets, and the actual input/render alignment require instrumentation.
- **Security vs. rollout:** current v1 unauthenticated behavior is a pre-existing risk, not an S02 fix. Do not broaden private/cloud claims or enable new page-aware remote writes until ownership/authz and migration gates are satisfied.
- **S01 vs. current runtime:** all screens, page operations, durable save, and mathematical feedback remain proposals until implemented and tested. Keep S00 partial.

## Dependency-ordered implementation plan

Each slice below is a future implementation phase; it is not authorized by this architecture-only handoff. A failed gate stops dependent work rather than inviting silent fallback.

| Slice | Scope and dependencies | Affected subsystems | Acceptance and required tests |
|---|---|---|---|
| **S03 — Durable local document repository and outbox** | After product confirms the initial page profile and the AndroidX Ink serialization spike proves lossless round-trip. Add Room behind a repository; create stable DocumentId/PageId/WorkSessionId for the existing one-page workspace. Atomically persist canonical ink/profile data, document revision, and immutable batches using the existing v1 event semantics; do not add page-aware API behavior. Empty add/remove operations must not advance revision. | Android Gradle/dependency manifest; domain; Room database/entities/DAOs/migrations; repository/outbox; ViewModel state source; ink serializer. | Atomic state/revision/outbox transaction rollback; empty operations do not advance revision; reopen/process-kill restoration after commit; stable IDs; stroke/brush/input/profile and outbox round-trip; no false “saved locally” before commit; corruption surfaced without empty replacement; schema migration tests. |
| **S04 — Sync dispatcher and eraser/revision recovery** | Depends on S03. Instrument and resolve S00-F02 before persisted eraser operations are sent. Preserve existing v1 request semantics for the single-page document; retry the identical committed batches and apply acknowledgment only to the sent snapshot. | `InkSurface` callbacks; `StudentWork`/sync state; Room outbox; `SyncCoordinator`/repository; FastAPI v1 service; unit/instrumented/integration tests. | One erase gesture yields the intended event(s); process restart preserves exact retry content; ack racing newer edits preserves them; conflict/gap stops writes without data loss; PostgreSQL transaction/replay tests pass before claiming E2E. |
| **S05 — Authorization and legacy sync verification** | Depends on S04 plus a product/security decision for authentication and legacy ownership. Keep existing v1 payload semantics for single-page work. Do not enable broader cloud/private behavior while the endpoint remains unauthenticated. Configure a migrated PostgreSQL test database before claiming backend E2E. | FastAPI auth/access boundary; v1 sync service/repository; Android sync status; PostgreSQL migrations/integration environment. | Auth/ownership tests (once provider selected); v1 event/replay/gap/conflict tests; process-death/lost-ack exact replay; Android→FastAPI→PostgreSQL success, outage, duplicate, conflict; existing data unchanged on rejection. |
| **S06 — Compose navigation and adaptive shell** | Depends on S03 repository and ADR-005 review. Establish stable document-ID routes and state boundaries before expanding destinations. | MainActivity/AppRoot; route/back stack; app container/ViewModel factories; workspace layout; WindowInsets; Compose UI tests. | Navigation back/resume keeps correct document and UI state; no extra document/revision/network event; width tests at 600/840/1200 dp and 560 dp canvas minimum; resize/fold/IME/inset; focus/TalkBack/48 dp targets. |
| **S07 — Truthful toolbar, local-save and sync feedback** | Depends on S03–S06 and S01 UX. Keep Clear separate from page/document deletion and require confirmation or proven undo. | Workspace header/toolbar; save/sync state model; accessibility announcements; error/recovery UI. | Distinguish current-session, durable local, queued/uploading, accepted, uncertain/conflict; no “synced” for no-op without known state; Clear cancel/confirm tests; S00-F03 inset and F04 clear behavior verified on target windows. |
| **S08 — Single-page navigator shell** | Depends on real persisted Page identity and S06 adaptive shell. No inert Add Page control until S10 ships operations. | Page rail/sheet; active-page UI state; workspace ViewModel; accessibility/focus. | Real active page is shown; collapsing/opening does not mutate document or viewport; compact/wide threshold and TalkBack tests; no fake page list. |
| **S09 — Ownership and authorization foundation** | Must precede any new private/cloud v2 page writes. Choose authentication/account-linking and legacy ownerless-data policy; no multi-device editing. | FastAPI auth dependencies; owner/document/session association; Android credential boundary; secrets/backup policy; API tests. | Unauthenticated/wrong-owner/revoked/cross-document/page requests fail without mutation; explicit ownerless legacy handling; credentials excluded from backups; no UUID-based authorization. |
| **S10 — Page domain, v2 API and additive migration** | Depends on approved ADR-003, S05/S09, and PostgreSQL test availability. Backfill Document/initial Page per legacy WorkSession without rewriting event history; deploy dual v1/v2 before client use. | Android event DTOs/client; FastAPI v2 route/schema/reducer; backend models/migrations/repository; rollout flags. | Ordered create/reorder/delete/restore/clear and page ink replay; event-count and exact-payload checks; atomic rollback; stable tombstones; legacy `document_cleared`; v1/v2 compatibility matrix; upgrade/rollback and migration fixtures. |
| **S11 — Continuous vertical page surfaces** | Depends on S10 plus geometry profile/coordinate contract. Start with fixed-size pages and no zoom/pan gesture promises. | Workspace page stack; InkSurface ownership/clipping; page-local rendering and input routing. | Page boundaries/order/scroll; page-ID-scoped strokes; navigation/layout changes preserve ink; deterministic renderer tests at fit width; no cross-page eraser hits. |
| **S12 — Coordinate transforms and stylus gestures** | Depends on S11 and ADR-004 implementation proof. Physical stylus/touch checks are required before enabling gestures broadly. | AndroidX Ink input adapter; renderer transforms; viewport/gesture state; hit testing; responsive canvas. | Forward/inverse and wet/dry alignment at scales/densities/insets; constant dp eraser shape/size; edge/gap tests; resize anchor; zoom/pan does not alter stroke/revision/outbox; physical stylus and touch matrix. |
| **S14–S16 — Home, Library, organization, and Trash** | Depends on S03/S06 and server/local ownership decisions. Keep local browsing available offline. Trash semantics must be approved before destructive actions. | Document repository/query; Home/Library routes/search; optional Subject/Notebook; soft-delete/restore/purge policy and UI. | Stable-ID open/resume/create; search/offline/error tests; restore retains identity/location; destructive purge confirmation, authorization, retention and failure tests. |
| **S17 — AI Review state UI** | Depends on ADR-006 analysis contract; can initially show explicit unavailable/unsupported states only. | Review pane/sheet/route; revision binding; stale state; accessibility/focus. | Same document/tool/viewport after close; results linked to target revision; edits mark stale; no math verdict from sync acknowledgment; retry/cancel/uncertain states. |
| **S18 — Recognition and verified math feedback** | Depends on versioned analysis request/result and evaluated verifier; independent of ink synchronization success. | FastAPI analysis service; recognition/verifier/evaluation; Android AnalysisRepository; feedback UI. | Dataset/evaluation threshold approved separately; unsupported/uncertain abstains; immutable results, hint-first progression, stale/cancel/idempotency and API tests. No correctness claims without verifier evidence. |
| **S19 — Account/cloud expansion** | Deferred. Requires S09 security, explicit product scope, privacy/backup policy, and a multi-device conflict model. | Account linking; remote library; optional sharing/multi-device sync; backup/restore. | Ownership/revocation, concurrent writer/conflict, restore and recovery tests; no last-write-wins; release/security approval before enabling. |

Slice numbers intentionally retain S01's planned sequence; S09 is inserted as an authorization prerequisite for the page-aware remote API. Do not let a later-numbered UI slice bypass its data/security prerequisites.

## S00 and S01 implementation-gate crosswalk

| Evidence/requirement | Owner slice(s) | Gate |
|---|---|---|
| S00-F01 — unsynced work lost on process death | S03–S04 | Do not say saved locally until Room transaction commits; prove process-kill/reopen. |
| S00-F02 — possible duplicate eraser revision | S04 | Instrument callbacks/events and reproduce or falsify before persisting eraser outbox operations. |
| S00-F03 — tablet toolbar/status-bar overlap | S06–S07 | Verify insets and hit targets on phone, tablet, split-screen/foldable windows. |
| S00-F04 — Clear has no confirmation; runtime attempt inconclusive | S07 | Confirm/cancel and data-preservation tests; do not treat screenshot as proof. |
| S00-F05 — offline sync and data-loss risk | S03–S05 | Keep local data; verify retry/ack/conflict paths against a real migrated test database. |
| S00-F06 — injected stroke artifact; physical relevance unknown | S11–S12 | Compare software-injected, touch, and physical-stylus input; do not infer a stylus result from emulator injection. |
| S00-F07 — PostgreSQL integration/E2E blocked | S05 and S10 | Provision migrated disposable PostgreSQL and run integration/E2E; remains blocked until evidence exists. |
| S01 J01–J02 and Home/Library | S06, S14–S15 | Stable IDs, local-first data, create/resume/error paths; no duplicate identity on navigation. |
| S01 J03–J05 and continuous pages | S08, S10–S12 | Real page state, transform correctness, hit testing, gesture validation; no fake controls. |
| S01 J06 truthful save/offline/recovery | S03–S05, S07 | Local commit, outbox, ack, uncertainty, and conflict remain distinct. |
| S01 J07 revision-bound math feedback | S17–S18 | Separate analysis contract/service; sync acceptance is never a correctness verdict. |
| S01 J08 Trash/restore | S14–S16 | Stable identity, authorization, retention and destructive confirmation before purge. |

## Readiness for S03

**Conditionally ready for S03 planning; not ready for unrestricted S03 implementation.** Room/SQLite and the stable identity model are accepted provisionally. Before committing the S03 schema/dependency, resolve:

1. Product approval of A4 portrait as the initial profile (or choose another explicit profile) and canonical unit/origin.
2. AndroidX Ink 1.0.0 serializer proof for exact reopen/render preservation of stroke geometry and brush/input attributes; keep local format versioned and separate from the API DTO.
3. Backup/device-transfer and corruption policy, since current `allowBackup` rules do not explicitly scope future local document/outbox data.
4. Confirm S03 only imports live in-memory state best-effort; do not promise recovery of work lost before upgrade.
5. Keep S03 local-only if account/auth policy is not resolved. Do not broaden unauthenticated remote behavior.

After these decisions, S03 may implement the local repository and test it independently; S04 sync/outbox delivery, S05 remote verification, S09 authentication, and S10 page events remain gated by their dependencies. Do not mark S00 complete to start S03.

## Validation evidence and non-claims

- Read-only source review and five independent bounded research/review assignments informed ADR-003 through ADR-007.
- Tracked app/backend source was reported unchanged between the recorded S02 baseline and reviewed HEAD.
- This turn ran documentation checks only: whitespace (`git diff --check`) and local Markdown-link resolution.
- No Android/backend tests, PostgreSQL integration, API calls to the app backend, physical stylus test, or runtime UX test were run for this handoff.
- S00 remains partial; its existing historical test results remain exactly as recorded in the audit, with PostgreSQL/E2E, exact eraser tracing, and physical stylus verification still outstanding.
