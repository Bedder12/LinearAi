[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $root
try {
    & docker compose -f compose.yaml -f compose.postgres.yaml down
    if ($LASTEXITCODE -ne 0) { throw "Could not stop development containers." }
    Write-Host "Development containers stopped. PostgreSQL volume preserved."
}
finally {
    Pop-Location
}
