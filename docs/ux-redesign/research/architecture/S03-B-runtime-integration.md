# S03-B — Runtime integration and process-death recovery

**Status:** Implemented and validated for the single-page Android workspace
**Branch:** `feat/s03b-runtime-persistence`
**Base:** `c98496b82f5cd631e314b552ca2a1fa44a6cd3a5` (main after PR #21)
**Scope:** Connect the Room-backed repository to the live workspace. No backend, API, database schema, coordinate, or synchronization-dispatch changes.

## Runtime behavior

- `WorkspaceRepository` is the source of truth for document content, stable document/page/work-session IDs, revision, and the local outbox. The Activity creates the application-scoped database/repository and injects it through a ViewModel factory; Compose has no Room construction or entity dependencies.
- The ViewModel loads asynchronously and exposes `Loading`, `Ready`, `Saving`, and `LocalSaveError`. It displays only the last committed snapshot. A save is called local only after its repository transaction returns; failures retain the previous snapshot and pending edit for an explicit retry. A load failure does not create an empty fallback.
- Add, erase, and clear callbacks enter one FIFO edit queue. New strokes are assigned stable IDs before queuing so retry cannot change their identity. Repository mutations commit before the next queued operation starts.
- The existing one-page, raw InkSurface coordinate model is unchanged (`legacy-raw-canvas-v1`, `canvas-px`); no A4 conversion, pan/zoom, or page-aware API was added.
- Remote sync remains unavailable. The UI can show the count of committed pending batches, but does not regenerate requests, send them, acknowledge them, or treat `/analysis/check` as mathematical feedback. Input and clear are disabled after a local-save failure until retry.

## Process-death proof

On the isolated Pixel Tablet API 35 emulator (`emulator-5556`), the process-death test was run in two separate instrumentation invocations:

1. `RuntimeProcessDeathTest#test01SeedWorkspaceForForceStop` passed after drawing and committing a stroke. It stored the document, page, work-session, revision, stroke-ID, and ordered outbox fingerprints in test preferences.
2. The target app process was observed at PID `5663`, force-stopped, verified absent, and relaunched through its launcher activity at PID `5721`.
3. `RuntimeProcessDeathTest#test02VerifyWorkspaceAfterForceStopAndRelaunch` passed in a new instrumentation invocation. It verified all stored IDs, revision, ordered outbox fingerprint, and visible rendered ink.

This proves persistence across a force-stop/relaunch after the stroke's local transaction committed. It does not claim that an edit still in progress at process death has committed.

## Validation evidence and limitations

- `:app:assembleDebug` — passed.
- `:app:testDebugUnitTest` — **32 JVM tests passed, 0 failures/errors/skips**, including five ViewModel lifecycle/queue/retry tests. The final aggregate invocation reported this task up-to-date; the XML test results were inspected.
- `:app:assembleDebugAndroidTest` — passed.
- `:app:lintDebug` — passed.
- Full API 35 instrumentation suite, invoked directly with `am instrument` on the isolated emulator — **31 tests passed, 0 failures**. This includes all three runtime UI integration tests, all 17 `RoomWorkspaceRepositoryTest` cases, both `RuntimeProcessDeathTest` methods, and `InkStorageRoundTripSpikeTest`.
- The isolated seed/force-stop/relaunch/verify sequence above passed as separate invocations and confirmed the app PID changed.
- `:app:pixelApi35DebugAndroidTest` could not start its managed Pixel 5 emulator in this environment (`EmulatorStartFailedException`; emulator logged an invalid `auto-no-window` GPU option). Its generated HTML summary reports **0 tests**; it is not evidence of a test pass. The 31-test result came from direct instrumentation on the already-running isolated API 35 emulator, not that managed task.
- Direct `am instrument` output does not create Gradle XML reports. The Gradle managed-device report was inspected and contains no executed tests. No XML test count is claimed for the direct run.
- During full-suite validation, an existing rollback test was updated to expect the repository's typed `WorkspacePersistenceException` and assert its SQLite cause. The UI integration test now waits for the final pending-outbox status before closing its test database, avoiding a teardown race with the asynchronous status read.

## Decisions still open

- Product/privacy approval is still required for Android Auto Backup and device-transfer inclusion/exclusion. This implementation changes neither manifest nor backup rules.
- Restored/stale outbox reconciliation, sync dispatch/acknowledgment, corruption recovery UX, and user-directed data export/recovery remain future work.
- The original in-memory session cannot be reconstructed if it was already lost before this runtime integration.
- S03 remains in progress; S03-B process-death validation does not complete later implementation slices or S00's PostgreSQL, physical stylus, and end-to-end gates.
