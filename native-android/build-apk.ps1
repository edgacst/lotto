$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$WorkRoot = "C:\tmp\luckypick-native-build"
$Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$BuildTools = Join-Path $Sdk "build-tools\36.0.0"
$Platform = Join-Path $Sdk "platforms\android-36\android.jar"
$Java = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot\bin"

$SourceApp = Join-Path $ProjectRoot "app"
$BuildApp = Join-Path $WorkRoot "app"
$App = Join-Path $BuildApp "src\main"
$Out = Join-Path $WorkRoot "build"
$Gen = Join-Path $Out "gen"
$Classes = Join-Path $Out "classes"
$Dex = Join-Path $Out "dex"
$Unsigned = Join-Path $Out "luckypick-unsigned.apk"
$Aligned = Join-Path $Out "luckypick-aligned.apk"
$Signed = Join-Path $ProjectRoot "build\luckypick-debug.apk"
$KeyStore = Join-Path $Out "debug.keystore"

if (Test-Path $WorkRoot) {
  Remove-Item -LiteralPath $WorkRoot -Recurse -Force
}

New-Item -ItemType Directory -Force $WorkRoot, $Out, $Gen, $Classes, $Dex, (Join-Path $ProjectRoot "build") | Out-Null
Copy-Item -LiteralPath $SourceApp -Destination $WorkRoot -Recurse -Force

& "$BuildTools\aapt2.exe" compile --dir (Join-Path $App "res") -o (Join-Path $Out "resources.zip")
& "$BuildTools\aapt2.exe" link `
  -o $Unsigned `
  -I $Platform `
  --manifest (Join-Path $App "AndroidManifest.xml") `
  --java $Gen `
  --min-sdk-version 23 `
  --target-sdk-version 36 `
  (Join-Path $Out "resources.zip")

$Sources = @()
$Sources += Get-ChildItem -Recurse -Filter *.java (Join-Path $App "java") | Select-Object -ExpandProperty FullName
$Sources += Get-ChildItem -Recurse -Filter *.java $Gen | Select-Object -ExpandProperty FullName

& "$Java\javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $Platform -d $Classes $Sources
& "$BuildTools\d8.bat" --lib $Platform --output $Dex (Get-ChildItem -Recurse -Filter *.class $Classes | Select-Object -ExpandProperty FullName)

Push-Location $Dex
& "$Java\jar.exe" uf $Unsigned classes.dex
Pop-Location

& "$BuildTools\zipalign.exe" -f -p 4 $Unsigned $Aligned

if (-not (Test-Path $KeyStore)) {
  & "$Java\keytool.exe" -genkeypair -v `
    -keystore $KeyStore `
    -storepass android `
    -alias androiddebugkey `
    -keypass android `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -dname "CN=Android Debug,O=Lucky Pick,C=KR"
}

& "$BuildTools\apksigner.bat" sign `
  --ks $KeyStore `
  --ks-pass pass:android `
  --key-pass pass:android `
  --out $Signed `
  $Aligned

& "$BuildTools\apksigner.bat" verify --verbose $Signed
Write-Host "Built $Signed"
