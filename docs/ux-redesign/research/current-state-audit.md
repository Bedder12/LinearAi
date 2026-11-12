# S00 — Current-state audit

**Audit date:** 2026-11-12
**Repository revision:** `e9822cc8dc741fb63eec8df70703a523542d94e2`
**Branch:** `ui/slice-01-tool-selection`
**Status:** Partial; emulator workflows are now partially observed, while eraser-event tracing and PostgreSQL-backed integration verification remain outstanding.

This audit records the checked-out source and commands run for S00. It does not treat the plan's prior GitHub snapshot as the current implementation. The worktree also contains a pre-existing, zero-byte untracked file at `backend/app/services/ink_state`; it was left unchanged. No application code was changed for this audit.

## Current architecture observed

```text
Android MainActivity
  -> MathWorkspaceScreen
    -> MathWorkspaceViewModel (in-memory StudentWork and sync state)
      -> AnalysisRepository / SyncCoordinator
        -> Retrofit /analysis/check
          -> FastAPI route
            -> revision service
              -> PostgreSQL work-session and ordered ink-change records
```

- Android starts directly in one Compose workspace. The current UI has Pen, Eraser, Clear, Check, revision, and sync status controls. There is no observed navigation host, library, or multi-page model.
- `InkSurface` renders AndroidX Ink strokes with an identity transform. Pen input is handled by `InProgressStrokes`; the eraser hit-tests raw pointer coordinates. `InkInputPolicy` accepts both stylus and touch.
- `StudentWork` owns a `workSessionId`, one document, a revision counter, and pending events. The ViewModel is the only observed owner; no Room, DataStore, or other local persistence integration was found in the Android source/dependencies. App process death therefore remains a data-loss risk for unacknowledged work.
- The client retries transient failures up to three total attempts with bounded delays. On a revision gap it can verify overlapping history and resend a complete contiguous missing suffix once; it stops on conflict or incomplete history. These behaviors have Android unit-test coverage but have not been verified end to end against a running backend/database.
- `/analysis/check` validates and commits ink revision history through PostgreSQL transactions, then returns `acceptedRevision`. This is a synchronization acknowledgment, not mathematical checking or authenticated cloud backup.
- The current API schema supports `stroke_added`, `stroke_removed`, and `document_cleared`; it has no page identifier or page operation.
- The inspected analysis route has no authentication/authorization dependency. A `workSessionId` must not be treated as an access-control mechanism.

Relevant sources: [MainActivity.kt](../../../app/src/main/java/com/example/myapplication/MainActivity.kt), [MathWorkspaceScreen.kt](../../../app/src/main/java/com/example/myapplication/MathWorkspaceScreen.kt), [MathWorkspaceViewModel.kt](../../../app/src/main/java/com/example/myapplication/ui/workspace/MathWorkspaceViewModel.kt), [StudentWork.kt](../../../app/src/main/java/com/example/myapplication/domain/StudentWork.kt), [InkSurface.kt](../../../app/src/main/java/com/example/myapplication/ink/InkSurface.kt), [SyncCoordinator.kt](../../../app/src/main/java/com/example/myapplication/analysis/sync/SyncCoordinator.kt), [analysis.py](../../../backend/app/schemas/analysis.py), [revision_sync.py](../../../backend/app/services/revision_sync.py), [analysis.py](../../../backend/app/api/analysis.py).

## UI/workflow baseline matrix

The first-pass runtime walkthrough used a software-injected Android input swipe, not a physical finger or stylus. It verifies app response to the emulator's injected events only; real-hardware stylus behavior remains unverified. Screenshots are in [evidence/](evidence/).

