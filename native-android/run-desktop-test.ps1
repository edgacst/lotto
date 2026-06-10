$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Java = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot\bin"
$Source = Join-Path $ProjectRoot "desktop-test\src\LuckyPickDesktop.java"
$Build = Join-Path $ProjectRoot "desktop-test\build"
$Jar = Join-Path $Build "luckypick-desktop-test.jar"

New-Item -ItemType Directory -Force $Build | Out-Null
& "$Java\javac.exe" -encoding UTF-8 -d $Build $Source
& "$Java\jar.exe" cfe $Jar LuckyPickDesktop -C $Build LuckyPickDesktop.class
Start-Process -FilePath "$Java\java.exe" -ArgumentList @("-jar", $Jar) -WorkingDirectory $Build -WindowStyle Normal

Write-Host "Started $Jar"
