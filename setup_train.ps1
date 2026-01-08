# SQUID Model Training - Setup and Train Script (NO VENV VERSION)
# This version installs and runs everything using the system Python installation.

param(
    [switch]$QuickTest,
    [int]$NumSamples = 10000,
    [int]$Epochs = 1000
)

Write-Host "=" -ForegroundColor Cyan -NoNewline
Write-Host ("=" * 78) -ForegroundColor Cyan
Write-Host "SQUID Model Training - Global Python Setup" -ForegroundColor Cyan
Write-Host "=" -ForegroundColor Cyan -NoNewline
Write-Host ("=" * 78) -ForegroundColor Cyan
Write-Host ""

$ErrorActionPreference = "Stop"
$pythonIaPath = "C:\Users\Gamer\Desktop\SQUID\python-ia"

# Change working directory
Set-Location $pythonIaPath
Write-Host "[INFO] Working directory: $pythonIaPath" -ForegroundColor Green

# Step 1: Check Python installation
Write-Host "`n[STEP 1] Checking Python installation..." -ForegroundColor Yellow
try {
    $pythonVersion = python --version 2>&1
    Write-Host "[OK] Python found: $pythonVersion" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Python not found! Please install Python 3.8 or higher." -ForegroundColor Red
    Write-Host "Download from: https://www.python.org/downloads/" -ForegroundColor Yellow
    exit 1
}

# Step 2: Upgrade pip
Write-Host "`n[STEP 2] Upgrading pip..." -ForegroundColor Yellow
python -m pip install --upgrade pip --quiet
Write-Host "[OK] pip upgraded." -ForegroundColor Green

# Step 3: Install dependencies
Write-Host "`n[STEP 3] Installing dependencies..." -ForegroundColor Yellow
try {
    pip install -r requirements.txt
    Write-Host "[OK] All dependencies installed successfully." -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Failed to install dependencies. Check requirements.txt" -ForegroundColor Red
    exit 1
}

# Step 4: Verify installation
Write-Host "`n[STEP 4] Verifying key packages..." -ForegroundColor Yellow
try {
    python -c "import torch, numpy, sklearn; print('PyTorch:', torch.__version__, '| NumPy:', numpy.__version__, '| scikit-learn:', sklearn.__version__)"
    python -c "from squid_model import SquidModel; print('SQUID modules OK')"
    Write-Host "[OK] All modules verified successfully." -ForegroundColor Green
} catch {
    Write-Host "[ERROR] One or more required modules failed to import!" -ForegroundColor Red
    exit 1
}

# Step 5: Prepare output directory
Write-Host "`n[STEP 5] Preparing models directory..." -ForegroundColor Yellow
if (-not (Test-Path "models")) {
    New-Item -ItemType Directory -Path "models" | Out-Null
    Write-Host "[OK] Models directory created." -ForegroundColor Green
} else {
    Write-Host "[INFO] Models directory already exists." -ForegroundColor Green
}

# Step 6: Train the model
Write-Host "`n[STEP 6] Starting model training..." -ForegroundColor Yellow
Write-Host ""

if ($QuickTest) {
    Write-Host "[INFO] Running quick test mode (1000 samples, 10 epochs)" -ForegroundColor Cyan
    python train.py --num-samples 1000 --epochs 10 --batch-size 32
} else {
    Write-Host "[INFO] Running standard training ($NumSamples samples, $Epochs epochs)" -ForegroundColor Cyan
    python train.py --num-samples $NumSamples --epochs $Epochs
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n" -NoNewline
    Write-Host "=" -ForegroundColor Green -NoNewline
    Write-Host ("=" * 78) -ForegroundColor Green
    Write-Host "Training completed successfully!" -ForegroundColor Green
    Write-Host "=" -ForegroundColor Green -NoNewline
    Write-Host ("=" * 78) -ForegroundColor Green
    Write-Host ""
    Write-Host "Trained model saved to: .\models\squid_model.pth" -ForegroundColor Cyan
    Write-Host "Training log saved to: .\training.log" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Yellow
    Write-Host "  1. Review training results in training.log" -ForegroundColor White
    Write-Host "  2. Test the model: python -m squid_model" -ForegroundColor White
    Write-Host "  3. Deploy the model: Copy-Item .\models\squid_model.pth ..\models\" -ForegroundColor White
    Write-Host "  4. Start the service: python app.py" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host "`n[ERROR] Training failed! Check training.log for details." -ForegroundColor Red
    exit 1
}
