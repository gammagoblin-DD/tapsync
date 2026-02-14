plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.example.tapsyncwatch.baselineprofile"
    compileSdk = 34

    defaultConfig {
        minSdk = 30
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // wichtig: zeigt auf dein App-Modul
    targetProjectPath = ":app"
}

dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0-beta01")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}
