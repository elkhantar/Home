# Independent review of the Bing launcher fork

**Latest disposition — focused second pass:** the four findings below are addressed in the
subsequent source changes. See the second-pass section at the end. The original findings remain
here as the review history, not as a claim that the current candidate still contains them.

Reviewed 26 October 2026. Scope: the current working tree in `clean-xiaomi-tv`, candidate notes,
native launcher lifecycle, Bing/network/cache implementation, build adapter, verification code,
tests, and preview installer. This review did not change implementation, access the TV, install or
launch an app, or change device settings. Only this report was written.

**Result:** I found two practical Bing reliability defects, a deployment verification gap, and a
non-default theme restoration regression. I did not find evidence of credential harvesting or an
obvious remotely exploitable critical flaw in the reviewed paths. That is not a security certification.
The findings below are confirmed by source control flow; the on-device scenarios have not been
executed. I would fix the first two and wire artifact verification into installation before preview.

## P2 — Cancelling the first Bing request suppresses wallpaper retries for 30 minutes

**Sources:** [DailyPhoto.java:45](../../launcher/app/src/main/java/com/gathin/tvhome/DailyPhoto.java),
[DailyPhoto.java:54](../../launcher/app/src/main/java/com/gathin/tvhome/DailyPhoto.java),
[MainActivity.java:633](../../launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java).

`DailyPhoto.list()` sets `lastAttempt` before the initial uncached synchronous `refresh()`. Leaving
Home cancels `photoTask` with interruption. The interrupted fetch is caught as an ordinary refresh
failure, but `lastAttempt` remains set. The next calls return the empty cached list throughout the
30-minute backoff, even if Wi-Fi is healthy and the user immediately returns to Home.

**Trigger:** start with no `photos.json`, open the launcher, then launch another app while its first
archive request is in progress. Return to Home. If interruption prevented metadata being saved,
the picture remains unavailable until the global retry interval passes. This does not require
Bing to be down or return malformed data.

**Impact:** a normal fast app round-trip can make the initial preview look broken for half an hour.
The dark fallback still permits app use, so this is not a loss-of-Home or boot problem.

**Fix:** distinguish cancellation from network failure. Do not charge a cancelled initial request
against the failure backoff, or reset the retry state when there is no usable cache and the owner
returns to Home. Keep cancellation and retry state testable with an injected clock/fetch function.
Add a regression test for first-load cancellation followed by a successful immediate resume.

## P2 — Low-space pruning can destroy the only good cached photo without obtaining a replacement

**Sources:** [DailyPhoto.java:110](../../launcher/app/src/main/java/com/gathin/tvhome/DailyPhoto.java),
[DailyPhoto.java:111](../../launcher/app/src/main/java/com/gathin/tvhome/DailyPhoto.java),
[DailyPhoto.java:149](../../launcher/app/src/main/java/com/gathin/tvhome/DailyPhoto.java),
[MainActivity.java:649](../../launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java).

A new-photo load calls `prune(folder, null)` before checking whether it can afford the download.
While free space is below 64 MiB, pruning deletes JPEGs without protecting the displayed/last-good
photo. The following admission check requires 64 MiB plus the 6 MiB maximum image size. Pruning can
therefore delete the only offline fallback and still refuse the replacement.

**Concrete example:** free space is 63 MiB and the sole cached/current JPEG is 2 MiB. A rotation
chooses an uncached photo. Pruning deletes the current JPEG and free space becomes 65 MiB. The
70 MiB download admission check then returns `null`. The current in-memory picture survives, but
after another app is opened, `onStop()` releases that bitmap. Returning offline has no cached image.

**Impact:** storage pressure makes offline behavior worse by destroying the last known-good
fallback during an unsuccessful refresh. This matters on a TV with limited internal storage,
although the previously measured 547 MB free would not trigger this scenario immediately.

**Fix:** track and protect the displayed/last-good cache entry during admission and eviction. Evict
other entries first, and skip the new download without destroying the final fallback if the reserve
cannot be met. The existing cache test protects a supplied `keep` argument, but the problematic
production call passes `null`; test the full low-space admission flow with an injected space reading.

## P2 defensive deployment gap — The preview installer does not verify the APK before installing

**Sources:** [install-preview.ps1:7](../../install-preview.ps1),
[install-preview.ps1:14](../../install-preview.ps1),
[install-preview.ps1:16](../../install-preview.ps1),
[verify-candidate.py:30](../../tools/verify-candidate.py).

The installer checks only that a file exists before `adb install -r`. Package, certificate,
permissions and candidate hash are not verified as an installation precondition. Stronger checks
exist in a separate manual verifier, but the installation script never invokes it or consumes an
approved manifest/hash.

**Trigger:** the APK at the conventional path is replaced, is stale after a failed later build, or
does not match the artifact the user reviewed. The script installs it before discovering any
mismatch. Its subsequent `pm path com.privs.tvhome` is not proof about the installed artifact: if
that package already exists, installing an unrelated APK can still pass that check.

**Impact:** the promise of installing the reviewed candidate is not enforced at the point that
mutates the TV. This is a hardening/deployment-integrity gap, not evidence that the current APK is
malicious or that a remote attacker can alter a local file. The candidate was manually verified
in the preceding work; this finding concerns future invocation of the script.

**Fix:** verify APK package identity, permitted manifest surface, expected signer and the explicitly
approved SHA-256 immediately before installation, then verify installed identity/version afterward.
Make the verifier accept the exact selected APK, including the debug option, rather than silently
checking a different fixed release path. Retain the existing Home snapshot/restoration logic.

