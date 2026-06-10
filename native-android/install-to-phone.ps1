$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$Apk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $Apk)) {
  & "C:\Users\USER\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat" -p $ProjectRoot assembleDebug
}

& $Adb start-server | Out-Null
$Devices = & $Adb devices | Select-String -Pattern "device$"

if (-not $Devices) {
  Write-Host "No Android device is connected."
  Write-Host "Enable Developer options > USB debugging, connect the phone, then accept the RSA prompt."
  & $Adb devices -l
  exit 1
}

& $Adb install -r $Apk
& $Adb shell monkey -p com.luckypick.app -c android.intent.category.LAUNCHER 1
