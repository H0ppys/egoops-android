# Build the collection app APK without Gradle or Android Studio.
# Toolchain lives in tools/ (jdk, build-tools, platform).
param(
    [switch]$NoCapture,          # 生成「不拍摄收集」版本
    [string]$Variant = ""        # 换名版本，例如 "采集水印相机"
)

$ErrorActionPreference = 'Stop'
$ROOT = Split-Path -Parent $PSScriptRoot
Set-Location $ROOT

$JDK  = Join-Path $ROOT 'tools\jdk\jdk-17.0.2'
$BT   = Join-Path $ROOT 'tools\build-tools\android-14'
$AJAR = Join-Path $ROOT 'tools\platform\android-34\android.jar'
$APP  = Join-Path $ROOT 'android'
$BUILD = Join-Path $APP 'build'

$env:JAVA_HOME = $JDK
$env:PATH = "$JDK\bin;$BT;$env:PATH"

Write-Host "== clean" -ForegroundColor Cyan
if (Test-Path $BUILD) { Remove-Item $BUILD -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$BUILD\gen", "$BUILD\classes", "$BUILD\dex", "$BUILD\res" | Out-Null

Write-Host "== aapt2 compile" -ForegroundColor Cyan
& "$BT\aapt2.exe" compile --dir "$APP\res" -o "$BUILD\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "== aapt2 link" -ForegroundColor Cyan
Add-Type -AssemblyName System.IO.Compression.FileSystem
$flatDir = Join-Path $BUILD 'flat'
if (Test-Path $flatDir) { Remove-Item $flatDir -Recurse -Force }
[System.IO.Compression.ZipFile]::ExtractToDirectory("$BUILD\res.zip", $flatDir)
$flats = Get-ChildItem $flatDir -Recurse -Filter *.flat | ForEach-Object { $_.FullName }
$manifest = "$APP\AndroidManifest.xml"
$label = ''
$pkgSuffix = ''
if ($NoCapture) { $label = '不拍摄收集'; $pkgSuffix = 'nocap' }
elseif ($Variant -ne '') { $label = $Variant; $pkgSuffix = 'wm' }

if ($label -ne '') {
    $m = Get-Content $manifest -Raw -Encoding UTF8
    $m = $m -replace 'android:label="@string/app_name"', ('android:label="' + $label + '"')
    $manifest = "$BUILD\AndroidManifest.xml"
    [System.IO.File]::WriteAllText($manifest, $m, (New-Object System.Text.UTF8Encoding($false)))
}
$rename = @()
if ($pkgSuffix -ne '') { $rename = @('--rename-manifest-package', ('com.egoops.collect.' + $pkgSuffix)) }
& "$BT\aapt2.exe" link -o "$BUILD\base.apk" -I $AJAR --manifest "$manifest" --java "$BUILD\gen" @rename --min-sdk-version 21 --target-sdk-version 34 --version-code 74 --version-name 74.0 $flats
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "== javac" -ForegroundColor Cyan
$sources = @()
$sources += Get-ChildItem "$BUILD\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
# 版本号同步到 Java 常量（App 内检查更新用）
$mf = Get-Content "$APP\AndroidManifest.xml" -Raw -Encoding UTF8
$vc = [regex]::Match($mf, 'android:versionCode="(\d+)"').Groups[1].Value
$vn = [regex]::Match($mf, 'android:versionName="([^"]+)"').Groups[1].Value
$vcFile = "$APP\src\com\egoops\collect\BuildConfigVersion.java"
$vcBody = @"
package com.egoops.collect;

/** 当前 APK 的版本号（构建时自动生成，勿手改） */
public class BuildConfigVersion {
    public static final int CODE = $vc;
    public static final String NAME = "$vn";
    public static final boolean NO_CAPTURE = $($NoCapture.ToString().ToLower());
}
"@
[System.IO.File]::WriteAllText($vcFile, $vcBody, (New-Object System.Text.UTF8Encoding($false)))

$sources += Get-ChildItem "$APP\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& "$JDK\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -nowarn -classpath $AJAR -d "$BUILD\classes" $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "== d8" -ForegroundColor Cyan
$classes = Get-ChildItem "$BUILD\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& "$BT\d8.bat" --lib $AJAR --min-api 21 --output "$BUILD\dex" $classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "== package classes.dex" -ForegroundColor Cyan
Copy-Item "$BUILD\base.apk" "$BUILD\app-unaligned.apk" -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$BUILD\app-unaligned.apk", 'Update')
$entry = $zip.GetEntry('classes.dex')
if ($entry) { $entry.Delete() }
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$BUILD\dex\classes.dex", 'classes.dex') | Out-Null
$zip.Dispose()

Write-Host "== zipalign" -ForegroundColor Cyan
& "$BT\zipalign.exe" -f 4 "$BUILD\app-unaligned.apk" "$BUILD\app-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "== sign" -ForegroundColor Cyan
$ks = Join-Path $APP 'debug.keystore'
if (-not (Test-Path $ks)) {
    & "$JDK\bin\keytool.exe" -genkeypair -keystore $ks -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}
if ($NoCapture) {
    $outApk = Join-Path $APP 'egoops-nocap-v74.apk'
} elseif ($Variant -ne '') {
    $outApk = Join-Path $APP 'egoops-wm-v74.apk'
} else {
    $outApk = Join-Path $APP 'egoops-collect-v74.apk'
}
& "$BT\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out $outApk "$BUILD\app-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& "$BT\apksigner.bat" verify --print-certs $outApk | Select-Object -First 3

$f = Get-Item $outApk
Write-Host ("`nBUILD OK: " + $f.FullName + " (" + [math]::Round($f.Length/1KB) + " KB)") -ForegroundColor Green
