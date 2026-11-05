[CmdletBinding()]
param(
    [switch]$RunAndroid,
    [switch]$BackendOnly
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($RunAndroid -and $BackendOnly) {
    throw "Choose either -RunAndroid or -BackendOnly, not both."
}

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$composeFiles = @("-f", (Join-Path $root "compose.yaml"), "-f", (Join-Path $root "compose.postgres.yaml"))
$envFile = Join-Path $root ".env"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found. Install/start Docker Desktop and try again."
}
if (-not (Test-Path $envFile) -and -not $env:POSTGRES_PASSWORD) {
    throw "Set POSTGRES_PASSWORD in the environment or create a root .env file containing POSTGRES_PASSWORD=<local-dev-password>. Do not commit .env."
}

Push-Location $root
try {
    & docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Docker Engine is unavailable. Start Docker Desktop first." }

    & docker compose @composeFiles config --quiet
    if ($LASTEXITCODE -ne 0) { throw "Invalid Docker Compose configuration." }

    Write-Host "Starting FastAPI and PostgreSQL..."
    & docker compose @composeFiles up --build --wait -d
    if ($LASTEXITCODE -ne 0) { throw "Docker services failed to start. Inspect: docker compose -f compose.yaml -f compose.postgres.yaml logs" }

    Write-Host "Applying database migrations..."
    & docker compose @composeFiles exec -T backend alembic upgrade head
    if ($LASTEXITCODE -ne 0) { throw "Database migration failed. Inspect backend logs before using the app." }

    $health = Invoke-RestMethod -Uri "http://127.0.0.1:8080/health" -TimeoutSec 10
    if ($health.status -ne "ok") { throw "Unexpected API health response." }

    Write-Host "Development backend ready: http://127.0.0.1:8080/docs"
    Write-Host "Android emulator API address: http://10.0.2.2:8080/"
    Write-Host "Database stays in the named Docker volume when stopped."
    if (-not $RunAndroid) {
        Write-Host "Start the Android app using Android Studio's Run button, or pass -RunAndroid."
        return
    }

    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adb) {
        $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
        if ($sdk) {
            $adbPath = Join-Path $sdk "platform-tools\adb.exe"
            if (Test-Path $adbPath) { $adb = @{ Source = $adbPath } }
        }
    }
    if (-not $adb) { throw "ADB not found. Add Android SDK platform-tools to PATH or set ANDROID_HOME. Backend is running." }

    $devices = @(& $adb.Source devices)
    if ($LASTEXITCODE -ne 0 -or -not ($devices | Where-Object { $_ -match '^\S+\s+device$' })) {
        throw "No authorized Android emulator/device found. Start an emulator in Android Studio. Backend is running."
    }

    Write-Host "Building/installing Android debug app..."
    & (Join-Path $root "gradlew.bat") :app:installDebug
    if ($LASTEXITCODE -ne 0) { throw "Android debug build/install failed. Backend is still running." }

    & $adb.Source shell am start -n "com.example.myapplication/.MainActivity"
    if ($LASTEXITCODE -ne 0) { throw "App installed but could not be launched. Backend is still running." }
    Write-Host "Android launched."
}
finally {
    Pop-Location
}
