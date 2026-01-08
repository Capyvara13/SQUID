# SQUID Quick Start Script
# This script starts the Docker services and opens the enhanced dashboard

Write-Host "🦑 SQUID Enhanced Dashboard - Quick Start" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Check if Docker is running
Write-Host "Checking Docker..." -ForegroundColor Yellow
$dockerRunning = $false
try {
    $dockerInfo = docker info 2>&1
    if ($LASTEXITCODE -eq 0) {
        $dockerRunning = $true
        Write-Host "✓ Docker is running" -ForegroundColor Green
    } else {
        throw "Docker not running"
    }
}
catch {
    Write-Host "✗ Docker is not running" -ForegroundColor Red
}

if (-not $dockerRunning) {
    Write-Host ""
    Write-Host "Docker is not running. Please start Docker Desktop first." -ForegroundColor Red
    Write-Host "After starting Docker, run this script again." -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

# Check if services are already running
Write-Host ""
Write-Host "Checking for existing services..." -ForegroundColor Yellow
$existingContainers = docker ps --filter "name=squid" --format "{{.Names}}"

if ($existingContainers) {
    Write-Host "Found running SQUID containers:" -ForegroundColor Yellow
    $existingContainers | ForEach-Object { Write-Host "  - $_" -ForegroundColor Cyan }
    Write-Host ""
    $restart = Read-Host "Restart services? (y/n)"
    
    if ($restart -eq "y" -or $restart -eq "Y") {
        Write-Host "Stopping existing services..." -ForegroundColor Yellow
        docker-compose down
        Write-Host "✓ Services stopped" -ForegroundColor Green
    }
}

# Start services
Write-Host ""
Write-Host "Starting SQUID services..." -ForegroundColor Yellow
Write-Host "This may take 30-60 seconds..." -ForegroundColor Cyan
docker-compose up -d

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Services started successfully" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to start services" -ForegroundColor Red
    Write-Host "Please check the error messages above." -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

# Wait for services to be ready
Write-Host ""
Write-Host "Waiting for services to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Check Java Backend
Write-Host "Checking Java Backend (Port 8080)..." -ForegroundColor Yellow
$javaReady = $false
for ($i = 1; $i -le 6; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/health" -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            $javaReady = $true
            Write-Host "✓ Java Backend is ready" -ForegroundColor Green
            break
        }
    }
    catch {
        Write-Host "  Attempt $i/6 - Waiting..." -ForegroundColor Cyan
        Start-Sleep -Seconds 5
    }
}

if (-not $javaReady) {
    Write-Host "⚠ Java Backend is not responding yet. It may still be starting up." -ForegroundColor Yellow
}

# Check Python AI Service
Write-Host "Checking Python AI Service (Port 5000)..." -ForegroundColor Yellow
$pythonReady = $false
for ($i = 1; $i -le 6; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:5000/health" -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            $pythonReady = $true
            Write-Host "✓ Python AI Service is ready" -ForegroundColor Green
            break
        }
    }
    catch {
        Write-Host "  Attempt $i/6 - Waiting..." -ForegroundColor Cyan
        Start-Sleep -Seconds 5
    }
}

if (-not $pythonReady) {
    Write-Host "⚠ Python AI Service is not responding yet. It may still be starting up." -ForegroundColor Yellow
}

# Open enhanced dashboard
Write-Host ""
Write-Host "Opening Enhanced Dashboard..." -ForegroundColor Yellow
$dashboardPath = Join-Path $PSScriptRoot "enhanced-dashboard.html"

if (Test-Path $dashboardPath) {
    Start-Process $dashboardPath
    Write-Host "✓ Dashboard opened in your default browser" -ForegroundColor Green
} else {
    Write-Host "✗ Dashboard file not found at: $dashboardPath" -ForegroundColor Red
    Write-Host "Please make sure enhanced-dashboard.html exists in the SQUID directory." -ForegroundColor Yellow
}

# Summary
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "🦑 SQUID Services Status" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

if ($javaReady) {
    Write-Host "Java Backend:       ✓ READY" -ForegroundColor Green
} else {
    Write-Host "Java Backend:       ⚠ STARTING" -ForegroundColor Yellow
}

if ($pythonReady) {
    Write-Host "Python AI Service:  ✓ READY" -ForegroundColor Green
} else {
    Write-Host "Python AI Service:  ⚠ STARTING" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Dashboard:          http://localhost/enhanced-dashboard.html" -ForegroundColor Cyan
Write-Host "Java Backend:       http://localhost:8080/api/v1/health" -ForegroundColor Cyan
Write-Host "Python AI:          http://localhost:5000/health" -ForegroundColor Cyan
Write-Host ""

if (-not $javaReady -or -not $pythonReady) {
    Write-Host "⚠ Some services are still starting up." -ForegroundColor Yellow
    Write-Host "Wait a moment, then click 'Check Services' in the dashboard." -ForegroundColor Yellow
    Write-Host ""
}

Write-Host "To stop services, run:" -ForegroundColor Cyan
Write-Host "  docker-compose down" -ForegroundColor White
Write-Host ""
Write-Host "To view logs, run:" -ForegroundColor Cyan
Write-Host "  docker-compose logs -f" -ForegroundColor White
Write-Host ""
Write-Host "Press Enter to exit..." -ForegroundColor Gray
Read-Host
