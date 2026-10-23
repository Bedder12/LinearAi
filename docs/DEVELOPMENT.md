# Development

## Prerequisites

### Android

- Android Studio
- Android SDK matching the project configuration
- JDK 17 or a compatible Android Studio bundled JDK for Gradle execution

### Backend

- Python 3.12+

## Backend environment

From the repository root:

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -e ".[dev]"
```

Run the API:

```powershell
uvicorn app.main:app --reload --port 8080
```

Run checks:

```powershell
ruff check .
pytest
```

Leave the environment:

```powershell
deactivate
```

## Android validation

From the repository root on Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

## Git workflow

Keep commits focused by concern. Examples:

```text
feat: add analysis service layer
test: add backend API contract coverage
fix: normalize backend import spacing
docs: improve repository documentation
```

Before committing:

```powershell
git status
```

For backend changes:

```powershell
cd backend
ruff check .
pytest
```

For Android changes:

```powershell
cd ..
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

Do not commit generated Python metadata such as `*.egg-info`, virtual environments, IDE state, local environment files, build output, or secrets.
