# Validation history — 26 October 2026

The table below describes the current artifact. Subsequent sections preserve dated test history;
earlier preview statements are superseded by the later deployment records.

Installed as **Home** on MiTV-MSSP3 / Android 9 at 192.168.1.40.
**User accepted Home; Home is now the default launcher.**

| Property | Verified value |
|---|---|
| Package | `com.privs.tvhome` |
| Version | `1.11-spanish-menus`, code 12 |
| APK size | 402,775 bytes |
| SHA-256 | `c7af8e1cdfa9e272de5ead6600f91016cb200e46c34be6811ab1b7a67a64eb50` |
| Signature | Verified personal signing certificate, pinned by installer |
| Launcher tests | 72 passed; zero failures, errors or skips |
| Full Android lint | 0 errors, 123 warnings |
| Packaged services | 0 |
| Permissions | Internet, network state, Wi-Fi state, request uninstall, read TV listings |

Artifact: `launcher/app/build/outputs/apk/release/app-release.apk`.
Reproduce verification with `python tools/verify-candidate.py`.
The verifier's `device_accessed: false` describes its local checks; installation is recorded here.

## Independent review remediation

- A cancelled cold Bing request no longer consumes the 30-minute failure backoff. An explicit
  lifecycle token covers Android I/O that clears the thread's interrupt flag. True failures back
  off from completion. Three retry tests cover these cases.
- Low-space pruning preserves the last decoded fallback (or newest cached file), reserves room
  before downloading, and cleans abandoned temporary downloads. Decode OOM retains the file.
- Preview installation stages and checks package, version, SHA-256 and pinned signing certificate
  before ADB. Wrong hash/version tests confirm rejection before any ADB call. The installed APK
  is pulled back and compared byte-for-byte. Original Home is verified/restored in finally.
- Static theme backgrounds survive stop/resume; only Bing wallpaper/blur references are released.
  This static-theme path was source-reviewed, not exercised with on-device private configuration.
- Network requests use fixed HTTPS hosts, no redirects, size/dimension/deadline limits, bounded
  workers, sampled 1080p decode and a 24 MiB / 12-file cache with 64 MiB free-space reserve.
- Prayer/athkar, Now Playing, their services/helpers and the replacement screensaver are removed.
  No location, storage, account, accessibility or notification permission is included.

The independent review and second-pass disposition are in `../review/INDEPENDENT-FORK-REVIEW.md`.
Lint warnings include incomplete upstream translations, unused resources/attributes and older
style/dependency notices; this is not a complete dependency vulnerability certification.

## Observed on the TV

- Bing archive HTTPS succeeds on Android 9 with ordinary certificate verification.
- Wallpapers display and change across five-minute slots, with credits. A later launch restores
  cached imagery and the glass backdrop.
- Clock, Spanish date and live weather for Aldeamayor de San Martín display correctly.
- Dock launches Netflix; returning to the preview restores the wallpaper. No playback was started.
- App drawer and D-pad navigation tested through ADB; physical remote acceptance remains pending.
- Cold preview activity launch around 0.9–1.2 seconds in these runs, not a benchmark.
- One wallpaper/drawer PSS snapshot approximately 29.5 MiB, not a long-duration memory/GPU test.
- Google TV launcher remains default; Google Backdrop remains the screensaver. Private DNS mode
  and hostname remain `null`, matching saved pre-install values. No further debloat was done.
- Scoped AndroidRuntime log sample contained no fatal exception during these checks.

Screenshots and before-setting snapshots are under `local/tv-preview/` (ignored by git).
The TV has a 4K panel but renders the Android UI at 1920×1080.

## Still to validate

Physical remote, HDMI/Live TV, extended offline use, sustained frame-time/memory, standby/wake,
reboot persistence and recovery have not been fully exercised. The TV retains its old Android
security patch and development ADB access. Google Home will only be replaced after user acceptance.
Forslag remains optional and uninstalled; the earlier Cosy Home preview remains installed.

