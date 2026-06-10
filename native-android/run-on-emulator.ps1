$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$Emulator = Join-Path $Sdk "emulator\emulator.exe"
$Adb = Join-Path $Sdk "platform-tools\adb.exe"
$AvdName = "LuckyPick_Test"
$Apk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"

$AccelOutput = & $Emulator -accel-check 2>&1
$AccelText = $AccelOutput -join "`n"
Write-Host $AccelText

if ($LASTEXITCODE -ne 0) {
  Write-Host ""
  Write-Host "Emulator acceleration is not ready. If this says the hypervisor driver is not installed,"
  Write-Host "enable BIOS virtualization and run setup-emulator-acceleration.ps1 as Administrator."
  Write-Host ""
}

if (-not (Test-Path $Apk)) {
  & "C:\Users\USER\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat" -p $ProjectRoot assembleDebug
}

Start-Process -FilePath $Emulator -ArgumentList @("-avd", $AvdName, "-no-snapshot-load") -WindowStyle Normal

$deadline = (Get-Date).AddMinutes(4)
do {
  Start-Sleep -Seconds 5
  $devices = & $Adb devices | Select-String -Pattern "device$"
  if ($devices) {
    $state = (& $Adb shell getprop sys.boot_completed 2>$null) -join ""
  } else {
    $state = ""
  }
  Write-Host "Waiting for emulator... devices=$($devices.Count) boot=$state"
} until ($state -match "1" -or (Get-Date) -gt $deadline)

if ($state -notmatch "1") {
  throw "Emulator did not finish booting. Check that hypervisor acceleration is installed."
}

& $Adb install -r $Apk
& $Adb shell monkey -p com.luckypick.app -c android.intent.category.LAUNCHER 1
