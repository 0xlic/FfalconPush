plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.lichen.raybirdpush"
    compileSdk = 36
    compileSdkMinor = 1
    buildToolsVersion = "36.1.0"
    defaultConfig {
        applicationId = "io.github.lichen.raybirdpush"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.2.1"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        // Local test release: same local debug certificate, no production signing key in source.
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true; unitTests.isIncludeAndroidResources = true }
}
kotlin { jvmToolchain(17) }
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Keep test-created files inside this project, not the host's global temp directory.
    systemProperty("java.io.tmpdir", temporaryDir.absolutePath)
    systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots/$name").get().asFile.absolutePath)
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
