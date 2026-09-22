plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

// macOS writes AppleDouble metadata beside generated files on external FAT/exFAT
// volumes. AAPT treats those files as resources, so keep disposable outputs on
// the local filesystem while all source files stay in the requested project.
if (System.getProperty("os.name").contains("Mac", ignoreCase = true) && rootDir.absolutePath.startsWith("/Volumes/")) {
    val cacheRoot = java.io.File(System.getProperty("user.home"), ".gradle/skopos-builds/" + rootDir.absolutePath.hashCode().toUInt().toString(16))
    layout.buildDirectory.set(java.io.File(cacheRoot, "root"))
    subprojects { layout.buildDirectory.set(java.io.File(cacheRoot, name)) }
}