| State or workflow | Observed behavior and source | Manual reproduction |
|---|---|---|
| Empty workspace | A new in-memory `StudentWork` starts at revision 0 with no strokes. [MathWorkspaceViewModel.kt](../../../app/src/main/java/com/example/myapplication/ui/workspace/MathWorkspaceViewModel.kt), [StudentWork.kt](../../../app/src/main/java/com/example/myapplication/domain/StudentWork.kt). Observed on emulator; See [s00-empty-workspace.png](evidence/s00-empty-workspace.png). | Launch the app and inspect the blank canvas and revision/status text. |
| Drawing | Pen is initially selected; a software-injected touch swipe produced an ink stroke and advanced the revision. A large triangular rendering artifact was visible on this injected stroke; treat it as unverified outside this input path. See [s00-drawing-touch.png](evidence/s00-drawing-touch.png). | On a stylus-capable device, draw with a stylus and finger separately; compare shape and rendering. |
| Erasing | Selecting Eraser changed the selected-tool semantics. In one injected drawing/erase sequence the revision advanced from 2 to 4, while the stroke was no longer visible. This is **consistent with** multiple remove callbacks for one gesture, but the exact callback/event sequence and initial revision state were not instrumented. See [s00-erased-touch.png](evidence/s00-erased-touch.png). [InkSurface.kt](../../../app/src/main/java/com/example/myapplication/ink/InkSurface.kt) invokes removal while each pointer event is pressed; [StudentWork.kt](../../../app/src/main/java/com/example/myapplication/domain/StudentWork.kt) increments for each call. | Use a fresh workspace, create one identified stroke, erase it once, and inspect each callback and pending event before drawing any other stroke. |
| Clear | Source inspection confirms the control directly calls `viewModel.clear()` with no confirmation. The emulator attempt was inconclusive: [s00-cleared.png](evidence/s00-cleared.png) still shows a stroke at revision 1, so it does not prove that the tap cleared the document. Runtime success and empty-document behavior remain unverified. [MathWorkspaceScreen.kt](../../../app/src/main/java/com/example/myapplication/MathWorkspaceScreen.kt), [StudentWork.kt](../../../app/src/main/java/com/example/myapplication/domain/StudentWork.kt). | Repeat on an empty and non-empty document with a verified target tap; inspect feedback and reversibility. |
| Check with pending changes | With the API unreachable, the UI showed “Connection unavailable. Tap Check to retry.” while retaining the current revision. This message is visible in [s00-drawing-touch.png](evidence/s00-drawing-touch.png). The separate [s00-offline-check.png](evidence/s00-offline-check.png) capture shows “Saving changes...” and is only an in-flight sample, not a stable failure state. Check does not analyze mathematics. | Repeat with a reachable API and a PostgreSQL test database; verify committed response and UI state. |
| Check with no pending changes | The ViewModel changes local sync state to `Synced` without a request. [MathWorkspaceViewModel.kt](../../../app/src/main/java/com/example/myapplication/ui/workspace/MathWorkspaceViewModel.kt) | Press Check on a fresh workspace and verify no network request is needed. |
| Offline/transient failure | The coordinator retries the identical request up to three times; failure leaves the pending changes and exposes retry status. [SyncCoordinator.kt](../../../app/src/main/java/com/example/myapplication/analysis/sync/SyncCoordinator.kt), [SyncState.kt](../../../app/src/main/java/com/example/myapplication/ui/workspace/SyncState.kt) | Draw, disconnect the backend/network, press Check, and verify pending work remains. |
| Revision gap | The coordinator verifies overlapping revisions before sending a missing suffix; unresolved gaps stop and remain visible. [RevisionReconciliation.kt](../../../app/src/main/java/com/example/myapplication/analysis/sync/RevisionReconciliation.kt) | Force a server-ahead/behind revision scenario and verify the resulting sync state and retained ink. |
| Conflict/invalid request | The ViewModel preserves local data and disables further Check attempts for conflict/recovery/invalid states. [MathWorkspaceViewModel.kt](../../../app/src/main/java/com/example/myapplication/ui/workspace/MathWorkspaceViewModel.kt), [MathWorkspaceScreen.kt](../../../app/src/main/java/com/example/myapplication/MathWorkspaceScreen.kt) | Return a conflict or invalid-request response and verify the error message, disabled control, and retained document. |
| Process death/reopen | After creating unsynchronized work at revision 2, force-stop/relaunch displayed revision 0 and a blank canvas. This confirms loss for this in-memory workspace. See [s00-relaunch.png](evidence/s00-relaunch.png). | Repeat with distinct strokes and record document and pending revision state before and after force-stop. |
| Navigation/pages | One workspace is launched directly; page navigation and page-scoped events are absent. [MainActivity.kt](../../../app/src/main/java/com/example/myapplication/MainActivity.kt), [analysis.py](../../../backend/app/schemas/analysis.py) | Inspect app entry and verify there are no page/library navigation actions. |

## Validation evidence

Environment: Windows 10, current checkout above. Automated instrumented tests used the configured managed Pixel 5 / API 35 device. Runtime inspection used the `Medium_Tablet` Android emulator AVD, Android 15 / API 35, 2560×1600 px at 320 dpi (800×500 dp), target SDK 37. The app ran on the emulator with the API unreachable. No physical stylus was available. Backend tests ran in the existing `backend\.venv` with Python 3.14.6.

| Command | Result |
|---|---|
| `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug` | Exit 0. Build, JVM tests, and lint passed; 29 JVM tests passed. |
| `.\gradlew.bat pixelApi35DebugAndroidTest` | Exit 0. Six instrumented tests passed on the configured managed Pixel API 35 device. This is automated test evidence, not the requested manual workflow screenshots. |
| `backend\.venv\Scripts\python.exe -m ruff check .` (from `backend`) | Exit 0; all checks passed. |
| `backend\.venv\Scripts\python.exe -m pytest -q` (from `backend`) | Exit 0; 14 passed, 6 skipped. The PostgreSQL integration fixtures require `DATABASE_URL` and a migrated test database, so persistence integration was not exercised. One Starlette/httpx deprecation warning was emitted. |
| `backend\.venv\Scripts\python.exe -c "from app.main import app; print(app.title)"` (from `backend`) | Exit 0; printed `Linear AI API`. |

