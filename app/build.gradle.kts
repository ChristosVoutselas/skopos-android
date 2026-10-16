plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "com.voutselas.skopos"
    compileSdk = 36
    defaultConfig { applicationId = "com.voutselas.skopos"; minSdk = 26; targetSdk = 36; versionCode = 4; versionName = "1.3" }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}
dependencies {
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material:1.6.8")
    implementation("androidx.compose.material:material-icons-core:1.6.8")
    implementation("androidx.browser:browser:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
}
