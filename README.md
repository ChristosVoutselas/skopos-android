# SKOPOS for Android

Native Kotlin / Jetpack Compose counterpart of the existing SKOPOS SwiftUI app. Requires Android 8.0 (API 26) or newer. The original iOS project was inspected read-only at `/Volumes/Transcend/ios/SKOPOS`.

## Open and run

Open this folder in Android Studio, allow Gradle sync, choose an emulator or device, and run **app**. A ready-to-install debug APK is in `dist/SKOPOS-debug.apk`.

- SDK: compile/target 36; install Android SDK Platform 36 if needed.
- Build runtime: Java 21. `gradle/gradle-daemon-jvm.properties` pins it independently of Android Studio's launcher. Java 21 was already available in this Mac's Gradle JDK cache. On another machine, install/select Java 21 in Android Studio's Gradle JDK settings.
- On macOS when opened from `/Volumes/`, generated build outputs go to `~/.gradle/skopos-builds/` to avoid exFAT AppleDouble resource errors. All project sources remain on Transcend; Android Studio locates the generated APK automatically. A convenient verified copy is in `dist/`.
- Gradle wrapper: 8.13; Android Gradle plugin: 8.13.2; Kotlin/Compose compiler: 2.0.0.
- `local.properties` points to this Mac's Android SDK. Replace it on another machine; it is ignored by Git.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Debug signing is configured by Android tooling. Distribution through Google Play requires your release signing key, store listing and release testing; no signing credentials are included.

## Backend

Default: **https://api.voutselasgroup.com/skopos/**. Change it from **IOC Hunt → gear** or **Product & Legal → Connection settings**. Saving returns to Overview and refreshes.

| Data | GET endpoint and parameters |
|---|---|
| Overview | `api/summary?hours=24` |
| Recent indicators / globe | `api/iocs?hours=24&limit=2000` |
| Vulnerabilities | `api/vulnerabilities?limit=1000` |
| Claims | `api/ransomware?limit=200` |
| News | `api/news?limit=100` |
| IOC Hunt | `api/iocs?q=…&hours=1,6,24&ioc_type=…&limit=1000` (one selected hours value) |
| Quick investigation | `api/iocs?q=…&hours=720&ioc_type=&limit=1000` |
| APT campaigns | `api/apt/campaigns?limit=200&offset=…` (all pages, duplicate-page detection) |

HTTPS is mandatory for backend requests. Redirects are rejected, cookies and HTTP caches are not enabled, and paths preserve `/skopos/`. Current endpoints accept unauthenticated reads. Provider API keys remain on the server. If the backend introduces login, a first-party authentication flow will need to be added.

## Screens and behavior

Eight horizontally scrollable bottom tabs match the current iOS source: Overview, News, Vulnerabilities, Ransomware, IOC Hunt, APT Campaigns, Watchlist Setup, Product & Legal.

- Original owl logo/icon and bundled Natural Earth geography; near-black panels, coral accents and radar animation.
- Overview totals use server counts, never the length of a bounded feed. The unavailable new/updated CVE total stays “—”.
- News includes local text/source filtering, four sorts, 10/20/50 per page, detail views and publisher links.
- CVEs include search, KEV/high-EPSS filters, date/CVSS/EPSS/KEV sorting, remediation and NVD links. The backend caps this feed at 1,000; this is not a complete database browser.
- Ransomware search and details preserve the distinction between reported claims and verified breaches.
- IOC Hunt accepts free text, time/type filters, category toggles and a 24-bin source-first-seen chart. Quick investigation validates individual indicators. Indicators remain defanged text; they are never visited.
- APT campaigns include search over supplied metadata, MITRE update ordering and details/source links. Missing severity displays the same Medium default as iOS, with disclosure.
- Watchlist terms support add/edit/delete and matches against loaded IOC, CVE, claim and APT records. Detail screens support saved bookmarks. Everything is local; no background alerts or sync.
- Globe supports drag/pinch, zoom/reset and accessible rotate buttons. Its animated collection arcs use real IOC locations and illustrative hubs, never claims of measured attack traffic.

Foreground polling refreshes every 60 seconds, with per-feed request gates and manual pull-to-refresh. Returning to the foreground refreshes when due. APT and Hunt update while their relevant sections are selected. Connectivity recovery makes the next tick eligible immediately. Already-started requests can finish after backgrounding; no background polling is scheduled.

Full snapshots, APT campaigns and Hunt results use separate atomic disk caches keyed by backend and, for Hunt, normalized query parameters. Cached results retain the successful retrieval timestamp. Quick investigations are not persisted. Watchlists use app-private preferences with write failure checks; backup and device transfer are excluded. No fictional/demo fallback is shipped.

## Implementation and validation

`MainActivity.kt` contains native screen components; `Globe.kt` implements local Canvas rendering; `Store.kt` owns refresh/caching/preferences; `Data.kt` owns records, URL validation and backend mapping. Coroutine requests run off the UI thread. No third-party mapping or analytics service is contacted.

See `docs/PORTING-NOTES.md` for source inventory and practical platform differences, and `docs/VALIDATION.md` for checks actually run. Emulator captures are under `docs/screenshots/`.
