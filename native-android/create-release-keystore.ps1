$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Java = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot\bin"
$KeystoreDir = Join-Path $ProjectRoot "keystore"
$Keystore = Join-Path $KeystoreDir "luckypick-upload.jks"
$Properties = Join-Path $ProjectRoot "keystore.properties"

New-Item -ItemType Directory -Force $KeystoreDir | Out-Null

if (-not (Test-Path $Keystore)) {
  & "$Java\keytool.exe" -genkeypair -v `
    -keystore $Keystore `
    -storepass "change-this-password" `
    -alias "luckypick" `
    -keypass "change-this-password" `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -dname "CN=Lucky Pick,O=Lucky Pick,C=KR"
}

@"
storeFile=keystore/luckypick-upload.jks
storePassword=change-this-password
keyAlias=luckypick
keyPassword=change-this-password
"@ | Set-Content -Path $Properties -Encoding ASCII

Write-Host "Created $Keystore"
Write-Host "Created $Properties"
Write-Host "Change the keystore passwords before real production release."
