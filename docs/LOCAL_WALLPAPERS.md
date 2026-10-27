# Curated local wallpapers ? 27 October 2026

## Current behavior

The final personal collection contains **366 photos: 124 nature and 242 landscapes**,
**135.2 MiB** total, all 1920x1080 WebP quality 90. Source crops are scaled with Lanczos,
without any additional crop. The quality comparison switch and comparison JPEGs have been
removed from the TV. **Next wallpaper** remains. Full-colour ARGB8888 decoding is enabled.
Metadata enrichment is independent and continues as the API allowance permits.
The dated sections below retain the rollout history and superseded comparison experiments.

## Repository tools

The current companion scripts are included in tools/. They operate relative to this checkout,
using ignored wallpaper-metadata/ for inventory, prepared payloads, API cache and status.
They do not include personal source photos, API credentials or the signing key.
The owner's existing live collector still runs from its original workspace; copying these
tools into the repository did not restart it or alter its working data.

For a new local collection, create wallpaper-metadata/ with inventory.json and prepared.json
containing empty JSON arrays, and create wallpaper-metadata/device/. Place corrected JPEG
crops in nature_cropped/ and landscapes_cropped/ under your Downloads folder, or set
HOME_WALLPAPER_SOURCE_DIR to their parent folder. Filenames must end in an Unsplash photo
ID, for example photographer-ABCDEFGHIJK-unsplash_crop.jpg. Sources must already be 16:9;
the tool rejects mismatched crops. Python requires Pillow; metadata tools require Node 22+.

```powershell
$env:TV_HOST = 'YOUR_TV_IP:5555'
python tools/refresh-wallpaper-collection.py nature_cropped
python tools/deploy-wallpaper-refresh.py nature_cropped
```

Deployment requires adb on PATH (or ADB set to its executable), the sibling .toolchain JDK,
and your private app signing key at local/personal.jks. It verifies available space and
uploaded bytes before publishing a signed catalogue. These commands write TV wallpaper
data but do not navigate the screen. Review the staged refresh-plan.json first.

For metadata, put only your Unsplash access key in ignored .unsplash-access-key, then run
node tools/collect-wallpaper-metadata.mjs. Cache/quota/status are resumable; this is a local
process, not an installed system service. Do not run refresh/deploy concurrently with the
collector: stop it while it is waiting for quota, publish the refresh, then resume it.
Use python tools/export-wallpaper-credits.py to update the photographer credits document.
Never commit API cache, source photos or signed personal data directories.

## Initial rollout (historical)

The two user-curated Downloads collections contain 366 distinct 1920x1080 JPEG crops. Original
files total 195.1 MiB. WebP quality 90 exports total 109.4 MiB (about 44% smaller). Neither copying
nor compression modifies the source files. Both versions currently reside in Home's own external
files/wallpapers directory for direct comparison, totaling about 304.6 MiB plus filesystem overhead.
After initial deployment the TV reported 350 MiB free. Originals can be removed later after visual
comparison; no original was deleted from the TV or PC during this rollout.

## Use

Local images replace Bing when a signed local catalogue is present. Five-minute rotation and the
existing photo scrim, top gradient, glass blur and transitions remain. The collection has a stable
shuffle, so metadata updates and quality switches do not reorder it. Settings > Next wallpaper
advances immediately. Settings > Wallpaper quality toggles Original / WebP 90 for the same photo.
After the user accepted WebP 90, the next update defaults to WebP 90 once while retaining the
comparison switch. The top row remains five apps plus Continue (six slots total).

Curated decoding uses ARGB8888: the previous shared RGB565 preference could introduce banding
regardless of whether the source was JPEG or WebP. At 1080p this adds about 4 MiB per live decoded
wallpaper versus RGB565 (temporarily more during crossfade). This removes the launcher's 16-bit
quantization; it cannot fix banding already in source images, panel or downstream processing.

## Metadata

The TV receives only a signed caption catalogue. The Unsplash API credential stays on the PC.
Verified photographer names, publication dates, locations and photographer captions are used as
available; missing fields remain blank. Alt text is excluded. Publication dates are explicitly
labelled Published, not presented as date taken. Photographer credit is placed before descriptive
text so a long caption does not hide the attribution in the single-line footer.

