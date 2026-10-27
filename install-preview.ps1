param([Parameter(Mandatory=$true)][string]$TvHost,[switch]$DebugPreview,[string]$CandidateManifest)
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if ($TvHost -notmatch '^\d{1,3}(\.\d{1,3}){3}(:\d{1,5})?$') {throw 'Supply a local IPv4 address with an optional ADB port'}
if (!$TvHost.Contains(':')) {$TvHost += ':5555'}
$apk=if($DebugPreview){'launcher/app/build/outputs/apk/debug/app-debug.apk'}else{'launcher/app/build/outputs/apk/release/app-release.apk'}
if (!$CandidateManifest) {$CandidateManifest=if($DebugPreview){'local/candidate-debug.json'}else{'local/candidate.json'}}
$approved=Get-Content -LiteralPath $CandidateManifest -Raw | ConvertFrom-Json
if ($approved.sha256 -notmatch '^[a-fA-F0-9]{64}$' -or $approved.package -ne 'com.privs.tvhome') {throw 'Invalid candidate metadata'}
if ([IO.Path]::GetFullPath($approved.apk) -ne [IO.Path]::GetFullPath((Join-Path $PSScriptRoot $apk))) {throw 'Candidate metadata belongs to a different APK'}
New-Item -ItemType Directory -Force local/staging | Out-Null
$id=[Guid]::NewGuid().ToString('N')
$staged=Join-Path $PSScriptRoot "local/staging/$id.apk"
$pulled=Join-Path $PSScriptRoot "local/staging/$id-installed.apk"
$oldHome=$null
$attempted=$false
try {
    Copy-Item -LiteralPath $apk -Destination $staged
    $checkArgs=@('tools/verify-candidate.py','--apk',$staged,'--expected-sha256',$approved.sha256,'--artifact-only')
    if($DebugPreview){$checkArgs+='--allow-debug'}
    $previousErrorAction=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $verified=& python @checkArgs 2>&1
        $verificationExit=$LASTEXITCODE
    } finally {$ErrorActionPreference=$previousErrorAction}
    if($verificationExit -ne 0){throw ('Artifact verification failed before ADB: '+($verified -join "`n"))}
    $identity=$verified | ConvertFrom-Json
    if($identity.version_code -ne $approved.version_code -or $identity.version_name -ne $approved.version_name){throw 'Candidate version differs from approved metadata'}
    $currentHomeOutput=& adb -s $TvHost shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
    if($LASTEXITCODE -ne 0){throw 'Could not read current Home; no installation attempted'}
    $oldHome=($currentHomeOutput | Select-Object -Last 1).Trim()
    if($oldHome -notmatch '^[A-Za-z0-9_.]+/[A-Za-z0-9_.]+$'){throw 'Current Home is ambiguous; no installation attempted'}
    $currentHomeOutput | Set-Content local/home-before-preview.txt
    if((Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash -ne $approved.sha256){throw 'Staged APK changed'}
    $attempted=$true
    & adb -s $TvHost install -r $staged
    if($LASTEXITCODE -ne 0){throw 'Preview installation failed'}
    $paths=& adb -s $TvHost shell pm path com.privs.tvhome
    if($LASTEXITCODE -ne 0){throw 'Cannot read installed package path'}
    $base=$paths | Where-Object {$_ -match '^package:(/.+/base\.apk)\s*$'} | Select-Object -First 1
    if(!$base){throw 'Installed base APK not found'}
    $remote=($base -replace '^package:','').Trim()
    & adb -s $TvHost pull $remote $pulled
    if($LASTEXITCODE -ne 0 -or (Get-FileHash -LiteralPath $pulled -Algorithm SHA256).Hash -ne $approved.sha256){throw 'Installed APK differs from the verified candidate'}
    Write-Output "Installed and byte-verified $($identity.version_name)."
} finally {
    if($attempted -and $oldHome){
        $after=& adb -s $TvHost shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
        if($LASTEXITCODE -ne 0){throw 'Cannot verify Home after installation; original launcher was not disabled'}
        if(($after | Select-Object -Last 1).Trim() -ne $oldHome){
            & adb -s $TvHost shell cmd package set-home-activity --user 0 $oldHome
            if($LASTEXITCODE -ne 0){throw 'Could not restore original Home preference'}
            $restored=& adb -s $TvHost shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
            if($LASTEXITCODE -ne 0 -or ($restored | Select-Object -Last 1).Trim() -ne $oldHome){throw 'Home restoration could not be confirmed'}
        }
    }
    foreach($file in @($staged,$pulled)){if(Test-Path -LiteralPath $file){Remove-Item -LiteralPath $file}}
}
Write-Output 'Original Home verified. No launcher disabled, DNS changed, permission granted or screensaver replaced.'
