# Local full-stack development (Windows)

The Android client runs in Android Studio or on an emulator. Docker Compose manages FastAPI and PostgreSQL.

## One-time prerequisites

- Docker Desktop running with its Linux/WSL2 engine.
- Android Studio, JDK and Android SDK; an Android emulator configured.
- A local, **untracked** root `.env` file containing:

```dotenv
POSTGRES_PASSWORD=replace-with-a-private-development-password
```

Do not commit credentials. `.gitignore` excludes `.env`. For a shell environment variable instead, set `$env:POSTGRES_PASSWORD`.

## Start

From the repository root in PowerShell:

```powershell
.\scripts\start-dev.ps1
```

This checks Docker, builds and starts the existing Compose stack, waits for container health checks, runs `alembic upgrade head`, then checks the API health endpoint. The scripts do not install Docker or change Git branches.

Use Android Studio's **Run** button to debug on an emulator. Optionally, to install and launch the debug APK via Gradle and ADB after starting the services:

```powershell
.\scripts\start-dev.ps1 -RunAndroid
```

`-BackendOnly` can be used explicitly for backend/database startup (the default). The `-RunAndroid` option requires a running authorized emulator or device and ADB on PATH (or `ANDROID_HOME`). An Android Studio run configuration is still preferable for breakpoints.

- Host API: http://127.0.0.1:8080/health
- Swagger docs: http://127.0.0.1:8080/docs
- Android emulator API base URL: http://10.0.2.2:8080/
- PostgreSQL is reachable from the backend container as `db:5432`; no host DB port is published.

Android's debug manifest permits HTTP to the emulator's local development backend. This must not be used as a production networking policy. A physical tablet uses a different reachable server address and appropriate secure network setup.

## Stop and troubleshooting

```powershell
.\scripts\stop-dev.ps1
docker compose -f compose.yaml -f compose.postgres.yaml ps
docker compose -f compose.yaml -f compose.postgres.yaml logs --tail 100 backend db
```

`stop-dev.ps1` **does not** use `down -v`, so the PostgreSQL named volume survives restarting containers.

This setup uses the backend source copied into the image when built. Rerun `start-dev.ps1` to rebuild after switching branches or modifying backend files; it does not currently bind-mount live Python source. Changing Android files requires rebuilding the Android app, not the backend image.

An API `/health` response confirms the API process is reachable, not automatically that a sync transaction was successful. Validate database-backed sync via `POST /analysis/check` and integration tests. The startup migration step helps ensure schema availability.

The scripts are intended for local development only; Docker Desktop needs to be started beforehand. They have not been verified on every Windows and Android SDK configuration.
