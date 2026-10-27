param([switch]$Release)
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$toolRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.toolchain'))
if (!(Test-Path "$toolRoot/gradle-8.9/bin/gradle.bat")) {throw 'Gradle 8.9 is required in the workspace .toolchain directory.'}
$env:JAVA_HOME=(Get-ChildItem "$toolRoot/jdk" -Directory | Where-Object Name -Like 'jdk-17*' | Select-Object -First 1).FullName
if (!$env:JAVA_HOME) {throw 'JDK 17 is required in .toolchain/jdk.'}
$env:GRADLE_USER_HOME="$toolRoot/gradle-cache"
$env:ANDROID_HOME="$toolRoot/sdk"
$env:ANDROID_USER_HOME="$toolRoot/android-user"
New-Item -ItemType Directory -Force local,$env:ANDROID_USER_HOME | Out-Null
[IO.File]::WriteAllText("$PSScriptRoot/launcher/local.properties", 'sdk.dir='+$env:ANDROID_HOME.Replace('\','/'))
if (!(Test-Path local/personal.jks)) {
    & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore local/personal.jks -storepass android -keypass android -alias cosy -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Personal TV Home'
    if ($LASTEXITCODE -ne 0) {throw 'Could not create personal signing key'}
}
$variant=if($Release){'Release'}else{'Debug'}
& "$toolRoot/gradle-8.9/bin/gradle.bat" -I "$PSScriptRoot/tools/compiler-bridge.gradle" -PpersonalSandboxBuild -p "$PSScriptRoot/launcher" --no-daemon "assemble$variant" "test${variant}UnitTest" "lint$variant" --console=plain
if ($LASTEXITCODE -ne 0) {throw 'Build or tests failed'}
Write-Output 'Build complete. Nothing was installed, launched, granted, disabled or changed on the TV.'
