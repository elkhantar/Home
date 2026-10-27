# Credits and third-party notices

## Launcher source and design

Home is a modified fork of [CleanXiaomiTV](https://github.com/majnioui/CleanXiaomiTV)
by [majnioui](https://github.com/majnioui), using commit
`5d371afffad6439daf9eae0a7f569ceb88e637cf` as its base. CleanXiaomiTV builds on
[CleanYourTV](https://github.com/Gathin23/cleanyourtv) by
[Gathin / Gathin23](https://github.com/Gathin23), also at [cleanyour.tv](https://cleanyour.tv/).
Their launcher architecture, native glass design, module contract and tooling made this fork
possible. The original **Copyright (c) 2026 Gathin** and MIT terms remain in [LICENSE](LICENSE).

This fork's Home artwork (SVG masters in artwork/ and Android exports), remote-inspired
window, Spanish menu completion and subsequent features were developed for Pablo Rivas's
family TV with assistance from OpenAI Codex. Modifications are distributed under MIT;
this does not imply upstream, OpenAI or any service provider endorses the project.
See [CHANGELOG.md](CHANGELOG.md) for the changes.

## Fonts and icons

- **Outfit**, Copyright 2021 The Outfit Project Authors. [Project](https://github.com/Outfitio/Outfit-Fonts),
  SIL Open Font License 1.1. Full notices: [launcher](launcher/FONT-LICENSE-OFL.txt) and
  [modules](modules/FONT-LICENSE-OFL.txt). Font files retain OFL, not MIT.
- Upstream credits identify **Material**, **Feather** and **Lucide** as icon sources.
  Retained/modified vector assets carry those notices conservatively; no per-icon provenance
  claim is made where upstream did not provide a mapping.
- [Material icons](https://github.com/google/material-design-icons), Google, Apache 2.0:
  [license](licenses/Apache-2.0.txt).
- [Feather](https://github.com/feathericons/feather), Cole Bemis, MIT:
  [copyright and license](licenses/Feather-MIT.txt).
- [Lucide](https://github.com/lucide-icons/lucide), Lucide contributors, ISC, with inherited
  Feather notices: [full license](licenses/Lucide-ISC.txt).

## Libraries and build tools

AndroidX RecyclerView 1.3.2 and DynamicAnimation 1.0.0 are Android Open Source Project
libraries, [Apache 2.0](licenses/Apache-2.0.txt). The Android SDK and Gradle are external
build prerequisites, not re-licensed by Home. Gradle wrapper files are inherited from upstream.
JUnit 4.13.2 is a test-only dependency under
[Eclipse Public License 1.0](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt).
The optional PC photo tools use Pillow, under its
[HPND license](https://github.com/python-pillow/Pillow/blob/main/LICENSE).
Dependency versions and their original notices remain authoritative.

## Weather

Weather data by **[Open-Meteo.com](https://open-meteo.com/)**, licensed
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Home selects the current temperature, day/night and WMO weather code, rounds the displayed
temperature and maps the code to a weather symbol; it does not produce its own forecast.
The configured city's rounded coordinates are sent to the API. See the provider's
[data sources and attribution terms](https://open-meteo.com/en/licence).
This source repository's attribution does not replace the provider's requirement for a
linked credit next to displayed weather data in a distributed application.

## Wallpapers and photographs

Photographs are **not covered by this repository's MIT license**. No personal wallpaper
collection or photographer descriptions are bundled with Home's source or APK.

- The owner's curated collection was downloaded separately from **Unsplash**. Individual
  photographers retain copyright. [Unsplash license](https://unsplash.com/license) and
  [API terms](https://unsplash.com/api-terms) apply independently of this code license.
- [Photographer credits](docs/WALLPAPER_CREDITS.md) record verified names and links for the
  current personal collection; unresolved entries are marked pending, never inferred from
  filenames. Regenerate with `python tools/export-wallpaper-credits.py` as metadata arrives.
- On the TV, photographer names are shown first, followed by available publication date,
  location and a short photographer caption. Publication date is not capture date. Alt text
  is deliberately excluded. Cropping, resizing and WebP encoding are local modifications.
- Bing fallback photographs are credited using the archive's supplied copyright metadata.
  Bing and each rights holder retain their rights. This code's license grants no rights to
  redistribute Bing photos or to use an undocumented endpoint as an unrestricted photo API.
- Inherited screenshots, banners and theme sample images are upstream demonstration assets;
  their original provenance is CleanXiaomiTV. Where an individual photo credit was not supplied,
  do not assume that MIT conveys that photo's copyright or reuse it as a wallpaper collection.

The metadata fetcher is an offline enrichment tool for an existing user-owned selection,
not an Unsplash discovery/download client. A future public API integration needs a separate
review of attribution links, API rate limits, hotlinking and download-tracking obligations.

Netflix, Prime Video, Disney+, Crunchyroll, Google, Xiaomi, Unsplash and other product names,
app icons and marks belong to their respective owners. Installed apps supply their own
icons and Watch Next records; no partnership or endorsement is implied.
