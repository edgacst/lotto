$ErrorActionPreference = "Stop"

Write-Host "Enabling Windows emulator acceleration features..."

dism.exe /Online /Enable-Feature /FeatureName:HypervisorPlatform /All /NoRestart
dism.exe /Online /Enable-Feature /FeatureName:VirtualMachinePlatform /All /NoRestart

$driverDir = Join-Path $env:LOCALAPPDATA "Android\Sdk\extras\google\Android_Emulator_Hypervisor_Driver"
if (Test-Path (Join-Path $driverDir "silent_install.bat")) {
  Push-Location $driverDir
  cmd.exe /c silent_install.bat
  Pop-Location
}

bcdedit /set hypervisorlaunchtype auto

Write-Host ""
Write-Host "Done. Restart Windows, then run:"
Write-Host "  powershell.exe -ExecutionPolicy Bypass -File .\run-on-emulator.ps1"
