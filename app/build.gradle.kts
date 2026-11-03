plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fitshare"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitshare"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.9.1"
    }

    // Release key lives only in CI secrets (and ~/.config/fitshare on the maintainer's machine).
    // Without it, local release builds fall back to the debug key so they still install.
    val storePass = System.getenv("SIGNING_STORE_PASSWORD")
    signingConfigs {
        if (storePass != null) create("release") {
            storeFile = file(System.getenv("SIGNING_STORE_FILE") ?: "release.jks")
            storePassword = storePass
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // the OCR library is native; phones are ARM, x86 only matters for the emulator (debug builds keep it)
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    // compress the OCR .so files inside the APK: ~10 MB smaller download, unpacked once at install
    packaging { jniLibs { useLegacyPackaging = true } }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.gson)
    // OCR for share pictures, model inside the APK: works on Huawei and degoogled phones without Play Services
    implementation(libs.mlkit.text)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
