# Home

An ad-free Android TV launcher with a frosted-glass app drawer, rotating photographs,
clock, weather and Continue watching. Built for a family Xiaomi TV and tested on a
MiTV-MSSP3 running Android 9, with a 1920 x 1080 Android UI on a 4K panel.

Home is a modified fork of [CleanXiaomiTV](https://github.com/majnioui/CleanXiaomiTV)
by majnioui, based on [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin.
The upstream base is commit `5d371afffad6439daf9eae0a7f569ceb88e637cf`.
See [credits and licenses](ATTRIBUTIONS.md). This is an independent personal fork.

## Features

- Five editable favourites plus a fixed **Continuar** button in the top row.
- A dark, frosted drawer that opens when navigating down, with configurable apps per row.
- Clock, date and Open-Meteo weather; complete Spanish menu translations.
- Rotating local wallpapers with photographer credits, publication date, location and
  photographer caption when verified metadata is available. No generated alt text.
- Full-colour ARGB8888 decoding; curated images are 1920 x 1080 WebP quality 90.
- **Siguiente fondo** advances immediately. The comparison-quality switch has been removed.
- Continue watching lists up to ten unfinished items or next episodes published by installed
  apps through Android TV's Watch Next provider. Availability depends on each streaming app.
- Long-press to move an app, open its Android settings (including Force stop), or request
  uninstall with Android's confirmation. System apps and Home are excluded from uninstall.
- Optional module cards. Forslag has not been integrated or tested as an add-on here.

Prayer times, athkar, Now Playing, accessibility key services and the replacement screensaver
were removed. The TV's existing screensaver remains in use. Drive sign-in and automatic
Unsplash photo discovery are not implemented.

## Build

Requires JDK 17, Android SDK 34/build-tools 34.0.0 and Gradle 8.9.
The local Windows build script expects a sibling `.toolchain` directory containing
`jdk/jdk-17*`, `sdk` and `gradle-8.9`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-personal.ps1 -Release
python tools/verify-candidate.py
```

The script creates a personal key in ignored `local/personal.jks` if absent, then builds,
runs tests and Android lint. Its sandbox compiler bridge works around restricted Java
ZipFS operations; Gradle still handles resources, R8, packaging and tests. For a normal
build, use the Gradle wrapper under `launcher` after setting up the SDK and signing key.

Package: `com.privs.tvhome`. APK: `launcher/app/build/outputs/apk/release/app-release.apk`.
The installed version is `1.11-spanish-menus` (code 12). The last verified build passed
72 tests and lint with 0 errors and 123 warnings. Other inherited locales are partial.

**Signing:** the personal key is not distributed. Keep your own key backed up for future
updates. The build file uses a local development password, not a production secret.
The verifier intentionally pins the author's certificate: another builder must change
that pin in `tools/verify-candidate.py` to their own independently verified certificate
before using the preview installer. An APK signed with another key cannot update an
existing installation under the same package name.

## Install and recover

Enable ADB debugging and approve your computer on your own TV. Build and verify first:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\install-preview.ps1 -TvHost 192.168.1.40
```

Replace the example address with your TV's IP. The installer verifies the staged APK and
pulls back the installed APK to compare bytes. It preserves the existing default Home;
it does not disable launchers, change DNS, grant permissions or replace the screensaver.
Open Home from the app list to test it before selecting it as the default in Android.

Some Xiaomi firmware requires a vendor-specific workaround to change the default.
[Installation and recovery](docs/INSTALLATION.md) records the tested procedure and limits.
Do not run inherited debloat, theme or restore tools unchanged: their assumptions and
package IDs belong to upstream and they can alter unrelated TV settings.

## Privacy and resources

No accounts, analytics, notification listener, accessibility service or broad storage
permission. The five permissions are Internet, network state, Wi-Fi state, request
uninstall and read TV listings. TV-listings consent is requested when enabling Continuar;
request-uninstall only opens Android's confirmation.

The TV's built-in network clients allow HTTPS only to Bing and Open-Meteo, reject redirects,
and bound responses and worker queues. Weather sends the configured city coordinates.
Local wallpapers live in Home's own external-files directory and need no storage grant.
The offline-signed catalogue and each image hash are verified before decoding. Private
signing keys and Unsplash API credentials stay on the computer.

The app uses cached blur surfaces and releases wallpaper references when stopped. The
curated photo collection is user-provided and is not included in this repository. Bing
is the fallback when no local catalogue is configured. Its cache is capped at 12 files /
24 MiB with a 64 MiB free-space reserve. The curated collection has its own separate
limits. See the linked implementation notes for exact validation and failure behavior.

## Documentation

- [Wallpapers and metadata workflow](docs/LOCAL_WALLPAPERS.md)
- [Continue watching, service support and safe deep links](docs/WATCH_NEXT.md)
- [Spanish localization](docs/SPANISH_LOCALIZATION.md)
- [Installation and recovery](docs/INSTALLATION.md)
- [Security model and limitations](SECURITY.md)
- [Performance measurements](PERFORMANCE.md)
- [Version history](CHANGELOG.md)
- [Build and TV validation record](CANDIDATE.md) (dated observations, not a device guarantee)
- [Credits and third-party licenses](ATTRIBUTIONS.md)

Source code is MIT licensed with the original copyright retained. Fonts, icons,
third-party dependencies, photographs and service data retain their own licenses.
