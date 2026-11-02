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
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        create("release") {
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "fitshare"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "fitshare123"
            storeFile = file("release.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "fitshare123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
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
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
