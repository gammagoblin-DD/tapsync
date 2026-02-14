plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile") // add
}

android {
    namespace = "com.example.tapsyncwatch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.tapsyncwatch"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "2.4.1-alpha"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
}

// Baseline Profile Gradle plugin configuration.
// - mergeIntoMain: keep a single profile (no flavors here, simpler workflow)
// - saveInSrc: write into src/ so you can commit it
baselineProfile {
    mergeIntoMain = true
    saveInSrc = true
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Enables local/side-loaded Baseline Profile installation on devices where
    // Cloud Profiles aren't available (and is recommended generally).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Pull generated Baseline Profiles from the generator module.
    baselineProfile(project(":baselineprofile"))


    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))


}
