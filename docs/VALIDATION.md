# Globe appearance restored — 12 October 2026

Restored the previous shaded Natural Earth sphere and outlines. New palette, balanced sampling and selectable markers remain. Debug build, 13 unit tests and lint pass.

# Validation — 12 October 2026

- Android debug build, all 13 unit tests and lint passed.
- Lint: 0 errors; 19 advisory warnings (dependency updates and optional idioms).
- Added tests verify minority URL inclusion after more than 95 IP records, category balance, the 95-node limit, unique records, invalid geography omission and zero capacity.
- Final APK installed and launched on Pixel_10 emulator. Live backend data loaded; new palette, owl branding, NASA Earth texture, red/cyan arcs, marker clusters and accessible indicator selection were checked visually. Selected indicator details displayed country, family and source. No app crash was observed in AndroidRuntime logs.
- Earth rendering was corrected so rotation does not cancel unfinished texture frames.
- APK: `dist/SKOPOS-debug.apk`. Build log and unit-test XML accompany this report.
- Tablet side-by-side layout is implemented and compiled; a tablet runtime was not inspected. No Play Store upload or production signing was performed.

## Previous validation

# Validation — 21 September 2026

## Completed

The final successful build ran directly from `/Volumes/Transcend/android/SKOPOS`. All 51 tasks executed successfully. Generated outputs are stored on the Mac at `~/.gradle/skopos-builds/d498b051/` to avoid macOS AppleDouble files on the external filesystem; the project and packaged APK remain on Transcend.

- Gradle `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`: **BUILD SUCCESSFUL**.
- Unit tests: **11 passed, 0 failures, 0 skipped**. Coverage includes backend URL restrictions/path preservation, individual indicator validation, source-link restrictions, defanging, ISO timestamps, missing numerical values, confidence/severity distinction, invalid coordinates, backend/query cache-key isolation and APT fallback severity.
- Android lint: **0 errors**. Remaining advisory warnings concern newer dependency versions and optional Kotlin extension idioms. These are not suppressed.
- Build launched with Android Studio's Java 25 environment and automatically selected the pinned Java 21 daemon successfully.
- Read-only live requests to summary, IOC, vulnerability, ransomware, news and APT campaign endpoints succeeded. Main feeds were valid JSON with the expected fields and bounded response sizes.
- Debug APK installed and launched on the existing Pixel 9 ARM64 emulator.
- Visually inspected Overview, News, IOC Hunt, APT Campaigns and the globe using live backend data. Horizontal tab navigation worked. Live counts and the unavailable new/updated-CVE total rendered correctly.
- Offline check: enabling airplane mode retained the loaded intelligence and original timestamp with an explicit CACHED badge. Network access was restored afterwards.
- App runtime log inspection found no SKOPOS crash.

## Artifacts

- `dist/SKOPOS-debug.apk`: installable debug-signed app.
- `docs/build-verification.log`: successful Gradle output.
- `docs/unit-test-results.xml`: machine-readable unit-test result.
- `docs/screenshots/`: emulator captures of verified layouts (live intelligence changes over time).

## Not claimed

No physical-device matrix, Android 8 runtime test, Play Store upload, production signing, exhaustive UI regression suite or backend authentication flow was performed. Unit tests do not replace live-network or device testing. The native Android visual treatment and tablet detail navigation differ modestly from SwiftUI; see PORTING-NOTES.md.
