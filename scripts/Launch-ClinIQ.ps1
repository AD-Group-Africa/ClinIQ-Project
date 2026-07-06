#Requires -Version 5.1
<#
.SYNOPSIS
    ClinIQ Production Launcher - ASCII safe, PS 5.1 and 7+ compatible
.DESCRIPTION
    Builds Docker images, starts services, waits for health.
    No Unicode. No &&. No PS7-only syntax.
.PARAMETER ProjectRoot
    Path to ClinIQ project root. Default: parent of this script.
.PARAMETER SkipBuild
    Skip docker build, use existing images.
.PARAMETER Restart
    Stop all containers before starting.
.PARAMETER HealthTimeout
    Seconds to wait for backend health. Default: 150.
.EXAMPLE
    .\Launch-ClinIQ.ps1
    .\Launch-ClinIQ.ps1 -Restart
    .\Launch-ClinIQ.ps1 -SkipBuild -HealthTimeout 200
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot    = (Split-Path $PSScriptRoot -Parent),
    [switch]$SkipBuild,
    [switch]$Restart,
    [int]$HealthTimeout     = 150
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# -------------------------------------------------------
# Logging (ASCII only - safe on PS 5.1 CP1252 encoding)
# -------------------------------------------------------
function Write-Step  { param([string]$m) Write-Host "[>>>] $m" -ForegroundColor Cyan }
function Write-OK    { param([string]$m) Write-Host "[OK ] $m" -ForegroundColor Green }
function Write-Warn  { param([string]$m) Write-Host "[WRN] $m" -ForegroundColor Yellow }
function Write-Fail  { param([string]$m) Write-Host "[ERR] $m" -ForegroundColor Red }
function Write-Info  { param([string]$m) Write-Host "[   ] $m" }

function Write-Banner {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  ClinIQ Launcher" -ForegroundColor Cyan
    Write-Host "  Root : $ProjectRoot" -ForegroundColor Cyan
    Write-Host "  PS   : $($PSVersionTable.PSVersion)" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
}

# -------------------------------------------------------
# Detect docker compose command (v2 built-in vs v1 binary)
# -------------------------------------------------------
$Script:ComposeCmd   = $null
$Script:ComposeIsV2  = $false

function Initialize-DockerCompose {
    Write-Step "Detecting Docker Compose..."

    # Test docker compose (v2 - built into Docker Desktop)
    $null = docker compose version 2>&1
    if ($LASTEXITCODE -eq 0) {
        $Script:ComposeIsV2 = $true
        Write-OK "docker compose v2 detected"
        return
    }

    # Fallback: docker-compose (v1 standalone binary)
    $dcPath = Get-Command docker-compose -ErrorAction SilentlyContinue
    if ($null -ne $dcPath) {
        $Script:ComposeIsV2 = $false
        Write-OK "docker-compose v1 detected at $($dcPath.Source)"
        return
    }

    Write-Fail "Neither 'docker compose' nor 'docker-compose' found."
    Write-Fail "Install Docker Desktop: https://www.docker.com/products/docker-desktop"
    exit 1
}

function Invoke-Compose {
    param([string[]]$Arguments)
    if ($Script:ComposeIsV2) {
        docker compose @Arguments
    }
    else {
        docker-compose @Arguments
    }
}

# -------------------------------------------------------
# Resolve compose file
# -------------------------------------------------------
function Get-ComposeFile {
    $candidates = @(
        (Join-Path $ProjectRoot "docker-compose.yml"),
        (Join-Path $ProjectRoot "docker\docker-compose.yml"),
        (Join-Path $ProjectRoot "devops\docker-compose.yml")
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    Write-Fail "docker-compose.yml not found. Searched:"
    foreach ($c in $candidates) { Write-Fail "  $c" }
    exit 1
}

# -------------------------------------------------------
# Preflight checks
# -------------------------------------------------------
function Assert-Prerequisites {
    Write-Step "Checking prerequisites..."

    # Docker daemon
    $null = docker version 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "Docker is not running. Start Docker Desktop and retry."
        exit 1
    }
    Write-OK "Docker daemon is running"

    # Project root exists
    if (-not (Test-Path $ProjectRoot)) {
        Write-Fail "Project root not found: $ProjectRoot"
        exit 1
    }
    Write-OK "Project root exists: $ProjectRoot"
}

# -------------------------------------------------------
# Stop containers
# -------------------------------------------------------
function Stop-Services {
    param([string]$ComposeFile)
    Write-Step "Stopping existing containers..."
    Invoke-Compose @("-f", $ComposeFile, "down", "--remove-orphans")
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "docker compose down returned exit code $LASTEXITCODE (may be harmless)"
    }
    else {
        Write-OK "Containers stopped"
    }
    Start-Sleep -Seconds 3
}

