# Release optimization — 12 October 2026

Enabled release code shrinking, optimization and obfuscation with the standard optimized Android rules, plus resource shrinking. No blanket keep rules were added. App records use explicit org.json mapping rather than reflection-based serialization.

Release APK, AAB, all 13 release unit tests and release lint passed. Optimized release DEX totals 1.82 MiB. The AAB contains proguard.map and r8.json metadata. Google Play will calculate its own scores after upload; existing releases are unchanged.

The packaged AAB is unsigned. Generate a signed release AAB in Android Studio using the existing upload keystore before uploading. Runtime testing of this optimized release was not performed: no emulator was running. The unit tests exercise source logic, not the optimized APK.
