# Changelog

## 1.11 - Spanish menus

Complete Spanish resources (80 strings), including favourites editing, settings and module
cards. The owner approved Favoritos for dock terminology. 72 tests passed; lint: 0 errors.

## 1.10 - Curated WebP wallpapers

Settle on WebP quality 90 and remove the original/WebP comparison switch, retaining the
next-wallpaper action. Corrected nature and landscape crops are scaled to 1920 x 1080
without additional cropping. The private collection contains 366 images (135.2 MiB).

## Curated wallpaper rollout

Signed offline catalogues, per-image SHA-256 checks and bounded local decoding. Separately
synchronized photographer, publication date, location and photographer caption metadata.
Switch to ARGB8888 eliminated launcher-induced colour banding. Fix highlighted header
button clipping. Personal source photos and metadata credentials are not distributed.

## 1.7 - Continue watching

A fixed sixth top-row slot opens up to ten Continue/Next items from Android's Watch Next
provider. Validate and constrain playback links to the publishing app. Real Disney+ playback
and Crunchyroll entries were tested; Netflix/Prime expose no eligible entries on this TV.

## 1.6 - Rendering cleanup

Reuse glass drawing objects and release dismissed popup/backdrop references. On-TV samples
showed no measurable animation speedup; the visual effects were retained.

## Earlier Home previews

Independent package ID and Home branding; high-resolution house icon/banner with a remote-
inspired circular window; dark header gradient; open/closed frosted drawer; aligned first
row on drawer opening. Remove app-dependent wallpaper tinting. Long-press app actions use
Android settings/uninstall confirmation. Harden network loading, retry/cancellation and
fallback cache pruning. Stage, verify and byte-check preview installs. Remove prayer,
athkar, Now Playing, key accessibility services and custom screensaver. Existing MIT/OFL
notices retained. Home became the owner's default launcher on 26 October 2026.
