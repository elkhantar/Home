# Installation and recovery

Tested on Xiaomi MiTV-MSSP3, Android 9. Other firmware may behave differently. The preview
installer preserves the existing default; choose Home only after trying app navigation,
inputs, Live TV, remote keys and standby on your device.

## Default launcher

Try Android's default Home picker first. ADB can request the same selection:

```powershell
adb -s YOUR_TV_IP:5555 shell cmd package set-home-activity --user 0 com.privs.tvhome/com.gathin.tvhome.MainActivity
adb -s YOUR_TV_IP:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

On the tested Xiaomi, the first command reported success while resolution still pointed to
Google. After the owner approved replacement, disabling the Google launcher package for
user 0 allowed Home to become the default. Its data and installation were retained:

```powershell
adb -s YOUR_TV_IP:5555 shell pm disable-user --user 0 com.google.android.tvlauncher
adb -s YOUR_TV_IP:5555 shell cmd package set-home-activity --user 0 com.privs.tvhome/com.gathin.tvhome.MainActivity
```

This is a device-specific fallback, not a required install step. Identify your actual stock
launcher package and keep ADB recovery access before disabling it. Never disable the only
working launcher. Google Backdrop remains the screensaver; DNS is unchanged.

## Restore the tested Google launcher

```powershell
adb -s YOUR_TV_IP:5555 shell pm enable --user 0 com.google.android.tvlauncher
adb -s YOUR_TV_IP:5555 shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
adb -s YOUR_TV_IP:5555 shell input keyevent 3
```

`restore-google-home.ps1` contains this recovery for the author's specific TV address.
Inspect and adapt it before use. Other devices may use `com.google.android.apps.tv.launcherx`
or another component. Do not uninstall Home until a working stock launcher is restored.

Old upstream scripts under tools/ and PROMPT.md describe a broader debloat/theme setup.
They are retained for provenance and are not the supported Home installation workflow.
