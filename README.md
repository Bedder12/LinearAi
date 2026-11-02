# Linear AI

AI-native handwriting analysis for step-by-step mathematics.

Linear AI is an Android-first mathematics workspace focused on a simple interaction: a student writes a multi-step solution naturally with a stylus, taps **Check**, and receives feedback on the **first incorrect mathematical step** rather than a full solution.

> **Project status:** active development. The current repository contains the Android ink pipeline, incremental transport contract, FastAPI backend foundation, service layer, tests, and CI. Recognition, mathematical intermediate representation, deterministic verification, and hint generation are the next major stages.

[![Android CI](https://github.com/Bedder12/LinearAi/actions/workflows/android.yml/badge.svg)](https://github.com/Bedder12/LinearAi/actions/workflows/android.yml)
[![Backend CI](https://github.com/Bedder12/LinearAi/actions/workflows/backend.yml/badge.svg)](https://github.com/Bedder12/LinearAi/actions/workflows/backend.yml)

## Product direction

The initial target is university STEM mathematics, especially linear algebra and Gaussian elimination.

The intended analysis pipeline is:

```text
Student writes with stylus
        ↓
Vector ink document
        ↓
Incremental changes
        ↓
FastAPI analysis service
        ↓
Handwriting / layout recognition
        ↓
Math IR
        ↓
Deterministic verification
        ↓
First incorrect step
        ↓
Minimal pedagogical hint
```

The project deliberately keeps vector ink as the source of truth. Raster images are intended as an optional fallback for ambiguous handwriting or layout, not as the canonical representation of student work.

## Repository structure

```text
LinearAi/
├── app/                         Android application
│   └── src/
│       ├── main/                Production Kotlin / Compose code
│       ├── test/                JVM unit tests
│       └── androidTest/         Device / instrumented tests
│
├── backend/                     FastAPI analysis backend
│   ├── app/
│   │   ├── api/                 HTTP routes
│   │   ├── domain/              Internal business models
│   │   ├── schemas/             Pydantic transport contracts
│   │   └── services/            Application orchestration
│   └── tests/
│       ├── api/                 API contract tests
│       └── services/            Service unit tests
│
├── docs/                        Architecture and development notes
├── .github/workflows/           Android and backend CI
└── gradle/                      Android build configuration
```

See [Architecture](docs/ARCHITECTURE.md) for the design boundaries and planned analysis pipeline.

## Android application

### Stack

- Kotlin
- Jetpack Compose
- AndroidX Ink
- Retrofit
- kotlinx.serialization
- Coroutines

### Current responsibilities

The Android client currently owns:

- stylus input and rendering
- local vector ink state
- stable stroke identifiers
- add/remove/clear change tracking
- monotonically increasing revisions
- pending unsynced changes
- backend transport mapping
- analysis API integration

The central local model is `StudentWork`:

```text
StudentWork
├── document
├── revision
└── pendingChanges
```

A successful backend acknowledgement removes only changes at or below the accepted revision, preserving edits made while a request was in flight.

## Backend

### Stack

- Python 3.12+
- FastAPI
- Pydantic
- pytest
- HTTPX
- Ruff

### Current layering

```text
HTTP request
    ↓
api/
    ↓
schemas/
    ↓
services/
    ↓
domain/
```

The API layer handles HTTP concerns. Business orchestration belongs in the service layer, while internal result models remain separate from external JSON contracts.

The current `/analysis/check` endpoint durably synchronizes ink revisions into PostgreSQL. Revision validation, retry comparison, stroke rules, and transaction orchestration are separated from SQLAlchemy persistence. It acknowledges committed changes; handwriting recognition and mathematical checking are not implemented yet.

## Backend quick start

From the repository root:

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8080
```

Useful endpoints:

- `GET /health`
- `POST /analysis/check`
- `GET /docs` for FastAPI's generated API documentation

Run backend validation locally:

```powershell
ruff check .
pytest
```

Deactivate the virtual environment with:

```powershell
deactivate
```

## Android quick start

Open the repository root in Android Studio.

For command-line validation on Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

The Android emulator reaches a backend running on the host machine through:

```text
http://10.0.2.2:8080/
```

A physical Android device requires the host machine's reachable LAN address and an appropriate development network configuration.

## Testing strategy

The project separates tests by responsibility:

- **Android JVM tests** for pure application and mapping logic
- **Android instrumented tests** for AndroidX Ink behavior that requires Android runtime/native components
- **Backend service tests** for business/application logic
- **Backend API tests** for FastAPI routing, validation, and response contracts

External AI providers will not be called directly from ordinary unit tests. Model integrations should use deterministic fakes/mocks in software tests and separate evaluation suites for model quality.

## CI

Two path-filtered GitHub Actions workflows keep Android and backend validation independent:

### Android CI

Runs when Android/build files change:

```text
assembleDebug
→ testDebugUnitTest
→ lintDebug
```

### Backend CI

Runs when backend files change:

```text
install
→ ruff check
→ pytest
→ FastAPI import smoke check
```

## Synchronization architecture and recovery plan

Linear AI uses a **hybrid consistency model**. Android is **local-first**: students can keep drawing without waiting for network round trips, so the local document can temporarily be ahead of the server. PostgreSQL owns the **authoritative committed revision history**, with atomic transactions and row locking to prevent gaps, conflicting retries, and partial writes. The goal is **eventual convergence** between Android and PostgreSQL when connectivity returns *and* the histories are compatible—not a claim of strong consistency across the network.

```text
Android local vector ink (StudentWork, pendingChanges)
          |
          | POST /analysis/check (workSessionId, revision, ordered changes)
          v
FastAPI (HTTP contract + RFC 9457 Problem Details)
          |
          v
Revision service (ordering, retries, stroke validation, transaction)
          |
          v
PostgreSQL (acceptedRevision + durable change history)
          |
          | 200 acceptedRevision / 409 revision-gap or revision-conflict
          v
Android (acknowledge confirmed changes or preserve pending work)
```

### Implemented foundation

- Stable `workSessionId`, monotonically increasing revisions, and ordered add/remove/clear events.
- Atomic PostgreSQL persistence; identical retries are checked against committed events, and missing or conflicting revisions are rejected.
- RFC 9457 `application/problem+json` responses for revision gaps, conflicts, and invalid requests. A revision gap includes the server's `acceptedRevision`.
- Android maps HTTP failures to typed synchronization results, displays sync state, prevents overlapping Check requests, and preserves pending changes when synchronization cannot be confirmed.
- Successful acknowledgment removes only pending changes covered by the sent request, retaining newer edits made during an in-flight request.

**Known limitations:** The Android pending queue currently lives in ViewModel memory, not durable local storage. A revision gap or conflicting history pauses synchronization; automatic reconciliation is not implemented. A UUID alone must not be treated as authorization to access another user's work session.

### Step 1 — Verify the full client-server flow

- Test on an Android emulator with FastAPI and PostgreSQL running: normal saves, identical retries, lost responses, missing revisions, conflicts, and edits made during a request.
- Verify API success/error schemas, HTTP status codes, server commits, and that local pending work is never silently discarded.
- Keep independent Android/backend CI plus PostgreSQL integration tests. CI passing is not a substitute for end-to-end verification.

### Step 2 — Implement safe revision reconciliation (current next milestone)

1. On `409 revision-gap`, read the server's `acceptedRevision` and inspect the **original local pending event history**.
2. Prove that every required revision from `acceptedRevision + 1` through the local request revision is present, ordered, and unchanged. Preserve all events belonging to each revision.
3. Only when continuity can be established, resend the complete missing range using an immutable request snapshot; the backend can already accept multiple contiguous revisions in one request.
4. Do not infer matching history merely from revision numbers. If required events are missing, or the server reports `revision-conflict`, **stop automatic synchronization and preserve local ink**.
5. For network timeouts and transient HTTP failures, retry the **same request** using bounded backoff; an HTTP response may have been lost after a successful commit. Never retry conflicts indefinitely or discard changes based on a failed response.
6. Keep one active synchronization attempt per work session and prevent an acknowledgment for an old snapshot from clearing edits created afterward.
7. Test recovery when the server is behind, an acknowledgment is lost, repeated requests arrive, history is incomplete, a conflict occurs, and new strokes are drawn during retry.

**Acceptance criteria:** Recoverable gaps converge without duplicate events or lost ink. Irreconcilable conflicts remain visible and blocked without overwriting either history. Server acceptance remains transactional, and client acknowledgments never exceed the verified request snapshot.

### Step 3 — Durable local persistence and conflict recovery

- Persist the work-session identity, vector document, revision sequence, and pending change journal locally (evaluate Room) so unsynced work survives process death and app restart.
- Define an authorized server-state read endpoint only when necessary for recovery from missing local history or conflicts. Compare authoritative history before allowing a reset, replay, or other explicit resolution.
- Establish authenticated work-session ownership and access checks **before** exposing saved sessions to multiple users.
- Add crash/restart, offline-to-online, repeated-retry, and conflicting-history tests.

### Later — Mathematical analysis pipeline

After synchronization is reliable, introduce handwriting/layout recognition, a structured Math IR, deterministic step verification (starting with Gaussian elimination), detection of the first incorrect step, and concise hint generation. Expensive analysis may run asynchronously after durable ink acceptance, without changing the meaning of the synchronization acknowledgment.

**Architecture constraint:** No Kafka, microservices, CQRS, distributed database, or Redis is justified solely by this synchronization plan. Add infrastructure only when measured requirements call for it.

## Near-term roadmap

1. [x] Stable work-session identity and revisioned ink changes.
2. [x] PostgreSQL-backed atomic revision persistence and idempotent retry detection.
3. [x] Structured API errors and Android-side failure classification.
4. [ ] End-to-end Android ↔ FastAPI ↔ PostgreSQL verification.
5. [ ] Safe revision-gap recovery and bounded transport retries (**Step 2**).
6. [ ] Durable offline pending changes, authenticated session ownership, and explicit conflict recovery.
7. [ ] Handwriting recognition, Math IR, and deterministic mathematical verification.
8. [ ] First-incorrect-step detection and minimal hint generation.

## Design principles

- Vector ink is the source of truth.
- Preserve raw strokes for replay and re-analysis.
- Prefer deterministic verification over LLM judgment when the mathematics is formally checkable.
- Keep HTTP contracts separate from internal domain models.
- Keep UI, transport, application logic, recognition, verification, and AI concerns separated.
- Build the smallest useful abstraction first; add infrastructure only when the product requires it.
- Optimize for reliable incremental analysis rather than repeatedly reprocessing the entire page.

## License

This project is licensed under the terms in [LICENSE](LICENSE).
