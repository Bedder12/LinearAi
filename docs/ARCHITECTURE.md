# Architecture

This document describes the current architectural boundaries in Linear AI and the intended direction for the analysis pipeline.

## System overview

Linear AI is split into two native/application boundaries:

```text
Android client
    │
    │ JSON over HTTP
    ▼
FastAPI backend
```

The Android application owns pen interaction and local ink state. The backend owns analysis orchestration and will progressively take responsibility for server-side work-session state, recognition, mathematical interpretation, verification, and hint generation.

## Android architecture

The current Android flow is:

```text
Stylus / pointer input
        ↓
InkInputPolicy
        ↓
AndroidX Ink
        ↓
TrackedStroke
        ↓
InkDocument
        ↓
StudentWork
        ↓
Transport mappers
        ↓
AnalysisRepository
        ↓
AnalysisApi
```

### Domain state

`TrackedStroke` wraps an AndroidX Ink stroke with a stable identifier.

`InkDocument` represents the current local vector document.

`StudentWork` owns:

- the current document
- the current revision
- pending incremental changes

Supported change events are:

- stroke added
- stroke removed
- document cleared

A single logical revision may contain multiple changes. For example, several strokes completed together can share one revision.

### Synchronization rule

The Android client removes pending changes only when their revision is less than or equal to the backend's `acceptedRevision`.

This protects edits created while an earlier network request is still in flight.

## Backend architecture

Current backend layering:

```text
app/
├── api/
│   └── analysis.py
├── schemas/
│   └── analysis.py
├── services/
│   └── analysis_service.py
└── domain/
    └── analysis_result.py
```

### API layer

Responsibilities:

- define HTTP routes
- receive validated request schemas
- call application services
- map internal results to response schemas

The API layer should not contain mathematical verification or persistent state logic.

### Schema layer

Pydantic schemas define the public HTTP contract.

They intentionally use transport-oriented names such as `acceptedRevision` and `strokeId` to match the Android JSON contract.

### Service layer

`AnalysisService` is the application-level orchestrator.

Its responsibility will grow into:

```text
load work session
→ validate incremental revisions
→ apply ink changes
→ reconstruct current work
→ invoke recognition
→ build Math IR
→ invoke deterministic verifier
→ locate first incorrect step
→ request minimal hint when needed
→ produce AnalysisResult
```

The service should orchestrate these components rather than absorb all of their implementation details.

### Domain layer

Domain models represent internal concepts and should remain independent from HTTP/JSON details.

For example:

```text
AnalysisResult
    accepted_revision
    status
```

is intentionally separate from the external `AnalysisResponsePayload`.

## Next protocol milestone: work sessions

The current request identifies a revision but not the state stream to which that revision belongs.

The next contract change should introduce a stable work-session identity:

```json
{
  "workSessionId": "uuid",
  "revision": 7,
  "changes": []
}
```

The backend can then track:

```text
workSessionId
acceptedRevision
current strokes
```

This enables reliable retries and idempotent incremental synchronization.

### Revision rules

The intended rules are:

- an already accepted revision can be acknowledged again without reapplying changes
- a future revision with a missing predecessor is rejected as a revision gap
- all changes belonging to one revision are applied atomically as one logical unit
- stable stroke IDs prevent duplicate stroke creation
- `acceptedRevision` advances only after the revision has been successfully applied

Persistence should initially be hidden behind a repository boundary. An in-memory implementation is sufficient while the protocol is being designed and tested; a database or Redis should be introduced only when durability or distributed processing is required.

## Analysis pipeline

The intended analysis architecture is:

```text
Vector ink
    ↓
Recognition
    ↓
Math IR
    ↓
Deterministic verification
    ↓
First incorrect step
    ↓
Hint generation
```

### Recognition

Recognition converts handwriting and spatial layout into structured mathematical candidates.

A cropped raster representation may be used as an auxiliary signal when vector information alone is insufficient.

### Math IR

A mathematical intermediate representation separates recognition from verification.

The verifier should operate on structured mathematical meaning rather than raw pixels or free-form model text.

### Verification

For formally checkable operations such as Gaussian elimination, deterministic verification is preferred.

The verifier should eventually validate the exact transformation between consecutive student steps rather than merely testing whether two final expressions are mathematically equivalent.

### AI boundary

LLMs should be used where probabilistic reasoning is useful, for example:

- resolving ambiguous handwriting or layout
- converting verified error information into a concise pedagogical hint

They should not be the primary source of truth for deterministic mathematical correctness when explicit verification is possible.

## Testing boundaries

```text
Android domain / mapping
→ JVM unit tests

AndroidX Ink integration
→ instrumented tests

Backend services
→ unit tests

FastAPI routing + validation
→ API contract tests

Future model behavior
→ dedicated evals
```

This keeps software correctness testing deterministic while allowing AI quality to be measured separately.
