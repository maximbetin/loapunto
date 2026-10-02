plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mbk.loapunto"
    compileSdk = 37

    val automaticBuildProperty = providers.gradleProperty("loApuntoBuildNumber").orNull
    val automaticBuild = automaticBuildProperty?.toIntOrNull()
    val releaseKeystore = providers.environmentVariable("LOAPUNTO_DEBUG_KEYSTORE").orNull
    require(automaticBuildProperty == null || automaticBuild != null) {
        "loApuntoBuildNumber must be an integer."
    }
    require(automaticBuild == null || !releaseKeystore.isNullOrBlank()) {
        "LOAPUNTO_DEBUG_KEYSTORE is required for versioned release builds."
    }

    defaultConfig {
        applicationId = "com.mbk.loapunto"
        minSdk = 34
        targetSdk = 37
        versionCode = automaticBuild ?: 1
        versionName = automaticBuild?.let { "0.1.$it" } ?: "0.1"
    }

    // CI supplies the private update-compatible key; local unversioned builds use the normal debug key.
    signingConfigs.getByName("debug") {
        releaseKeystore?.takeUnless(String::isBlank)?.let { storeFile = file(it) }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Preserve the installed app's certificate while shipping a non-debuggable APK.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
