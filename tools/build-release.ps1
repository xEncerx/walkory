<#
.EXAMPLE
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\build-release.ps1
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$signingProperties = Join-Path $PSScriptRoot 'signing\release.properties'
$apkPath = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'

if (-not (Test-Path -LiteralPath $signingProperties -PathType Leaf)) {
    throw 'Missing tools/signing/release.properties. Copy tools/release-signing.properties.example there, create a keystore, and fill in the signing values.'
}

Push-Location $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') :app:assembleRelease --no-configuration-cache --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Release build failed (Gradle exit code $LASTEXITCODE). No release APK is being reported as ready."
    }
    if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
        throw 'Gradle completed but the expected release APK was not found.'
    }
    Write-Output "Signed release APK: $apkPath"
}
finally {
    Pop-Location
}