## Home 1.3 visual refinement

App label is Home, with a new vector house icon and TV banner. The round window has a centre
circle and four surrounding segments inspired by a remote D-pad. Removed the ambient hue overlay
and app-colour sampling. A fixed top gradient improves header contrast. Drawer rows are hidden
while favourites are focused; moving down reveals a dark frosted pane backed by the existing
cached blur. Returning up closes it. Both states and the return transition were captured on the TV
in `home-closed-v13.png`, `home-open-v13.png`, and `home-reclosed-v13.png`. Original Google Home
preference verified after this installation; scoped crash log sample was clear.

## Home 1.4 drawer alignment

Opening the drawer scrolls the favourites row to a 16dp top margin immediately, rather than
only scrolling enough to reveal the focused next row. Framework focus requests use the same
alignment for the first drawer row. Verified on TV: opening without the blank upper row,
navigating deeper, returning up, and closing/parking favourites. Screenshots:
`local/tv-preview/home-open-v14.png` and `home-reclosed-v14.png`. Release APK installed and
byte-verified with Google retained as default. All 62 unit tests and full lint pass (zero errors).

## Home 1.5 app actions and artwork

Long press opens a frosted contextual menu: Move, App settings, and Uninstall for non-system
apps. Move resolves the captured stable component key again before entering the existing reorder
mode. App settings opens Android's per-app page for Force stop. Verified on the TV with Netflix:
AppManagementActivity displayed the correct package and Force stop action. System apps omit
Uninstall, and Home excludes itself. The system uninstall intent supplies only the selected
package URI, uses normal REQUEST_DELETE_PACKAGES permission, and requires Android confirmation.
No silent uninstall/force-stop privilege was added. The native uninstall confirmation/cancellation
was not completed in this test session because concurrent physical remote use began; no app was
removed. Activity-not-found/security denial produces the existing unavailable notice.

Updated the verifier's exact permission allowlist to include only the newly required normal
request-uninstall permission. All 62 tests and full lint pass with zero errors; negative installer
hash/version rejection tests pass. Installed APK is byte-verified, Google Home retained.

The application icon is now a 512px PNG and TV banner a 1280x720 PNG exported from editable SVG
masters in artwork/. This avoids low-intrinsic-size vector caching by external launchers. Banner
text uses proper font lettering. Assets preserve the circular remote-inspired window. Local image
inspection confirms sharp source rendering; final external-launcher appearance received user
positive feedback generally, but no separate pixel-quality measurement was performed.

## Home 1.6 measured rendering cleanup

Per-surface paint/matrix/geometry and shader reuse, with explicit shader release on backdrop
changes/stop. Backdrop subscribers now use weak references, allowing dismissed popup views to be
collected. Existing visual constants and security/network behavior retained. Installed and
byte-verified; Google stays default. Before/after navigation, app-return measurements and visual
checks completed. Two navigation samples per build show no measurable smoothness gain; see
PERFORMANCE.md for exact figures and limits. All 62 tests and lint pass (zero errors).

## Default Home activated ? 26 October 2026

User approved replacing the default after testing. Setting preferred Home alone reported success
but still resolved to Google. Disabling only the activity was rejected by Android shell permissions.
Disabled the Google launcher package for user 0 (no uninstall or data clearing), then set Home.
Verified both HOME intent resolution and actual resumed activity after pressing Home:
`com.privs.tvhome/com.gathin.tvhome.MainActivity`. Google Backdrop remains the screensaver.
Google launcher package/data are retained. Rollback: `restore-google-home.ps1` re-enables Google
and selects it as Home. Reboot persistence has not been tested in this step.

## Home 1.7 Continue

