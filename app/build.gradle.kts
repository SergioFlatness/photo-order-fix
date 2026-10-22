plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.sergioflatness.photoorderfix"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.sergioflatness.photoorderfix"
        // Android 11: first version with one-tap bulk delete consent (MediaStore.createDeleteRequest).
        minSdk = 30
        targetSdk = 36
        versionCode = 26
        versionName = "0.12.1"
    }

    signingConfigs {
        // A fixed debug key, committed on purpose: every test build gets the same signature,
        // so a newer build installs over the old one instead of failing. Not for Play Store releases.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The upload key for Play Store bundles. It is never committed: the release workflow
        // writes it from GitHub secrets, so local release builds stay unsigned.
        System.getenv("RELEASE_KEYSTORE")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = "upload"
                keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks and obfuscates the code; Play warns when release code is not optimized.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
    }
}

dependencies {
    implementation("io.github.sergioflatness.photoorderfix:core:1.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