PC tools at the workspace root prepare files, fetch metadata with the official API, and publish
signed catalogue updates separately from image files. The background collector respects API
allowances, stores progress, and resumes eligible batches. Successful batches update the signed
catalogue on the TV if ADB is connected, without remote input or restarting Home. If the TV is
unavailable, updated metadata remains queued locally. Status: wallpaper-metadata/collector-status.json.
This is a running local process, not a Windows startup service; PC shutdown stops it. Resume with
`node tools/collect-wallpaper-metadata.mjs` from the workspace root.

## Integrity and resources

No new Android permission, exported component or network host is added. The app can read its own
external-files directory without general storage access. Because external storage is mutable,
the catalogue is signed offline with Home's signing key and verified against the installed APK's
certificate, using a domain-separated SHA256withRSA signature. Image filenames, canonical paths,
file sizes, SHA-256 hashes, decode dimensions and sampling are checked. Image bytes are read and
verified once, then that same buffer is decoded to avoid a file-replacement race. The private
signing key never goes on the TV. Invalid local data retains the last picture rather than falling
back to Bing. The manifest is limited to 1 MiB / 500 entries; each image to 6 MiB and bounded pixels.

The old Bing cache is separate and is not pruned into the curated collection. Local photos are not
automatically deleted. Existing bounded photo workers, cancellation, generation guards and bitmap
release on stop are retained. Runtime metadata changes are picked up by the next normal minute
tick or app return.

## Validation

72 unit tests pass, including four integrity tests covering names/traversal, image tampering,
metadata signature/key mismatch and hash format. Full Android lint has zero errors. Initial local
JPEG/WebP display and Settings quality switching were exercised on the TV, with no crash in the
scoped runtime log. User tested both versions and accepted WebP 90. Full-colour decoding and header
button clipping fixes are included in the follow-up build; visual checks are recorded in CANDIDATE.md.

## Corrected nature crops ? 27 October 2026

Refreshed all nature crops and included one new photo (125 nature + 242 landscapes = 367 total).
The corrected sources are full-resolution 16:9 crops; exported to 1920x1080 using Lanczos scaling
with no additional crop. WebP 90 nature files total 33.9 MiB. JPEG comparison references (95,
4:4:4 when resizing was required) total 86.5 MiB and use the same corrected framing. Downloads
sources remain untouched. All 250 transferred image payloads were SHA-256 checked on the TV
before publishing the signed catalogue. Landscape variant records verified byte-identical to
the pre-refresh catalogue. No screen inputs, launcher restart or APK update. API metadata by
existing photo ID is preserved; the new ID joins the enrichment queue. Resumable collection-specific
refresh/deploy tools live under the workspace root tools directory.

## WebP-only selection ? 27 October 2026

After user approval, Home 1.10 removes the quality switch and always selects WebP 90 while
retaining full-colour decoding and Next wallpaper. Removed the TV's 366 comparison JPEGs; current
free space is about 541 MiB. Downloads sources remain intact. The rejected Martin Martz image
AOm3bb8cM-Y is excluded and removed from TV/generated image copies. Collection is now 366 photos
(124 nature, 242 landscapes). Future refresh tooling exports and deploys only WebP 90.

## Corrected landscape crops ? 27 October 2026

Refreshed all 242 landscape images from the corrected full-resolution 16:9 crops, scaling to
1920x1080 with Lanczos and no additional crop before WebP 90 encoding. Landscape payload is
101.8 MiB; the combined 366-photo WebP collection is 135.2 MiB. One new Johannes Plenio photo
replaces the removed Luca Bravo hFzIoD0F_i8 photo. The obsolete TV/active-device WebP copy was
removed after the signed catalogue update. Source Downloads files remain untouched.
All 242 uploaded image hashes matched. The 124 nature records are identical to their pre-refresh
snapshot. No APK update, launcher restart or remote-button input. Metadata syncing resumed.