# -------------------------------------------------------
# Build images
# -------------------------------------------------------
function Build-Images {
    param([string]$ComposeFile)
    Write-Step "Building Docker images (this takes 2-5 min first time)..."
    Invoke-Compose @("-f", $ComposeFile, "build", "--no-cache", "backend")
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "Docker build failed (exit $LASTEXITCODE)"
        Write-Fail "Run: docker compose -f $ComposeFile logs"
        exit 1
    }
    Write-OK "Docker images built"
}

# -------------------------------------------------------
# Start infrastructure (db + redis first)
# -------------------------------------------------------
function Start-Infrastructure {
    param([string]$ComposeFile)

    Write-Step "Starting database and Redis..."
    Invoke-Compose @("-f", $ComposeFile, "up", "-d", "db", "redis")
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "Failed to start db/redis (exit $LASTEXITCODE)"
        exit 1
    }

    Write-Step "Waiting for PostgreSQL to be ready (up to 60s)..."
    $waited = 0
    $ready  = $false
    while ($waited -lt 60) {
        Start-Sleep -Seconds 5
        $waited += 5
        $state = docker inspect cliniq_db --format "{{.State.Health.Status}}" 2>&1
        if ($state -eq "healthy") {
            $ready = $true
            break
        }
        Write-Info "  PostgreSQL status: $state ($waited/60s)"
    }

    if (-not $ready) {
        Write-Fail "PostgreSQL did not become healthy within 60 seconds"
        Invoke-Compose @("-f", $ComposeFile, "logs", "db")
        exit 1
    }
    Write-OK "PostgreSQL is healthy"
}

# -------------------------------------------------------
# Start backend
# -------------------------------------------------------
function Start-Backend {
    param([string]$ComposeFile)
    Write-Step "Starting Spring Boot backend..."
    Invoke-Compose @("-f", $ComposeFile, "up", "-d", "backend")
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "Failed to start backend (exit $LASTEXITCODE)"
        exit 1
    }
    Write-OK "Backend container started"
}

# -------------------------------------------------------
# Wait for backend health endpoint
# -------------------------------------------------------
function Wait-ForBackend {
    param([int]$TimeoutSec)
    Write-Step "Waiting for backend health check (up to ${TimeoutSec}s)..."

    $waited  = 0
    $healthy = $false
    $url     = "http://localhost:8080/actuator/health"

    while ($waited -lt $TimeoutSec) {
        Start-Sleep -Seconds 10
        $waited += 10
        try {
            $response = Invoke-WebRequest `
                -Uri            $url `
                -UseBasicParsing `
                -TimeoutSec     5 `
                -ErrorAction    SilentlyContinue
            if ($response.StatusCode -eq 200) {
                $healthy = $true
                break
            }
        }
        catch {
            # Not ready yet - keep waiting
        }
        Write-Info "  Waiting... ($waited / ${TimeoutSec}s)"
    }

    if (-not $healthy) {
        Write-Fail "Backend did not become healthy within ${TimeoutSec}s"
        Write-Fail "Printing backend logs (last 60 lines):"
        docker logs cliniq_backend --tail 60
        exit 1
    }
    Write-OK "Backend is healthy at $url"
}

# -------------------------------------------------------
# Final summary
# -------------------------------------------------------
function Show-Summary {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  ClinIQ is running" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  Backend  : http://localhost:8080"
    Write-Host "  Health   : http://localhost:8080/actuator/health"
    Write-Host "  Swagger  : http://localhost:8080/swagger-ui.html"
    Write-Host "  DB port  : localhost:5432"
    Write-Host "  Redis    : localhost:6379"
    Write-Host ""
    Write-Host "  Run tests:"
    Write-Host "    .\scripts\Test-ClinIQ.ps1"
    Write-Host ""
    Write-Host "  View logs:"
    Write-Host "    docker logs cliniq_backend -f"
    Write-Host ""
    Write-Host "  Stop:"
    Write-Host "    docker compose down"
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
}

# -------------------------------------------------------
# MAIN
# -------------------------------------------------------
try {
    Write-Banner
    Assert-Prerequisites
    Initialize-DockerCompose

    $composeFile = Get-ComposeFile
    Write-Info "Compose file : $composeFile"

    if ($Restart) {
        Stop-Services -ComposeFile $composeFile
    }

    if (-not $SkipBuild) {
        Build-Images -ComposeFile $composeFile
    }
    else {
        Write-Warn "Skipping build (-SkipBuild). Using existing images."
    }

    Start-Infrastructure -ComposeFile $composeFile
    Start-Backend        -ComposeFile $composeFile
    Wait-ForBackend      -TimeoutSec  $HealthTimeout
    Show-Summary
}
catch {
    Write-Host ""
    Write-Fail "FATAL: $_"
    Write-Fail "At: $($_.InvocationInfo.PositionMessage)"
    exit 1
}
