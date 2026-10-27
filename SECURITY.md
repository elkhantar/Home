# Security model

Home is a personal Android TV launcher, tested on one Android 9 TV. It does not repair the
TV's operating-system security patch level. Keep development ADB limited to your trusted
network and devices. No third-party security certification is claimed.

## Boundaries

- No account tokens, analytics, accessibility key service, notification listener or
  broadly scoped storage permission in the launcher. No declared Android services.
- HTTPS uses ordinary certificate verification, fixed allowed hosts and no redirects;
  response sizes, image dimensions, workers and lifecycle callbacks are bounded.
- Curated catalogues are signed offline with the app signing key, with domain separation.
  The installed certificate verifies signatures. Paths, filenames, hashes and bounds are
  checked; the verified image bytes are the bytes decoded. Invalid data retains the last
  picture. This protects integrity, not confidentiality on readable external storage.
- Watch Next is platform data rather than a streaming-account integration. Playback links
  are constrained to the publisher's exported activity; unsafe schemes, selectors and
  cross-package components are rejected, and incoming flags/clip data are stripped.
- Module cards require owner consent scoped to the module's signing key.
- App removal uses Android's uninstall dialog. Force stop is left to Android app settings.
- The preview installer checks identity, permissions, signature and approved file hash
  before installation, then compares the installed APK's bytes. It preserves default Home.

Independent audit history: [review and remediation](docs/review/INDEPENDENT-FORK-REVIEW.md).

## Known limits

72 tests and Android lint (0 errors, 123 warnings) passed for the installed 1.11 build.
Lint includes inherited resource/localization and dependency notices. Dependency versions
remain the pinned upstream versions; no complete transitive vulnerability audit was done.
On-device samples are not extended standby, reboot, memory or all-device certification.
The prior independent review covered the earlier fork; later features have targeted tests
and source/device checks, not a claim of a new independent audit.

Do not commit signing keys, API credentials, device dumps or wallpaper collections. The
repository ignore rules cover the working locations; inspect staged files before publishing.
A locally generated signing key requires intentionally updating the verifier's certificate
pin. Never bypass a signature failure just to complete an installation.

For a suspected vulnerability, contact the repository owner privately through GitHub where
possible. Avoid including credentials or private device/account data in a public issue.
