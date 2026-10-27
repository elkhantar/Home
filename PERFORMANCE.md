# TV performance check ? 26 October 2026

Target: MiTV-MSSP3, Android 9, 1920x1080 Android UI. Baseline Home 1.5; candidate Home 1.6.
The user made the TV available for interactive measurements. Google remains default.

## Method

`python tools/profile-tv.py LABEL` wakes the TV, restarts only the preview, warms up its navigation,
then records two identical D-pad sequences using Android gfxinfo reset/read counters. Each sequence
moves right/left across favourites, enters the drawer, moves across and down, then returns up.
There is a 650ms pause after each injected key, in addition to Android input-command overhead.
Stats exclude the cold launch and warmup. Raw reports live in ignored local/performance/.
These are small sequential samples on a live TV, not isolated GPU benchmarks or guarantees of
sustained behavior. Android's jank counter and percentile histogram are reported as supplied.

App-return samples launch Netflix without starting playback, return to Home, and record am start
and gfxinfo after three seconds. Activity-start times do not measure wallpaper readiness.

## Changes

Glass surfaces now own reusable paint, matrix and edge geometry. Their gradients and composed
shaders are reused until surface geometry or wallpaper changes, with texture coordinates updated
as the dock moves. Every surface has its own renderer; there is no shared mutable paint state.
The rounded corners, colours, opacity, sheen, blur, focus animation and drawer behavior are retained.
Cached shaders release old bitmap references immediately when the backdrop changes or is cleared
on stop. This keeps the existing bitmap lifecycle and memory bounds intact.

Backdrop subscriptions now use weak references, pruning dead subscribers while registering and
updating. Dismissed popup panels are no longer kept alive by the activity's backdrop subscription
list. Existing code already skips tile rebuilding on unchanged app returns; no additional artwork
cache was introduced. No permissions, network policy, image limits or security controls changed.

## Validation

Release build, 62 unit tests and full Android lint pass (zero errors). Runtime comparisons and
visual checks are recorded below after the candidate measurements complete.

## Results

| Build / sample | Frames | Reported jank | p50 | p90 | p95 | p99 |
|---|---:|---:|---:|---:|---:|---:|
| baseline 1 | 354 | 115 (32.49%) | 8 ms | 73 ms | 81 ms | 89 ms |
| baseline 2 | 360 | 116 (32.22%) | 8 ms | 73 ms | 81 ms | 85 ms |
| candidate 1 | 357 | 115 (32.21%) | 8 ms | 73 ms | 81 ms | 89 ms |
| candidate 2 | 354 | 115 (32.49%) | 8 ms | 73 ms | 81 ms | 89 ms |

There is no measurable navigation smoothness improvement in these samples. The draw-command
and frame-deadline counters remain similar. Removing allocations has not addressed the dominant
frame-time cost; a CPU/GPU trace would be required to localize that cost more precisely.

Netflix return activity-start times: baseline 135 / 106ms; candidate 101 / 110ms. These small
samples do not establish a speedup. The post-navigation PSS snapshots were 42,201KiB baseline
and 35,924KiB candidate; Java allocations were similar. PSS depends on shared pages, caches and
timing, so do not attribute the difference to this patch or promise a sustained memory reduction.

The code-level benefits are reusable drawing objects and removal of strong retention of dismissed
popup panels. Screenshots candidate-closed.png and candidate-open.png were visually inspected:
wallpaper, header, dock, frosted drawer and focus appearance are retained. App return restores
wallpaper and glass correctly. Candidate has exactly the prior four permissions and zero services.
Full lint: zero errors, 109 warnings; 62 unit tests pass. No crash in the scoped runtime log sample.
No claim of a leak stress test, full lifecycle instrumentation or sustained GPU benchmark is made.
