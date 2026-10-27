$ErrorActionPreference='Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$original=Get-Content local/candidate.json -Raw | ConvertFrom-Json
$negative=Join-Path (Get-Location) 'local/rejected-candidate.json'
$log=Join-Path (Get-Location) 'local/installer-negative-test.log'
$global:previewAdbCalls=0
function global:adb { $global:previewAdbCalls++; throw 'Unexpected ADB call in a negative test' }
try {
    foreach($case in @('hash','version')) {
        $candidate=$original | ConvertTo-Json -Depth 10 | ConvertFrom-Json
        if($case -eq 'hash'){$candidate.sha256='0'*64}else{$candidate.version_code='999999'}
        $candidate | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $negative
        if(Test-Path $log){Remove-Item -LiteralPath $log}
        $rejected=$false
        try {& ./install-preview.ps1 -TvHost 192.0.2.1 -CandidateManifest $negative *> $log}
        catch {$rejected=$true; [IO.File]::AppendAllText($log,$_.ToString())}
        if(!$rejected -or $global:previewAdbCalls -ne 0){throw 'Installer failed to reject before contacting ADB'}
        $expected=if($case -eq 'hash'){'does not match the approved SHA-256'}else{'Candidate version differs from approved metadata'}
        if((Get-Content $log -Raw) -notlike "*$expected*"){throw "Rejected for an unexpected reason: $case"}
        Write-Output "PASS: $case mismatch blocked before ADB"
    }
} finally {
    Remove-Item Function:/adb -ErrorAction SilentlyContinue
    if(Test-Path $negative){Remove-Item -LiteralPath $negative}
}
