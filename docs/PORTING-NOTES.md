# iOS source inventory and Android mapping

Inspected the existing project at `/Volumes/Transcend/ios/SKOPOS` without writing to it. Current Swift source takes precedence over older README descriptions (the README still describes five tabs and an older palette).

| iOS reference | Findings and Android counterpart |
|---|---|
| `Views/RootView.swift` | Eight horizontally scrolling sections. Preserved in Android bottom navigation. |
| `Views/Theme.swift`, `DashboardView.swift` | Background ~#0C0A0D, surface #181418, accent #FF6673, muted #A8A1AB; rounded panels; owl header; radar; six independent totals. |
| `Views/WorkspaceView.swift` | News source chips, search, ordering, paging, details and Safari source sheets. Android uses native Compose controls and browser Custom Tabs. |
| `Views/VulnerabilitiesView.swift`, `Models/Intelligence.swift` | Loaded-set CVE search; KEV/EPSS filters, optional numerical scores, NVD dates, remediation, source links and bookmarks. |
| `Views/RansomwareView.swift` | Search by victim/group/geography/sector/source; claim details and external source links. Onion URLs remain unlaunchable. |
| `Views/InvestigationView.swift` | IOC Hunt category toggles, 1/6/24-hour requests, IOC type, source-first-seen chart and detailed enrichment. No export controls in current shipping screen. |
| `Views/APTView.swift`, `Models/APTModels.swift`, `Repositories/APTRepository.swift` | Campaign list sorted by MITRE modification, search, missing-severity disclosure, detail/source links. API pages use total/items with limit/offset and duplicate detection. Current view displays all matching rows despite older documentation mentioning 15-row paging/export. Android follows current visible behavior. |
| `Views/WatchlistSetupView.swift`, `Repositories/WatchlistRepository.swift` | Editable local keywords, matching loaded records, saved bookmarks and Product & Legal content. |
| `Views/GlobalGlobeView.swift`, `Docs/Geography.md` | Orthographic globe, local Natural Earth country outlines, up to 95 geolocated IOCs, illustrative collection hubs, gestures and visible-location metrics. |
| `Services/BackendService.swift` | Current general vulnerability endpoint differs from README priority endpoint; Android uses `api/vulnerabilities?limit=1000`. HTTPS validation, redirect rejection, anonymous GETs and missing-total semantics preserved. |
| `Services/IndicatorValidator.swift` | Normalized IPv4/IPv6/domain/HTTP URL/hash input; domain validation is syntactic and never resolves/visits the indicator. |
| `App/AppStore.swift`, repositories | Foreground refresh gates, backend-isolated snapshots, per-query Hunt caches, local bookmarks and settings. Ported to a retained Android ViewModel with coroutine requests. |
| `Resources/Info.plist` | Same default production backend: `https://api.voutselasgroup.com/skopos/`. |
| `Resources/Assets.xcassets` | Original `SKOPOSLogo` JPEG, app icon PNG and `WorldBoundaries` JSON copied into Android resources/assets. |

## Practical platform differences

- Compose/Material controls, Android system back and Custom Tabs replace SwiftUI controls and Safari sheets. The visual layout, branding and data semantics are preserved; it is not a pixel-identical iOS rendering.
- APT details open as a separate native screen at all widths rather than an iPad-style adjacent panel. Content centers within an 850dp width on larger displays.
- Globe geography is rendered as vector outlines rather than the iOS land-sampling treatment. The same bundled public-domain dataset is used, without remote tiles or SDKs.
- Active section filters and scroll state survive switching tabs. Record details are kept in memory; Android can return to the list after process recreation. Local watchlists, caches and backend settings persist across launches.
- Local files are protected by Android's app sandbox and device storage encryption; there is no provider-secret storage because none is required by the inspected backend.
- No push notifications, background monitoring, cloud watchlist sync or complete-CVE pagination are claimed; the existing iOS backend does not provide those flows.

## Attribution

Geography: Natural Earth, 1:50m Admin 0 Countries, public domain. The source project's simplified outlines are included unchanged in `app/src/main/assets/world.json`.

https://www.naturalearthdata.com/about/terms-of-use/

Compose compiler configuration follows the official Kotlin 2.0 plugin setup:
https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
