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

The current analysis service is intentionally small: it accepts the request revision and returns an accepted result. This is a foundation for the upcoming stateful revision protocol rather than the final analysis behavior.

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

## Near-term roadmap

1. Introduce a stable `workSessionId` across Android and backend contracts.
2. Implement backend session state and accepted revision tracking.
3. Make incremental change application idempotent and detect revision gaps.
4. Reconstruct server-side ink state from add/remove/clear events.
5. Add handwriting and layout recognition.
6. Introduce a mathematical intermediate representation.
7. Add deterministic step verification, beginning with row operations.
8. Identify the first incorrect step.
9. Generate minimal hints, using an LLM only where it adds value.

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
