#Requires -RunAsAdministrator
# Installs WSL2 + Docker Desktop on Windows. Reboot may be required.
# Usage (PowerShell as Administrator):
#   Set-ExecutionPolicy -Scope Process Bypass
#   .\install-docker-desktop.ps1

$ErrorActionPreference = "Stop"

Write-Host "=== 1) WSL2 ==="
wsl --install --no-distribution
if ($LASTEXITCODE -ne 0) {
  Write-Host "If WSL install failed, enable features manually and reboot:"
  Write-Host "  dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart"
  Write-Host "  dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart"
  Write-Host "  wsl --set-default-version 2"
}

Write-Host "=== 2) Docker Desktop (winget) ==="
winget install -e --id Docker.DockerDesktop --accept-package-agreements --accept-source-agreements

Write-Host ""
Write-Host "Next:"
Write-Host "  1. Reboot if Windows asked you to."
Write-Host "  2. Open Docker Desktop and wait until it says 'Engine running'."
Write-Host "  3. In PowerShell: docker version"
Write-Host "  4. Start GenieACS: docker compose -f docker-compose.genieacs.yml up -d"
