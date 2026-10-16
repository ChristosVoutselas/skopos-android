# Google Play fixes — 16 October 2026

Version 1.2, version code 3 replaces published version 1.1 (2).

- Removed the older AndroidX Activity edge-to-edge helper. WindowCompat disables decor fitting, Compose handles safe insets and window inset consumption, and light system-bar icons suit the dark theme. Transparent theme backgrounds support older Android releases. API 30+ uses the ALWAYS display cutout mode, never SHORT_EDGES.
- Enabled android.r8.optimizedResourceShrinking=true for AGP 8.13.2, alongside existing release minification and resource shrinking.
- Optimized release DEX inspection found no setStatusBarColor or setNavigationBarColor invocations; the old EdgeToEdge implementation is no longer retained.
- AAB R8 metadata confirms resourceOptimization.isOptimizedShrinkingEnabled=true.
- Debug APK, optimized release APK/AAB, release lint and all 13 release unit tests passed. Lint reports 0 errors and 15 advisory warnings.
- Optimized release was temporarily signed with the standard development key only for emulator testing. Live overview data loaded with both gesture and three-button navigation and no overlapping controls. The emulator runs API 37; Android 15 and older devices were not separately exercised.

Distribution: dist/SKOPOS-release-unsigned.aab is unsigned. Generate a signed release AAB in Android Studio using the existing upload keystore before uploading to Play. dist/SKOPOS-debug.apk is for local testing. Keep the mapping file for crash decoding. Google Play will reassess recommendations after the signed new bundle is uploaded; earlier published bundles retain their previous diagnostics.
