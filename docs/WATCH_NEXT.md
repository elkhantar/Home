# Continue watching

Home reserves the final favourites slot for Continue. Its previous app remains available in the
app drawer; the saved dock list is not silently overwritten on upgrade. The list editor and
in-place reorder operate on the remaining five app slots. The reserved slot cannot be uninstalled
or moved. OK opens a dark frosted panel above the dock; Up/Down navigate, OK opens the provider's
deep link, and Back returns focus to the button. Up to ten newest usable entries are displayed.

## Data and compatibility

The source is the Android TV provider's `content://android.media.tv/watch_next_program` table.
Home requests normal runtime `android.permission.READ_TV_LISTINGS` from an explanatory first-open
panel. Denial leaves the launcher usable. It does not request root, accessibility, notification
access, streaming credentials, usage access or signature-level EPG permissions. Home queries
without SQL selection (required for non-privileged readers), ordered newest first, and scans at
most 1,000 visible rows. Results are filtered to browsable Continue (0) and Next (1) entries,
exclude explicitly completed Continue entries, sort newest engagement first, deduplicate by
publisher and playback URI, and cap at ten. Progress is shown only when an app provides it.

This is not an independent account-history aggregator. Apps must publish searchable rows; some
publish none or keep their feed private. Home cannot reproduce private Google-launcher service
integrations or infer the next episode inside Netflix/other services. Next episodes appear only
when the publishing app supplies a Next entry. The system feed is scoped to the Android user,
not a profile picker for each streaming service. Streaming apps control profile/access checks and
resume behavior. The list can be stale if the publishing app stops updating it.

## Security and performance

The query runs on demand on one bounded background worker, with a cancellation signal and stale
callback generation checks. Closing the list or pausing/destroying Home cancels the work; no
background polling is added. There is no viewing-history file, server upload or TV-provider write.
Logs contain result counts/query status, not titles or playback URIs. Only installed app icons
and labels are used, so no arbitrary poster URLs or network hosts were added. Existing HTTP
allowlist, cache limits and TLS rules are unchanged.

Deep links are parsed without unsafe URI flags. They must use ACTION_VIEW, have non-local data,
and resolve to an exported activity in the publishing package. Conflicting packages/components,
selectors, self-launches and file/content/data/javascript/intent schemes are rejected. Package
binding is reapplied, URI grant flags are removed, and clip data is cleared. Scalar app-provided
deep-link extras are retained for playback compatibility. Targets are checked again at selection.
Activity disappearance or security rejection produces the existing unavailable notice.

## Validation on MiTV-MSSP3 / Android 9

- First-open permission explanation and Android's TV-listings permission prompt exercised.
- Permission accepted through the normal system UI. Ten real playable entries returned, including
  Disney+ unfinished episodes with episode labels and progress bars.
- D-pad navigation and scrolling exercised; list placement verified above the dock.
- Selecting a real entry opened Disney+'s PlaybackActivity. Paused playback and returned Home;
  the publishing app subsequently updated its engagement order.
- The focused-row text contrast and button icon sizing were corrected during on-device review.
- Unit tests cover type filtering, completed/unknown progress, newest-first deduplication, the
  ten-item cap, malformed/oversized records, unsafe schemes and overflow-safe progress (six tests).
- Next-type selection is unit-tested; no claim that a real Next-type row was present in this test.
- Local release checks: 68 tests pass, Android lint zero errors. The exact permission allowlist now
  includes READ_TV_LISTINGS. Negative installer hash/version rejection tests pass.

References:
- https://developer.android.com/reference/androidx/tvprovider/media/tv/WatchNextProgram
- https://android.googlesource.com/platform/packages/providers/TvProvider/+/master/src/com/android/providers/tv/TvProvider.java
- https://android.googlesource.com/platform/frameworks/support/+/6cf009e2939410a0844205d1a49ae45249b0f858/tvprovider/tvprovider/src/main/java/androidx/tvprovider/media/tv/TvContractCompat.java

## Unfiltered accessible-feed audit ? 26 October 2026

A temporary same-certificate instrumentation APK queried with Home's target context/UID and
verified READ_TV_LISTINGS granted. No database writes, production APK changes or extra permission
grants. Output contains counts/classifications only, not titles, profile labels or playback URIs.
Queried all 41 accessible Watch Next rows without Home filters/ten-item limit (not truncated).

| Publisher | Watch Next rows | Eligible | In newest ten | Preview rows |
|---|---:|---:|---:|---:|
| Disney+ | 12 | 12 | 5 | 47 |
| Crunchyroll | 12 | 12 | 5 | 20 |
| VLC | 16 | 16 | 0 | 140 |
| RTVE Clan | 1 | 0 (unusable playback link) | 0 | 68 |
| Netflix | 0 | 0 | 0 | 17 |
| Prime Video | 0 | 0 | 0 | 0 |

Crunchyroll rows include ten Continue and two Next entries. No hidden/completed/type exclusions
apply to Disney+, Crunchyroll or VLC. Netflix's 17 preview programs are in a separate channel,
all have resolvable publisher-bound links, but none provides positive position or duration. None
of its three channel labels matched continuation keywords. This does not establish unfinished
viewing, so those preview programs were not merged into Continue. Prime exposes one empty channel.
Installed Netflix current version: 8.3.11 build 12041; Prime: 6.24.12+v16.0.1.23-allAbis.

Scope: a non-privileged reader sees only own/searchable rows. These results rule out our filters
and limit as the cause for absent Netflix/Prime items, but cannot distinguish unpublished rows
from non-searchable rows or private Google integrations. They are a current snapshot, not proof
that either app never publishes under other versions, devices or after future playback.

Online research:
- Original launcher developer's first-hand probes found 14 Prime Watch Next rows on TCL Google
  TV 12, but no Netflix rows on that device. Thus Prime support is not universally absent:
  https://github.com/Gathin23/cleanyourtv/blob/main/docs/watch-next.md
- Projectivy user issue reports Netflix channel data present on one onn Android 14 model but
  absent on another; this is a first-hand device report, not a Netflix support guarantee:
  https://github.com/spocky/miproja1/issues/454
- Google documents a separate Engage Continue Watching API, distinct from legacy Watch Next.
  This does not establish which integration the installed Netflix/Prime builds use:
  https://developer.android.com/guide/playcore/engage/tv/continue-watching/client

No authoritative public Netflix/Amazon commitment to publishing accessible legacy Watch Next
rows across supported devices was located. Saved aggregate report: local/watchnext-audit/report.json.
