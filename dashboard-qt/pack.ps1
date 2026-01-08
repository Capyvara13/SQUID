# Packaging helper for SQUID Qt dashboard (Windows PowerShell)
# This script creates a single-file EXE using PyInstaller.

# Usage: Open PowerShell (x64) and run this script from the project root:
# .\dashboard-qt\pack.ps1

$ErrorActionPreference = 'Stop'

Write-Host "Installing PyInstaller..."
pip install pyinstaller --upgrade
pip install -r requirements.txt

Write-Host "Converting icon (if present) to .ico..."
# Run icon conversion script if present
if (Test-Path "convert_icon.py") {
	python convert_icon.py
} else {
	Write-Host "convert_icon.py not found; skipping icon conversion"
}

Write-Host "Building executable with PyInstaller..."
# Use icon.ico if produced
if (Test-Path "icon.ico") {
	pyinstaller --noconfirm --onefile --name squid-dashboard --icon icon.ico main.py
} else {
	pyinstaller --noconfirm --onefile --name squid-dashboard main.py
}

Write-Host "Build complete. EXE is in dashboard-qt\dist\squid-dashboard.exe"