Generated test evidence is available locally at `app/build/test-results/testDebugUnitTest/` and `app/build/outputs/androidTest-results/managedDevice/debug/pixelApi35/`. Emulator screenshots are linked in the interaction matrix. The API status text and offline behavior were observed, but no successful API interaction or live PostgreSQL behavior was exercised.

## Finding register

| ID | Priority | Finding / evidence | Confidence | Follow-up |
|---|---|---|---|---|
| S00-F01 | P0 | Unsynchronized work was lost after force-stop/relaunch; [s00-relaunch.png](evidence/s00-relaunch.png). State is currently ViewModel-memory-only. | Confirmed for this run | Durable local journal before claiming local save; S03–S04. |
| S00-F02 | P0 | One injected eraser sequence advanced the revision from 2 to 4 while removing the stroke; [s00-erased-touch.png](evidence/s00-erased-touch.png). Exact duplicate event trace is not yet captured. Backend rules reject removal of an inactive stroke ID. | Likely; mechanism unconfirmed | Instrument per-callback revision and pending events, then add a separate repair with a domain regression test before broader ink UI work. Preserve server validation. |
| S00-F03 | P1 | Toolbar bounds begin at y=0 and overlap the status-bar region in the tablet emulator; taps near y=45 did not select Eraser, while a tap at y=75 did. UI hierarchy reported toolbar controls at y=0..96 px; display density was 320 dpi. | Confirmed on this AVD | Workspace-shell/inset follow-up; verify phone and additional window sizes before generalizing. |
| S00-F04 | P1 | Source shows Clear directly mutates work without a confirmation. The emulator capture still shows ink at revision 1, so successful runtime clearing was not established. | No confirmation: confirmed by source. Runtime clear result: unverified. | Reproduce using a verified control hit; define confirmation or safe undo before toolbar implementation; S07. |
| S00-F05 | P1 | Offline Check displayed an actionable connection error in [s00-drawing-touch.png](evidence/s00-drawing-touch.png). Unsynchronized work at revision 2 was absent after force-stop/relaunch to revision 0; see [s00-relaunch.png](evidence/s00-relaunch.png). | Confirmed for this run | Distinguish local durability from server acknowledgment; S03–S05. |
| S00-F06 | P2 | A software-injected swipe produced a large triangular artifact in a stroke; [s00-drawing-touch.png](evidence/s00-drawing-touch.png). | Observed; cause and physical-input relevance unverified | Compare real finger and stylus input; do not generalize from synthetic input. |
| S00-F07 | P0 | PostgreSQL integration tests and Android–FastAPI–PostgreSQL E2E could not run because Docker Desktop's Linux engine was unavailable. | Blocked by environment | Run with a migrated test database; no persistence/E2E pass claim. |

## Confirmed discrepancies and follow-up

- `README.md` and `docs/DATABASE.md` described PostgreSQL persistence and revision recovery as not implemented, despite the current tracked backend transaction/repository and Android retry/reconciliation code. Those statements have been updated to distinguish implemented code from missing end-to-end proof.
- The Android local pending journal is not durable; unsynchronized strokes can be lost when the process is killed.
- The backend integration tests were skipped without a configured PostgreSQL test database. A live database run is still needed before treating durability/concurrency as validated.
- The `Check` label can imply mathematical analysis, but the current endpoint only acknowledges ink revisions.
- Runtime observations now cover empty, injected-touch drawing/erasing, an inconclusive Clear attempt, offline Check, toolbar hit-testing, and force-stop/relaunch. Sync success, conflict/recovery, stylus behavior, and exact emitted eraser events remain unobserved.
- The synthetic triangle is based on injected input only and is not evidence of a physical stylus defect.
- The checkout is a feature branch, not the plan's referenced default-branch SHA. Rebase/current-main comparison is still needed before selecting dependent implementation slices.

## S00 completion status

Partial. Source inventory, documentation correction, Android build/unit/lint, managed-device tests, backend lint/tests, FastAPI import smoke, and a bounded emulator walkthrough are recorded. The acceptance criteria are not all met: exact eraser event tracing, successful synchronization/conflict workflows, physical stylus verification, and PostgreSQL-backed E2E remain outstanding. Keep S00 partial; S01 design work may proceed using this report's confirmed-versus-unverified distinctions, but must not treat the S00 blockers as resolved.
