plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.retro.vfdlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.retro.vfdlauncher"
        minSdk = 29
        targetSdk = 35
        // GitHub Actions numbers every build; the in-app updater compares these numbers.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.1.$build"
    }

    // Release key: from GitHub secrets in CI (SIGNING_*), or a local keystore file. Without one,
    // release builds fall back to the debug key (fine for testing, but they can't update a
    // release-signed install).
    val keystoreFile = System.getenv("SIGNING_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")?.trim()
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")?.trim()?.ifEmpty { null } ?: "vfd"
                keyPassword = (System.getenv("SIGNING_KEY_PASSWORD") ?: System.getenv("SIGNING_STORE_PASSWORD"))?.trim()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
