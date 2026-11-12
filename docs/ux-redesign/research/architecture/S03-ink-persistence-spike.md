# S03 — AndroidX Ink persistence feasibility spike

**Status:** Test authored, NOT executed. Results pending Android instrumented run.
**Branch:** `spike/s03-ink-persistence-roundtrip`
**Baseline:** `f7067b46075c2934cf9baa5d9f39346f8d15b1b9` (merged S02 PR #17)
**Scope:** Test-only investigation; no production persistence, ViewModel, backend, database, or UI change.

## Purpose

Before adding Room, confirm that AndroidX Ink 1.0.0 can write a representative stylus stroke to local disk and reconstruct its input geometry and brush metadata.

The existing `StrokePayloadMapper` is only a network projection (x/y/time, tool type); it does not serialize full brush and pressure/tilt/orientation metadata. Do not reuse that projection as the canonical local ink persistence format.

## Changes made

- `app/build.gradle.kts`: add `androidx.ink:ink-storage:1.0.0` to **androidTestImplementation** only.
- `InkStorageRoundTripSpikeTest.kt`: build a three-point stylus stroke with pressure, tilt, orientation and timestamps; encode input batch with the 1.0.0 `OutputStream` API; write a test-only versioned envelope with stable stroke ID and stock pressure-pen brush fields to app cache; read it back; decode the input batch; recreate the Stroke; compare geometry, timing, pressure, tilt, orientation, brush properties and ID within declared tolerances; delete temporary file.

This test is a **feasibility check**, not the approved serializer or a claim of lossless/pixel-exact rendering. The current test does not exercise Room, separate process restarts, migration, corrupt input, brush-family extensibility, stylus hardware, or a visual renderer comparison.

## How to verify locally

On a machine with Android SDK, Gradle and a suitable emulator/device:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat pixelApi35DebugAndroidTest
```

The second command is a configured managed-device task in this repository. Its actual availability depends on local SDK/emulator configuration. To run only the spike with a connected device, use:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.ink.InkStorageRoundTripSpikeTest
```

Record command, environment, exit code, test XML, and any compiler/runtime exception. A passing Android test indicates the tested sample round-tripped within its chosen tolerances; it is not proof that every future brush/stroke or byte representation is lossless.

## Follow-up validation matrix

| Test | Success criterion | Status |
|---|---|---|
| Three-point stylus round-trip | Stable ID, stock brush fields, point count/type/timing and optional attributes within declared tolerances | NOT RUN |
| Finger input without optional attributes | Existing supported touch mode recreates correctly | NOT ADDED |
| More realistic pressure/tilt curves | Fidelity and rendering compared on instrumented device | NOT ADDED |
| Multiple stock brush families/colors | Explicit family registry; no default silent substitution | NOT ADDED |
| Corrupt/truncated versioned envelope | Reject without discarding existing document | NOT ADDED |
| Process-death reopen of committed stroke | Same document and stroke IDs restored | NOT ADDED (S03 Room integration) |
| Atomic document+revision+outbox writes | All commit or all roll back | NOT ADDED (S03 Room integration) |
| Snapshot/export performance | Measure memory/time/bytes on realistic pages | NOT ADDED |

## Open issues before production approval

1. The compressed Ink input codec may be quantized. Define an error budget and compare resulting rendered paths visually; do not call it lossless without evidence.
2. Define a versioned **local** stroke record including original stable stroke ID, all needed Ink stroke/brush fields, family identifier, encoding metadata, and restore failures. Keep it separate from the v1/v2 network protocol.
3. Choose the initial A4 page profile and coordinate units as an explicit product decision; the spike tests sample geometry only.
4. Decide Android backup/device transfer, corruption quarantine, and schema migration policy before adding Room to production.
5. Preserve S00's open eraser-event trace and PostgreSQL E2E blocker; this spike does not resolve them.

## References

- https://developer.android.com/develop/ui/compose/touch-input/stylus-input/ink-api-state-preservation
- https://developer.android.com/reference/kotlin/androidx/ink/storage/package-summary
- `docs/ux-redesign/research/architecture/ADR-002-local-storage-outbox.md`
- `docs/ux-redesign/research/architecture/ADR-004-page-coordinate-contract.md`
- `docs/ux-redesign/research/architecture/S02-implementation-handoff.md`