Reserved last dock slot opens an upward frosted list of the ten newest usable Continue/Next
entries shared through Android TV's provider. Last dock app remains in drawer. READ_TV_LISTINGS
is requested via explanation plus normal Android permission prompt; accepted on-device during
feature testing. No streaming credentials, privileged EPG permission, remote posters or additional
network hosts. Read-only, bounded, cancellable queries and publisher-bound safe playback links.
Real list returned ten entries; selecting one entered Disney+ PlaybackActivity and Home return
worked. Final remote navigation and focused-row contrast visually verified in
`local/tv-preview/continue-final-fixed.png`. Counts-only logs show success without fatal exception.
68 tests pass, full lint zero errors (121 warnings, including upstream/added translation fallbacks).
Installed exact candidate byte-verified, Home remains default. See docs/WATCH_NEXT.md for design,
security constraints, service compatibility and which cases were unit-tested vs observed on-TV.

## Home 1.9 curated wallpapers and colour ? 27 October 2026

366 user-provided 1080p crops replace Bing through a signed local catalogue. Original JPEG set:
195.1 MiB; WebP 90 set: 109.4 MiB. Both installed temporarily for comparison (304.6 MiB payload;
TV reports about 350 MiB free). Originals in Downloads remain untouched. User accepted WebP 90;
this update selects it once by default while retaining the Original/WebP 90 comparison switch.
Settings > Next wallpaper advances instantly. No storage or other permission added.

App-specific external files are authenticated using a signed catalogue and per-image SHA-256.
Checks include canonical paths, strict names, bounded file sizes/pixels, domain-separated signatures
verified with the installed APK certificate, and decoding the same verified bytes. Four new unit
tests cover these integrity rules. Curated decode changed from RGB565 to ARGB8888 after the user
observed banding in both JPEG and WebP: this removes our own 16-bit quantization, without a claim
that source/panel banding is eliminated. Existing scrim, blur and rotation retained.

Top-button ancestor clipping is disabled so focused circular buttons can expand normally; the grid
wrapper still clips drawer contents. User confirmed the circular-button clipping issue fixed.
Final on-TV checks verified Original selection, WebP 90 restoration and Next wallpaper action;
WebP 90 left selected. Full-colour screenshot: local/tv-preview/curated-full-colour-final.png.
Scoped runtime log sample clear. User will reassess banding from normal viewing distance.

72 tests pass, full lint zero errors. Final APK installed and byte-verified, Home remains default.
PC metadata collector is running within Unsplash API quotas; verified photographer captions,
publication dates and locations are uploaded as signed metadata-only updates when the TV is
reachable. Alt text excluded. Credentials remain on PC. See docs/LOCAL_WALLPAPERS.md.

## Home 1.10 WebP-only ? 27 October 2026

User approved WebP 90 and requested removal of the comparison switch. Removed the quality selector
and preference path; curated selection is always WebP 90, with ARGB8888 decode retained. Next
wallpaper remains. Signed publisher emits only WebP variants; future collection refreshes prepare
and deploy WebP only. Removed 366 comparison JPEGs from the TV, preserving Downloads sources and
PC working copies. TV has 366 WebP images and reports 554,396 KiB (about 541 MiB) free.

Removed rejected photo AOm3bb8cM-Y from inventory, active/prepared catalogues, TV image variants
and generated workspace copies. The specified Downloads file was already absent. Existing metadata
cache is retained for reuse but cannot reintroduce the photo because it is no longer in inventory.
Background metadata syncing resumed. Installed exact APK byte-verified; menu checked on-TV for
Next wallpaper present and quality option absent. All 72 tests pass; lint zero errors.


## Spanish menus - 27 October 2026

Version 1.11 completes the 46 missing Spanish translations: 80/80 default string keys and
positional format placeholders match. Release build, 72 tests and full lint passed (0 errors,
123 warnings). Installed APK was pulled back and byte-verified; Home remains the default.
Repeated TV checks after the user handed back the controls confirmed Settings, favourites
editing, apps per row (still 6), advanced effects and its translated status. Screenshot:
`local/tv-preview/spanish-menu.png`. No favourites or settings were changed during these checks.
The user approved Favoritos as the dock translation on 27 October 2026.