## P2 for theme configurations; P3 for the Bing-only candidate — Background/glass state is not restored after stopping

**Sources:** [MainActivity.java:254](../../launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java),
[MainActivity.java:256](../../launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java),
[MainActivity.java:537](../../launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java),
[MainActivity.java:650](../../launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java).

`onCreate()` builds a static theme wallpaper and its sampled blur, or applies a configured plain
background color. A static wallpaper makes `homePhotoOn` false. `onStop()` unconditionally clears
the sampled backdrop and changes the root background to black, while `onResume()` reloads only
the Bing path when `homePhotoOn` is true.

**Trigger:** use a configured static wallpaper, open a full-screen app and return. The static
wallpaper ImageView remains, but the glass no longer samples its image. With Bing disabled and
only a non-black background configured, the color changes to black after that same round-trip.

**Fix:** retain a background-source model and restore the correct wallpaper/blur/color on resume,
or release only sources that have a corresponding reload path. Add resume tests for Bing, static
theme wallpaper and plain-color modes. The default Bing setup does reload, so this is not the
highest-priority blocker for the current narrow preview; it is a real regression in retained
configuration functionality.

## Test and review gaps

The 58 passing tests are useful but do not test the activity lifecycle, cold archive refresh and
cancellation, HTTP connection/redirect/TLS behavior, low-space admission before eviction, or the
preview installer. The added cache test directly exercises `prune(folder, keep)` rather than the
new-photo load path. The cancellation/deadline tests exercise the bounded stream reader, not the
interaction between cancellation and global archive backoff.

I inspected the source and existing results rather than repeating the broad build. I did not run
an emulator, benchmark memory/frame time, test the physical remote/HDMI/standby, verify old Android
TLS trust against the live servers, or perform a complete transitive dependency/CVE audit.

Positive findings: the manifest requests only Internet and network/Wi-Fi state; there are no bound
services; the unwanted prayer/media features are removed from the active implementation; the
network client uses a fixed HTTPS-host allowlist and rejects redirects; image decode and transfer
sizes are bounded; configuration is private; metadata accesses are serialized; photo results have
lifecycle generation checks; and the preview installer does not disable the existing Home, change
DNS, grant permissions, or replace the screensaver. No additional concrete stale-weather defect
was found in the new six-hour cutoff path.

Additional lower-priority hardening to consider: stale `.tmp` JPEG files left by process death are
not counted or cleaned by the JPEG-only pruning routine, so the 24 MiB budget does not cover all
possible abandoned cache files. This needs a crash-recovery test and cleanup policy; I did not
reproduce a process-kill scenario on a device.

The custom compiler adapter is an exceptional build path and deserves a clean-build comparison in
a normal environment before general distribution. No confirmed current API-28 incompatibility was
found in this pass; static compatibility checks do not replace on-device testing.

## Focused second pass after remediation

I inspected the specific fixes and added tests without changing implementation, accessing the TV,
or repeating the build. **No new concrete blocker was found in these changes.**

| Original finding | Source disposition | Remaining validation |
|---|---|---|
| Cancelled cold fetch consumes backoff | Addressed. `ArchiveRetry` charges the delay at completion and makes success/interruption immediately retryable. `DailyPhoto.refresh()` finalizes retry state even through the exception path. | The added `ArchiveRetryTest` covers immediate retry after cancellation and completion-based backoff. Actual Android HTTP interruption and the pause/resume integration still need preview testing. |
| Low-space pruning deletes final fallback | Addressed. `prepareDownload()` protects the last decoded photo, or the newest cached JPEG after process restart, while evicting other entries toward the full admission reserve. It declines the download while preserving that fallback if space remains insufficient. | The new injected-space test covers the reported 63 MiB scenario and verifies fallback retention. Real device storage-pressure behavior remains untested. |
| Installer lacks artifact verification | Addressed. The installer copies the exact selected APK to a unique staging path and verifies its approved hash, package, signer, manifest surface and version before any ADB operation. It rechecks staged bytes, installs that copy, pulls the installed base APK and compares its bytes, and checks/restores the prior Home in `finally`. | Mock/fault-path checks and then the authorized actual preview should cover installation failure, loss of ADB and Home restoration. An unavailable ADB connection can still prevent recovery verification; the original launcher is never disabled. |
| Static theme/plain background loses state | Addressed. `onStop()` now clears the bitmap/backdrop and applies black only for the Bing source. Static theme state and configured plain background color are retained across app round-trips. | Validate visually. Retaining static theme bitmaps is a small memory tradeoff; documentation should distinguish this from releasing Bing imagery. |

The reported abandoned-image `.tmp` concern is also addressed: the serialized image-loading path
removes abandoned temporary files before cache pruning/admission. Metadata atomic-write files use
different suffixes and are not treated as image-download temporaries.

The artifact-only verifier checks the selected debug/release APK rather than silently checking the
fixed release path, and it does not rewrite the approval metadata during installation. The staged
file and installed-file comparison substantially strengthen the deployment boundary. This does
not amount to an independent fresh signing/hash verification in this second pass: I inspected the
implementation, while the parent agent owns the final build and artifact checks.

Proceeding to the authorized preview while Google remains the default is reasonable once the
parent's final build/tests/artifact verification pass. Keep the distinction between passing unit
tests and unperformed Android lifecycle, visual, remote, standby and recovery checks. No source
review can guarantee those on this particular firmware.
