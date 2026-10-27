$ErrorActionPreference='Stop'
$tv='192.168.1.40:5555'
& adb connect $tv
& adb -s $tv shell pm enable --user 0 com.google.android.tvlauncher
if ($LASTEXITCODE -ne 0) { throw 'Could not re-enable Google launcher' }
& adb -s $tv shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'Could not restore Google Home' }
& adb -s $tv shell input keyevent 3
& adb -s $tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
