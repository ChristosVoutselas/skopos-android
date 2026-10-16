# Built-in Kotlin migration

The Android Gradle plugin was upgraded to 9.4.1 with Gradle 9.6.0. AGP 9 provides Kotlin support directly, so applying org.jetbrains.kotlin.android again registered the kotlin extension twice.

Removed the redundant plugin from the project and app scripts. Removed android.kotlinOptions; built-in Kotlin inherits the Java 17 bytecode target from compileOptions. Kept the Compose compiler plugin at 2.2.10, matching the Kotlin plugin dependency supplied by AGP 9.4.1. Release optimization, version 1.2 (3), and app features are unchanged.

Reference: https://developer.android.com/build/migrate-to-built-in-kotlin

Validation: Gradle 9.6.0 debug APK, optimized release AAB, all 13 debug unit tests and release lint passed. Build log: docs/agp9-build.log.
